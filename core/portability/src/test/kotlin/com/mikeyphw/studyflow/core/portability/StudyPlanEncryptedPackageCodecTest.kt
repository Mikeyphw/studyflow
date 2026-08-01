package com.mikeyphw.studyflow.core.portability

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StudyPlanEncryptedPackageCodecTest {
    private val codec = StudyPlanEncryptedPackageCodec()
    private val plain = "checksum-verified-study-plan".encodeToByteArray()

    @Test
    fun envelopeMatchesThePublishedPbkdf2AndAesGcmVector() {
        var nextByte = 0
        val deterministicRandom = object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) {
                bytes.indices.forEach { index ->
                    bytes[index] = nextByte.toByte()
                    nextByte += 1
                }
            }
        }
        val deterministicCodec = StudyPlanEncryptedPackageCodec(deterministicRandom)
        val encrypted = deterministicCodec.encrypt(plain, "correct horse".toCharArray())

        assertEquals(
            "5354554459464c4f57454e43010003345010000102030405060708090a0b0c0d0e0f" +
                "0c101112131415161718191a1b0f474d32305e9782e7cd359c87f9753c913ea9ef6" +
                "aa36829646738889b5ee4c6cdd6c63fd531830b9ac8a3d5",
            encrypted.joinToString("") { byte -> "%02x".format(byte) },
        )
    }

    @Test
    fun encryptedBytesRequireTheCorrectPassword() {
        val encrypted = codec.encrypt(plain, "correct horse".toCharArray())
        assertTrue(codec.isEncrypted(encrypted))
        assertArrayEquals(plain, codec.decrypt(encrypted, "correct horse".toCharArray()))
        assertFalse(encrypted.copyOfRange(0, 2).contentEquals(byteArrayOf('P'.code.toByte(), 'K'.code.toByte())))
    }

    @Test
    fun streamingEncryptionAuthenticatesTheCompleteWrittenPackage() {
        val output = ByteArrayOutputStream()
        codec.encrypt(output, "correct horse".toCharArray()) { encryptedOutput ->
            encryptedOutput.write(plain, 0, 8)
            encryptedOutput.write(plain, 8, plain.size - 8)
        }

        assertArrayEquals(plain, codec.decrypt(output.toByteArray(), "correct horse".toCharArray()))
    }

    @Test
    fun streamingDecryptionAuthenticatesBeforeTheTemporaryOutputIsConsumed() {
        val encrypted = codec.encrypt(plain, "correct horse".toCharArray())
        val output = ByteArrayOutputStream()

        codec.decrypt(
            input = encrypted.inputStream(),
            output = output,
            password = "correct horse".toCharArray(),
            maximumEncryptedBytes = encrypted.size.toLong(),
        )

        assertArrayEquals(plain, output.toByteArray())
    }

    @Test
    fun streamingDecryptionEnforcesTheEncryptedSizeLimit() {
        val encrypted = codec.encrypt(plain, "correct horse".toCharArray())
        try {
            codec.decrypt(
                input = encrypted.inputStream(),
                output = ByteArrayOutputStream(),
                password = "correct horse".toCharArray(),
                maximumEncryptedBytes = 16,
            )
            fail("Oversized encrypted input should fail")
        } catch (_: IllegalArgumentException) {
            // Expected bounded-read failure.
        }
    }

    @Test
    fun partialMagicPrefixIsNotMisclassifiedAsEncrypted() {
        val encrypted = codec.encrypt(plain, "correct horse".toCharArray())
        assertFalse(codec.isEncryptedPrefix(encrypted.copyOf(StudyPlanEncryptedPackageCodec.MagicSize - 1)))
        assertTrue(codec.isEncryptedPrefix(encrypted.copyOf(StudyPlanEncryptedPackageCodec.MagicSize)))
    }

    @Test
    fun wrongPasswordFailsWithoutReturningPlaintext() {
        val encrypted = codec.encrypt(plain, "correct horse".toCharArray())
        try {
            codec.decrypt(encrypted, "wrong battery".toCharArray())
            fail("Wrong password should fail")
        } catch (_: StudyPlanPackageAuthenticationException) {
            // Expected safe failure.
        }
    }

    @Test
    fun tamperingFailsAuthentication() {
        val encrypted = codec.encrypt(plain, "correct horse".toCharArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        try {
            codec.decrypt(encrypted, "correct horse".toCharArray())
            fail("Tampered package should fail")
        } catch (_: StudyPlanPackageAuthenticationException) {
            // Expected safe failure.
        }
    }
}
