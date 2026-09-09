package com.omb9.glucosehero.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedBackupCipherTest {

    private val cipher = EncryptedBackupCipher(AesDataKeyWrapper())

    @Test
    fun roundTrip_preservesUtf8JsonPayload() {
        val plain = """{"format":"glucosehero.backup","entries":[{"glucoseMgdl":101.5}]}"""
            .repeat(200)
            .toByteArray(Charsets.UTF_8)
        val encrypted = ByteArrayOutputStream()
        cipher.encryptingOutputStream(encrypted).use { output ->
            output.write(plain)
        }
        val blob = encrypted.toByteArray()
        assertTrue(EncryptedBackupCipher.matchesMagic(blob.copyOf(4)))
        assertFalse(blob.contentEquals(plain))

        val decrypted = cipher.decryptingInputStream(ByteArrayInputStream(blob))
            .use { it.readBytes() }
        assertArrayEquals(plain, decrypted)
    }

    @Test
    fun looksEncrypted_detectsMagicWithoutConsumingStream() {
        val payload = "tiny".toByteArray()
        val encrypted = ByteArrayOutputStream()
        cipher.encryptingOutputStream(encrypted).use { it.write(payload) }
        val peek = EncryptedBackupCipher.looksEncrypted(ByteArrayInputStream(encrypted.toByteArray()))
        assertTrue(peek.encrypted)
        val jsonPeek = EncryptedBackupCipher.looksEncrypted(
            ByteArrayInputStream("""{"format":"glucosehero.backup"}""".toByteArray()),
        )
        assertFalse(jsonPeek.encrypted)
    }

    @Test
    fun decrypt_rejectsWrongWrapper() {
        val encrypted = ByteArrayOutputStream()
        cipher.encryptingOutputStream(encrypted).use { it.write("secret-medical".toByteArray()) }
        val other = EncryptedBackupCipher(AesDataKeyWrapper(seed = 99))
        try {
            other.decryptingInputStream(ByteArrayInputStream(encrypted.toByteArray())).use { it.readBytes() }
            org.junit.Assert.fail("Expected decrypt to fail with a different wrapping key")
        } catch (_: Exception) {
            // Expected: the DEK cannot be unwrapped or the GCM tag fails.
        }
    }

    @Test
    fun suggestedEncryptedName_usesGhzkSuffix() {
        val name = com.omb9.glucosehero.data.export.suggestedEncryptedBackupFileName(
            java.time.LocalDate.of(2026, 9, 8),
        )
        assertEquals("glucosehero-backup-2026-09-08.ghzk", name)
    }
}

/**
 * Software AES-GCM stand-in for Android Keystore so the envelope format can
 * be tested on the JVM. Matches KeystoreManager's IV || ciphertext layout.
 */
private class AesDataKeyWrapper(
    seed: Int = 7,
) : DataKeyWrapper {
    private val wrappingKey = SecretKeySpec(ByteArray(32) { (it + seed).toByte() }, "AES")

    override fun wrap(rawKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, SecureRandom())
        return cipher.iv + cipher.doFinal(rawKey)
    }

    override fun unwrap(wrappedKey: ByteArray): ByteArray {
        val iv = wrappedKey.copyOfRange(0, 12)
        val cipherText = wrappedKey.copyOfRange(12, wrappedKey.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }
}
