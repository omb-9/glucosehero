package com.omb9.glucosehero.data.backup

import com.omb9.glucosehero.data.security.KeystoreManager
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Wraps and unwraps the per-file AES-256 data-encryption key. Production uses
 * [KeystoreDataKeyWrapper]; tests inject a software AES wrapper so the file
 * format can be exercised on the JVM without Android Keystore.
 */
interface DataKeyWrapper {
    fun wrap(rawKey: ByteArray): ByteArray
    fun unwrap(wrappedKey: ByteArray): ByteArray
}

class KeystoreDataKeyWrapper @Inject constructor(
    private val keystoreManager: KeystoreManager,
) : DataKeyWrapper {
    override fun wrap(rawKey: ByteArray): ByteArray = keystoreManager.wrapDataKey(rawKey)
    override fun unwrap(wrappedKey: ByteArray): ByteArray = keystoreManager.unwrapDataKey(wrappedKey)
}

/**
 * Envelope encryption for GlucoseHero backup blobs.
 *
 * A random 256-bit DEK encrypts the JSON (or SQLite-equivalent) payload in
 * AES-GCM chunks. That DEK is wrapped by the non-exportable Android Keystore
 * key and stored only in the file header, so the wrapping key never leaves
 * the device.
 *
 * Wire format (big-endian):
 * `GHZK` | version:1 | wrappedLen:2 | wrappedDek | repeating
 * (chunkIndex:4 | plainLen:4 | nonce:12 | ciphertext+tag) until plainLen=0.
 */
@Singleton
class EncryptedBackupCipher @Inject constructor(
    private val wrapper: DataKeyWrapper,
) {

    fun encryptingOutputStream(output: OutputStream): OutputStream {
        val dekBytes = ByteArray(DEK_BYTES).also { SecureRandom().nextBytes(it) }
        return try {
            val wrapped = wrapper.wrap(dekBytes)
            require(wrapped.size in 1..MAX_WRAPPED_LEN) { "Wrapped data key was an unexpected size." }
            output.write(MAGIC)
            output.write(VERSION)
            writeShort(output, wrapped.size)
            output.write(wrapped)
            EncryptingOutputStream(output, SecretKeySpec(dekBytes, "AES"))
        } finally {
            dekBytes.fill(0)
        }
    }

    fun decryptingInputStream(input: InputStream): InputStream {
        val magic = ByteArray(MAGIC.size)
        readFully(input, magic)
        require(matchesMagic(magic)) { "This file is not an encrypted GlucoseHero backup." }
        val version = input.read()
        if (version != VERSION) {
            throw IOException("Unsupported encrypted backup version $version.")
        }
        val wrappedLen = readShort(input)
        if (wrappedLen !in 1..MAX_WRAPPED_LEN) {
            throw IOException("Encrypted backup header is corrupt.")
        }
        val wrapped = ByteArray(wrappedLen)
        readFully(input, wrapped)
        val dekBytes = wrapper.unwrap(wrapped)
        return try {
            DecryptingInputStream(input, SecretKeySpec(dekBytes, "AES"))
        } finally {
            dekBytes.fill(0)
        }
    }

    companion object {
        val MAGIC: ByteArray = byteArrayOf(0x47, 0x48, 0x5A, 0x4B) // GHZK
        const val VERSION: Int = 1
        const val ENCRYPTED_MIME: String = "application/octet-stream"
        const val FILE_SUFFIX: String = ".ghzk"

        fun matchesMagic(bytes: ByteArray): Boolean {
            if (bytes.size < MAGIC.size) return false
            return bytes[0] == MAGIC[0] &&
                bytes[1] == MAGIC[1] &&
                bytes[2] == MAGIC[2] &&
                bytes[3] == MAGIC[3]
        }

        fun looksEncrypted(input: InputStream): PeekResult {
            val buffered = if (input.markSupported()) input else input.buffered()
            buffered.mark(MAGIC.size)
            val magic = ByteArray(MAGIC.size)
            val read = readAtMost(buffered, magic)
            buffered.reset()
            val encrypted = read == MAGIC.size && matchesMagic(magic)
            return PeekResult(buffered, encrypted)
        }
    }
}

data class PeekResult(
    val stream: InputStream,
    val encrypted: Boolean,
)

private const val DEK_BYTES = 32
private const val IV_SIZE = 12
private const val TAG_BYTES = 16
private const val TAG_BITS = 128
private const val CHUNK_SIZE = 64 * 1024
private const val MAX_WRAPPED_LEN = 4096
private const val TRANSFORMATION = "AES/GCM/NoPadding"

private class EncryptingOutputStream(
    output: OutputStream,
    private val key: SecretKey,
) : FilterOutputStream(output) {
    private val pending = ByteArray(CHUNK_SIZE)
    private var pendingSize = 0
    private var chunkIndex = 0
    private var closed = false
    private val random = SecureRandom()

    override fun write(b: Int) {
        pending[pendingSize++] = b.toByte()
        if (pendingSize == CHUNK_SIZE) flushChunk()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var offset = off
        var remaining = len
        while (remaining > 0) {
            val copy = min(remaining, CHUNK_SIZE - pendingSize)
            System.arraycopy(b, offset, pending, pendingSize, copy)
            pendingSize += copy
            offset += copy
            remaining -= copy
            if (pendingSize == CHUNK_SIZE) flushChunk()
        }
    }

    override fun flush() {
        out.flush()
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            if (pendingSize > 0) flushChunk()
            writeInt(out, chunkIndex)
            writeInt(out, 0)
            out.flush()
        } finally {
            pending.fill(0)
            super.close()
        }
    }

    private fun flushChunk() {
        val nonce = ByteArray(IV_SIZE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(chunkIndex, pendingSize))
        val cipherText = cipher.doFinal(pending, 0, pendingSize)
        writeInt(out, chunkIndex)
        writeInt(out, pendingSize)
        out.write(nonce)
        out.write(cipherText)
        pending.fill(0)
        pendingSize = 0
        chunkIndex++
        nonce.fill(0)
    }
}

private class DecryptingInputStream(
    private val source: InputStream,
    private val key: SecretKey,
) : FilterInputStream(source) {
    private var buffer = ByteArray(0)
    private var pos = 0
    private var eof = false
    private var expectedIndex = 0

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n <= 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (pos >= buffer.size) {
            if (!refill()) return -1
        }
        val n = min(len, buffer.size - pos)
        System.arraycopy(buffer, pos, b, off, n)
        pos += n
        return n
    }

    override fun skip(n: Long): Long {
        var remaining = n
        var skipped = 0L
        val tmp = ByteArray(min(remaining, CHUNK_SIZE.toLong()).toInt())
        while (remaining > 0) {
            val read = read(tmp, 0, min(remaining, tmp.size.toLong()).toInt())
            if (read <= 0) break
            remaining -= read
            skipped += read
        }
        return skipped
    }

    override fun available(): Int = (buffer.size - pos).coerceAtLeast(0)

    override fun markSupported(): Boolean = false

    private fun refill(): Boolean {
        if (eof) {
            buffer = ByteArray(0)
            pos = 0
            return false
        }
        val index = readInt(source)
        val plainLen = readInt(source)
        if (plainLen == 0) {
            eof = true
            buffer.fill(0)
            buffer = ByteArray(0)
            pos = 0
            return false
        }
        if (index != expectedIndex) {
            throw IOException("Encrypted backup chunk order was invalid.")
        }
        if (plainLen !in 1..CHUNK_SIZE) {
            throw IOException("Encrypted backup chunk is corrupt.")
        }
        val nonce = ByteArray(IV_SIZE)
        readFully(source, nonce)
        val cipherBytes = ByteArray(plainLen + TAG_BYTES)
        readFully(source, cipherBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(index, plainLen))
        buffer.fill(0)
        buffer = cipher.doFinal(cipherBytes)
        pos = 0
        expectedIndex++
        nonce.fill(0)
        cipherBytes.fill(0)
        return true
    }
}

private fun aad(chunkIndex: Int, plainLen: Int): ByteArray {
    val bytes = ByteArray(8)
    writeIntTo(bytes, 0, chunkIndex)
    writeIntTo(bytes, 4, plainLen)
    return EncryptedBackupCipher.MAGIC + EncryptedBackupCipher.VERSION.toByte() + bytes
}

private fun writeShort(output: OutputStream, value: Int) {
    output.write((value ushr 8) and 0xFF)
    output.write(value and 0xFF)
}

private fun readShort(input: InputStream): Int {
    val hi = input.read()
    val lo = input.read()
    if (hi < 0 || lo < 0) throw IOException("Unexpected end of encrypted backup.")
    return (hi shl 8) or lo
}

private fun writeInt(output: OutputStream, value: Int) {
    output.write((value ushr 24) and 0xFF)
    output.write((value ushr 16) and 0xFF)
    output.write((value ushr 8) and 0xFF)
    output.write(value and 0xFF)
}

private fun readInt(input: InputStream): Int {
    val b1 = input.read()
    val b2 = input.read()
    val b3 = input.read()
    val b4 = input.read()
    if (b1 < 0 || b2 < 0 || b3 < 0 || b4 < 0) {
        throw IOException("Unexpected end of encrypted backup.")
    }
    return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
}

private fun writeIntTo(dest: ByteArray, offset: Int, value: Int) {
    dest[offset] = ((value ushr 24) and 0xFF).toByte()
    dest[offset + 1] = ((value ushr 16) and 0xFF).toByte()
    dest[offset + 2] = ((value ushr 8) and 0xFF).toByte()
    dest[offset + 3] = (value and 0xFF).toByte()
}

private fun readFully(input: InputStream, dest: ByteArray) {
    var offset = 0
    while (offset < dest.size) {
        val n = input.read(dest, offset, dest.size - offset)
        if (n < 0) throw IOException("Unexpected end of encrypted backup.")
        offset += n
    }
}

private fun readAtMost(input: InputStream, dest: ByteArray): Int {
    var offset = 0
    while (offset < dest.size) {
        val n = input.read(dest, offset, dest.size - offset)
        if (n < 0) break
        offset += n
    }
    return offset
}
