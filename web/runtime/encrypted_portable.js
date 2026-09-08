"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.EncryptedPortableAuthenticationError = exports.MAX_ENCRYPTED_PORTABLE_BYTES = exports.ENCRYPTED_PORTABLE_TAG_BITS = exports.ENCRYPTED_PORTABLE_NONCE_BYTES = exports.ENCRYPTED_PORTABLE_SALT_BYTES = exports.ENCRYPTED_PORTABLE_MIN_PASSWORD_LENGTH = exports.ENCRYPTED_PORTABLE_MAX_ITERATIONS = exports.ENCRYPTED_PORTABLE_MIN_ITERATIONS = exports.ENCRYPTED_PORTABLE_PBKDF2_ITERATIONS = exports.ENCRYPTED_PORTABLE_VERSION = exports.ENCRYPTED_PORTABLE_MAGIC = void 0;
exports.buildEncryptedPortableHeader = buildEncryptedPortableHeader;
exports.isEncryptedPortableBytes = isEncryptedPortableBytes;
exports.parseEncryptedPortableHeader = parseEncryptedPortableHeader;
exports.encryptPortableBytes = encryptPortableBytes;
exports.decryptPortableBytes = decryptPortableBytes;
exports.encryptPortableText = encryptPortableText;
exports.decryptPortableText = decryptPortableText;
exports.encryptedPortableFilename = encryptedPortableFilename;
exports.ENCRYPTED_PORTABLE_MAGIC = "STUDYFLOWENC";
exports.ENCRYPTED_PORTABLE_VERSION = 1;
exports.ENCRYPTED_PORTABLE_PBKDF2_ITERATIONS = 210000;
exports.ENCRYPTED_PORTABLE_MIN_ITERATIONS = 100000;
exports.ENCRYPTED_PORTABLE_MAX_ITERATIONS = 1000000;
exports.ENCRYPTED_PORTABLE_MIN_PASSWORD_LENGTH = 8;
exports.ENCRYPTED_PORTABLE_SALT_BYTES = 16;
exports.ENCRYPTED_PORTABLE_NONCE_BYTES = 12;
exports.ENCRYPTED_PORTABLE_TAG_BITS = 128;
exports.MAX_ENCRYPTED_PORTABLE_BYTES = 205 * 1024 * 1024;
const encoder = new TextEncoder();
const decoder = new TextDecoder("utf-8", { fatal: true });
const MAGIC_BYTES = encoder.encode(exports.ENCRYPTED_PORTABLE_MAGIC);
class EncryptedPortableAuthenticationError extends TypeError {
    constructor(message = "The password is incorrect or the encrypted StudyFlow package is corrupted.") {
        super(message);
        this.name = "EncryptedPortableAuthenticationError";
    }
}
exports.EncryptedPortableAuthenticationError = EncryptedPortableAuthenticationError;
function requireCrypto(value = globalThis.crypto) {
    if (!value?.subtle || typeof value.getRandomValues !== "function")
        throw new TypeError("WebCrypto is unavailable in this browser");
    return value;
}
function passwordBytes(password) {
    if (typeof password !== "string" || password.length < exports.ENCRYPTED_PORTABLE_MIN_PASSWORD_LENGTH)
        throw new TypeError(`Encrypted StudyFlow exports require at least ${exports.ENCRYPTED_PORTABLE_MIN_PASSWORD_LENGTH} characters.`);
    return encoder.encode(password);
}
function validateIterations(iterations) {
    if (!Number.isInteger(iterations) || iterations < exports.ENCRYPTED_PORTABLE_MIN_ITERATIONS || iterations > exports.ENCRYPTED_PORTABLE_MAX_ITERATIONS)
        throw new TypeError("Encrypted package key-derivation settings are invalid.");
    return iterations;
}
function requireByteLength(bytes, length, label) {
    if (!(bytes instanceof Uint8Array) || bytes.byteLength !== length)
        throw new TypeError(`${label} must contain exactly ${length} bytes.`);
    return new Uint8Array(bytes);
}
function buildEncryptedPortableHeader(salt, nonce, iterations = exports.ENCRYPTED_PORTABLE_PBKDF2_ITERATIONS) {
    const normalizedIterations = validateIterations(iterations);
    if (salt.byteLength < 16 || salt.byteLength > 64)
        throw new TypeError("Encrypted package salt length is invalid.");
    if (nonce.byteLength < 12 || nonce.byteLength > 16)
        throw new TypeError("Encrypted package nonce length is invalid.");
    const output = new Uint8Array(MAGIC_BYTES.length + 1 + 4 + 1 + salt.byteLength + 1 + nonce.byteLength);
    let offset = 0;
    output.set(MAGIC_BYTES, offset);
    offset += MAGIC_BYTES.length;
    output[offset++] = exports.ENCRYPTED_PORTABLE_VERSION;
    new DataView(output.buffer).setUint32(offset, normalizedIterations, false);
    offset += 4;
    output[offset++] = salt.byteLength;
    output.set(salt, offset);
    offset += salt.byteLength;
    output[offset++] = nonce.byteLength;
    output.set(nonce, offset);
    return output;
}
function isEncryptedPortableBytes(value) {
    const bytes = value instanceof Uint8Array ? value : new Uint8Array(value);
    return bytes.byteLength >= MAGIC_BYTES.byteLength && MAGIC_BYTES.every((byte, index) => bytes[index] === byte);
}
function parseEncryptedPortableHeader(value) {
    const bytes = value instanceof Uint8Array ? value : new Uint8Array(value);
    try {
        if (!isEncryptedPortableBytes(bytes))
            throw new TypeError("This is not an encrypted StudyFlow package.");
        let offset = MAGIC_BYTES.length;
        const version = bytes[offset++];
        if (version !== exports.ENCRYPTED_PORTABLE_VERSION)
            throw new TypeError(`Unsupported encrypted package version ${version}.`);
        if (bytes.byteLength < offset + 5)
            throw new TypeError("Encrypted StudyFlow package header is incomplete.");
        const iterations = validateIterations(new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getUint32(offset, false));
        offset += 4;
        const saltLength = bytes[offset++];
        if (saltLength < 16 || saltLength > 64 || bytes.byteLength < offset + saltLength + 1)
            throw new TypeError("Encrypted package salt length is invalid.");
        const salt = new Uint8Array(bytes.slice(offset, offset + saltLength));
        offset += saltLength;
        const nonceLength = bytes[offset++];
        if (nonceLength < 12 || nonceLength > 16 || bytes.byteLength < offset + nonceLength + 16)
            throw new TypeError("Encrypted package nonce length is invalid.");
        const nonce = new Uint8Array(bytes.slice(offset, offset + nonceLength));
        offset += nonceLength;
        const header = new Uint8Array(bytes.slice(0, offset));
        return { version, iterations, salt, nonce, header, ciphertextOffset: offset };
    }
    catch (error) {
        if (error instanceof EncryptedPortableAuthenticationError)
            throw error;
        throw new EncryptedPortableAuthenticationError(error instanceof Error ? error.message : "Encrypted StudyFlow package header is invalid.");
    }
}
async function deriveAesKey(password, salt, iterations, cryptoImpl) {
    const raw = passwordBytes(password);
    try {
        const material = await cryptoImpl.subtle.importKey("raw", raw, { name: "PBKDF2" }, false, ["deriveKey"]);
        return await cryptoImpl.subtle.deriveKey({ name: "PBKDF2", hash: "SHA-256", salt, iterations }, material, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
    }
    finally {
        raw.fill(0);
    }
}
async function encryptPortableBytes(plain, password, options = {}) {
    const cryptoImpl = requireCrypto(options.crypto);
    const iterations = validateIterations(options.iterations ?? exports.ENCRYPTED_PORTABLE_PBKDF2_ITERATIONS);
    const salt = options.salt ? new Uint8Array(options.salt) : cryptoImpl.getRandomValues(new Uint8Array(exports.ENCRYPTED_PORTABLE_SALT_BYTES));
    const nonce = options.nonce ? new Uint8Array(options.nonce) : cryptoImpl.getRandomValues(new Uint8Array(exports.ENCRYPTED_PORTABLE_NONCE_BYTES));
    if (options.salt)
        requireByteLength(salt, exports.ENCRYPTED_PORTABLE_SALT_BYTES, "Encrypted package test salt");
    if (options.nonce)
        requireByteLength(nonce, exports.ENCRYPTED_PORTABLE_NONCE_BYTES, "Encrypted package test nonce");
    const header = buildEncryptedPortableHeader(salt, nonce, iterations);
    const key = await deriveAesKey(password, salt, iterations, cryptoImpl);
    const source = plain instanceof Uint8Array ? plain : new Uint8Array(plain);
    const encrypted = await cryptoImpl.subtle.encrypt({ name: "AES-GCM", iv: nonce, additionalData: header, tagLength: exports.ENCRYPTED_PORTABLE_TAG_BITS }, key, source);
    const output = new Uint8Array(header.byteLength + encrypted.byteLength);
    output.set(header, 0);
    output.set(new Uint8Array(encrypted), header.byteLength);
    return output;
}
async function decryptPortableBytes(encrypted, password, options = {}) {
    const cryptoImpl = requireCrypto(options.crypto);
    const bytes = encrypted instanceof Uint8Array ? encrypted : new Uint8Array(encrypted);
    const maximum = options.maximumEncryptedBytes ?? exports.MAX_ENCRYPTED_PORTABLE_BYTES;
    if (bytes.byteLength > maximum)
        throw new TypeError("Encrypted StudyFlow package exceeds the supported size.");
    const envelope = parseEncryptedPortableHeader(bytes);
    const key = await deriveAesKey(password, envelope.salt, envelope.iterations, cryptoImpl);
    const ciphertext = bytes.slice(envelope.ciphertextOffset);
    try {
        const plain = await cryptoImpl.subtle.decrypt({ name: "AES-GCM", iv: envelope.nonce, additionalData: envelope.header, tagLength: exports.ENCRYPTED_PORTABLE_TAG_BITS }, key, ciphertext);
        return new Uint8Array(plain);
    }
    catch {
        throw new EncryptedPortableAuthenticationError();
    }
}
async function encryptPortableText(text, password, options = {}) {
    return encryptPortableBytes(encoder.encode(String(text)), password, options);
}
async function decryptPortableText(encrypted, password, options = {}) {
    const bytes = await decryptPortableBytes(encrypted, password, options);
    try {
        return decoder.decode(bytes);
    }
    catch {
        throw new EncryptedPortableAuthenticationError("The encrypted StudyFlow package decrypted but does not contain valid UTF-8 portable data.");
    }
    finally {
        bytes.fill(0);
    }
}
function encryptedPortableFilename(basePortableFilename) {
    const base = String(basePortableFilename || "studyflow.studyflow.json").replace(/\.studyflow\.json$/i, "").replace(/\.json$/i, "");
    return `${base}.studyflow.enc`;
}
