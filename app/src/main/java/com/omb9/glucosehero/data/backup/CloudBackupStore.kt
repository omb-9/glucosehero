package com.omb9.glucosehero.data.backup

/**
 * Remote destination for an already-encrypted backup blob.
 *
 * Implementations must never log credentials, wrapping keys, or plaintext
 * medical JSON. The bytes they receive from [EncryptedCloudBackupManager]
 * are ciphertext.
 */
interface CloudBackupStore {
    val provider: CloudBackupProvider

    suspend fun upload(remoteName: String, encryptedFile: java.io.File): CloudBackupFile

    suspend fun download(file: CloudBackupFile, destination: java.io.File)

    suspend fun list(): List<CloudBackupFile>

    suspend fun delete(file: CloudBackupFile)
}

enum class CloudBackupProvider {
    NONE,
    WEBDAV,
    GOOGLE_DRIVE,
}

data class CloudBackupFile(
    val id: String,
    val name: String,
    val sizeBytes: Long? = null,
    val modifiedAtMillis: Long? = null,
)

class CloudBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
