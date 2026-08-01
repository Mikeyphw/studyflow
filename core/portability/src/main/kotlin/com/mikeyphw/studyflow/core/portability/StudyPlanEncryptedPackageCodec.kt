package com.mikeyphw.studyflow.core.portability

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class StudyPlanPackageAuthenticationException(message: String) : IllegalArgumentException(message)
class StudyPlanPackagePasswordRequiredException(message: String) : IllegalArgumentException(message)

class StudyPlanEncryptedPackageCodec(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun isEncrypted(bytes: ByteArray): Boolean = isEncryptedPrefix(bytes)

    fun isEncryptedPrefix(prefix: ByteArray): Boolean =
        prefix.size >= Magic.size && Magic.indices.all { index -> prefix[index] == Magic[index] }

    fun encrypt(plainBytes: ByteArray, password: CharArray): ByteArray =
        ByteArrayOutputStream().also { output ->
            encrypt(output, password) { encryptedOutput -> encryptedOutput.write(plainBytes) }
        }.toByteArray()

    fun encrypt(
        output: OutputStream,
        password: CharArray,
        writePlainPackage: (OutputStream) -> Unit,
    ) {
        require(password.size >= MinimumPasswordLength) {
            "Encrypted Study Plan exports require at least $MinimumPasswordLength characters."
        }
        val salt = ByteArray(SaltSize).also(secureRandom::nextBytes)
        val nonce = ByteArray(NonceSize).also(secureRandom::nextBytes)
        val header = buildHeader(salt, nonce)
        val key = deriveKey(password, salt, Iterations, KeySize)
        try {
            val cipher = Cipher.getInstance(CipherTransformation)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TagBits, nonce))
            cipher.updateAAD(header)
            output.write(header)
            CipherOutputStream(output, cipher).use { encryptedOutput ->
                writePlainPackage(encryptedOutput)
            }
        } finally {
            key.fill(0)
            salt.fill(0)
            nonce.fill(0)
            header.fill(0)
        }
    }

    fun decrypt(encryptedBytes: ByteArray, password: CharArray): ByteArray =
        ByteArrayOutputStream().also { output ->
            decrypt(
                input = encryptedBytes.inputStream(),
                output = output,
                password = password,
                maximumEncryptedBytes = encryptedBytes.size.toLong(),
            )
        }.toByteArray()

    /**
     * Streams authenticated plaintext into [output]. A caller must discard the output when this method throws,
     * because GCM authentication is only final after the complete ciphertext and tag have been consumed.
     */
    fun decrypt(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        maximumEncryptedBytes: Long,
    ) {
        require(maximumEncryptedBytes >= GcmTagBytes) { "Encrypted package size limit is invalid." }
        val envelope = parseEnvelopeHeader(input)
        val key = deriveKey(password, envelope.salt, envelope.iterations, KeySize)
        val encryptedBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var finalPlaintext: ByteArray? = null
        try {
            val cipher = Cipher.getInstance(CipherTransformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TagBits, envelope.nonce),
            )
            cipher.updateAAD(envelope.header)
            var encryptedBytesRead = 0L
            while (true) {
                val count = input.read(encryptedBuffer)
                if (count < 0) break
                encryptedBytesRead += count
                require(encryptedBytesRead <= maximumEncryptedBytes) {
                    "Encrypted Study Plan package exceeds the supported size."
                }
                cipher.update(encryptedBuffer, 0, count)?.let { plaintext ->
                    output.write(plaintext)
                    plaintext.fill(0)
                }
            }
            require(encryptedBytesRead >= GcmTagBytes) { "Encrypted Study Plan package is incomplete." }
            val authenticatedTail = cipher.doFinal()
            finalPlaintext = authenticatedTail
            output.write(authenticatedTail)
        } catch (_: GeneralSecurityException) {
            throw StudyPlanPackageAuthenticationException(
                "The password is incorrect or the encrypted Study Plan package is corrupted.",
            )
        } finally {
            finalPlaintext?.fill(0)
            encryptedBuffer.fill(0)
            key.fill(0)
            envelope.salt.fill(0)
            envelope.nonce.fill(0)
            envelope.header.fill(0)
        }
    }

    private fun buildHeader(salt: ByteArray, nonce: ByteArray): ByteArray =
        ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.write(Magic)
                data.writeByte(EnvelopeVersion)
                data.writeInt(Iterations)
                data.writeByte(salt.size)
                data.write(salt)
                data.writeByte(nonce.size)
                data.write(nonce)
            }
        }.toByteArray()

    private fun parseEnvelopeHeader(input: InputStream): EnvelopeHeader {
        try {
            val data = DataInputStream(input)
            val magic = ByteArray(Magic.size).also(data::readFully)
            require(magic.contentEquals(Magic)) { "This is not an encrypted Study Plan package." }
            val version = data.readUnsignedByte()
            require(version == EnvelopeVersion) { "Unsupported encrypted package version $version." }
            val iterations = data.readInt()
            require(iterations in MinimumIterations..MaximumIterations) {
                "Encrypted package key-derivation settings are invalid."
            }
            val salt = ByteArray(data.readUnsignedByte().also { require(it in 16..64) }).also(data::readFully)
            val nonce = ByteArray(data.readUnsignedByte().also { require(it in 12..16) }).also(data::readFully)
            val header = buildHeader(iterations, salt, nonce)
            magic.fill(0)
            return EnvelopeHeader(header, salt, nonce, iterations)
        } catch (error: StudyPlanPackageAuthenticationException) {
            throw error
        } catch (error: Exception) {
            throw StudyPlanPackageAuthenticationException(
                error.message ?: "Encrypted Study Plan package header is invalid.",
            )
        }
    }

    private fun buildHeader(iterations: Int, salt: ByteArray, nonce: ByteArray): ByteArray =
        ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.write(Magic)
                data.writeByte(EnvelopeVersion)
                data.writeInt(iterations)
                data.writeByte(salt.size)
                data.write(salt)
                data.writeByte(nonce.size)
                data.write(nonce)
            }
        }.toByteArray()

    private fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        outputSize: Int,
    ): ByteArray {
        val passwordBytes = password.toUtf8Bytes()
        return try {
            val blocks = (outputSize + DigestSize - 1) / DigestSize
            val output = ByteArray(blocks * DigestSize)
            for (block in 1..blocks) {
                val blockBytes = pbkdf2Block(passwordBytes, salt, iterations, block)
                blockBytes.copyInto(output, (block - 1) * DigestSize)
                blockBytes.fill(0)
            }
            output.copyOf(outputSize).also { output.fill(0) }
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun CharArray.toUtf8Bytes(): ByteArray {
        val encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(this))
        return ByteArray(encoded.remaining()).also { bytes ->
            encoded.get(bytes)
            if (encoded.hasArray()) encoded.array().fill(0)
        }
    }

    private fun pbkdf2Block(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        blockIndex: Int,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val initial = salt + byteArrayOf(
            (blockIndex ushr 24).toByte(),
            (blockIndex ushr 16).toByte(),
            (blockIndex ushr 8).toByte(),
            blockIndex.toByte(),
        )
        var current = mac.doFinal(initial)
        initial.fill(0)
        val result = current.copyOf()
        repeat(iterations - 1) {
            val next = mac.doFinal(current)
            current.fill(0)
            current = next
            current.indices.forEach { index ->
                result[index] = (result[index].toInt() xor current[index].toInt()).toByte()
            }
        }
        current.fill(0)
        return result
    }

    private data class EnvelopeHeader(
        val header: ByteArray,
        val salt: ByteArray,
        val nonce: ByteArray,
        val iterations: Int,
    )

    companion object {
        const val MinimumPasswordLength = 8
        val MagicSize: Int get() = Magic.size
        private const val EnvelopeVersion = 1
        private const val Iterations = 210_000
        private const val MinimumIterations = 100_000
        private const val MaximumIterations = 1_000_000
        private const val SaltSize = 16
        private const val NonceSize = 12
        private const val KeySize = 32
        private const val DigestSize = 32
        private const val TagBits = 128
        private const val GcmTagBytes = TagBits / 8
        private const val CipherTransformation = "AES/GCM/NoPadding"
        private val Magic = "STUDYFLOWENC".encodeToByteArray()
    }
}
