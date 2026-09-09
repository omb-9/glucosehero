package com.omb9.glucosehero.data.backup

import android.content.Context
import android.net.Uri
import com.omb9.glucosehero.data.export.BackupExportSummary
import com.omb9.glucosehero.data.export.BackupImportSummary
import com.omb9.glucosehero.data.export.BackupManager
import com.omb9.glucosehero.data.export.BackupPreview
import com.omb9.glucosehero.data.export.ImportMode
import com.omb9.glucosehero.data.export.suggestedEncryptedBackupFileName
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.security.KeystoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Zero-knowledge cloud backup: the existing JSON Room export is wrapped with
 * AES-GCM before it leaves the device. The wrapping key stays in Android
 * Keystore, so a blob on WebDAV or Drive cannot be decrypted on any other
 * install.
 */
@Singleton
class EncryptedCloudBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val settingsDataStore: SettingsDataStore,
    private val keystoreManager: KeystoreManager,
    private val webDavFactory: WebDavCloudBackupStoreFactory,
    private val driveFactory: GoogleDriveCloudBackupStoreFactory,
) {

    suspend fun exportEncryptedTo(uri: Uri): BackupExportSummary =
        backupManager.exportEncryptedTo(uri)

    suspend fun previewFrom(uri: Uri): BackupPreview = backupManager.previewFrom(uri)

    suspend fun importFrom(uri: Uri, mode: ImportMode): BackupImportSummary =
        backupManager.importFrom(uri, mode)

    suspend fun uploadEncrypted(): CloudBackupFile = withContext(Dispatchers.IO) {
        val store = currentStore()
        val remoteName = suggestedEncryptedBackupFileName(LocalDate.now())
        val temp = File.createTempFile("gh-enc-backup-", EncryptedBackupCipher.FILE_SUFFIX, context.cacheDir)
        try {
            FileOutputStream(temp).use { backupManager.exportEncryptedTo(it) }
            val uploaded = store.upload(remoteName, temp)
            settingsDataStore.setCloudBackupLastRun(System.currentTimeMillis())
            settingsDataStore.setCloudBackupRemoteName(uploaded.name)
            settingsDataStore.setCloudBackupRemoteId(uploaded.id)
            uploaded
        } finally {
            temp.delete()
        }
    }

    suspend fun restoreLatest(mode: ImportMode): BackupImportSummary = withContext(Dispatchers.IO) {
        val store = currentStore()
        val remote = latestRemote(store)
            ?: throw CloudBackupException("No encrypted backup was found in the cloud.")
        val temp = File.createTempFile("gh-enc-restore-", EncryptedBackupCipher.FILE_SUFFIX, context.cacheDir)
        try {
            store.download(remote, temp)
            FileInputStream(temp).use { backupManager.importFrom(it, mode) }
        } finally {
            temp.delete()
        }
    }

    suspend fun listRemote(): List<CloudBackupFile> = withContext(Dispatchers.IO) {
        currentStore().list()
    }

    suspend fun saveWebDav(url: String, username: String, password: String) {
        settingsDataStore.setCloudBackupProvider(CloudBackupProvider.WEBDAV)
        settingsDataStore.setWebDavUrl(url.trim())
        settingsDataStore.setWebDavUsername(username.trim())
        val trimmedPassword = password.trim()
        if (trimmedPassword.isNotEmpty()) {
            settingsDataStore.setWebDavPasswordEnc(keystoreManager.encrypt(trimmedPassword))
        }
    }

    suspend fun saveDriveAccessToken(token: String) {
        settingsDataStore.setCloudBackupProvider(CloudBackupProvider.GOOGLE_DRIVE)
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            settingsDataStore.setDriveAccessTokenEnc(null)
        } else {
            settingsDataStore.setDriveAccessTokenEnc(keystoreManager.encrypt(trimmed))
        }
    }

    suspend fun setProvider(provider: CloudBackupProvider) {
        settingsDataStore.setCloudBackupProvider(provider)
    }

    suspend fun clearCloudCredentials() {
        settingsDataStore.setCloudBackupProvider(CloudBackupProvider.NONE)
        settingsDataStore.setWebDavUrl("")
        settingsDataStore.setWebDavUsername("")
        settingsDataStore.setWebDavPasswordEnc(null)
        settingsDataStore.setDriveAccessTokenEnc(null)
        settingsDataStore.setCloudBackupRemoteName(null)
        settingsDataStore.setCloudBackupRemoteId(null)
    }

    private suspend fun currentStore(): CloudBackupStore {
        return when (settingsDataStore.cloudBackupProvider.first()) {
            CloudBackupProvider.WEBDAV -> webDavStore()
            CloudBackupProvider.GOOGLE_DRIVE -> driveStore()
            CloudBackupProvider.NONE ->
                throw CloudBackupException("Choose WebDAV or Google Drive before uploading.")
        }
    }

    private suspend fun webDavStore(): WebDavCloudBackupStore {
        val url = settingsDataStore.webDavUrl.first().trim()
        val username = settingsDataStore.webDavUsername.first().trim()
        val encryptedPassword = settingsDataStore.webDavPasswordEnc.first()
        if (url.isEmpty() || username.isEmpty() || encryptedPassword.isNullOrBlank()) {
            throw CloudBackupException("WebDAV URL, username, and password are required.")
        }
        val password = try {
            keystoreManager.decrypt(encryptedPassword)
        } catch (_: Exception) {
            throw CloudBackupException("Could not unwrap the stored WebDAV password on this device.")
        }
        return webDavFactory.create(url, username, password)
    }

    private suspend fun driveStore(): GoogleDriveCloudBackupStore {
        val encrypted = settingsDataStore.driveAccessTokenEnc.first()
            ?: throw CloudBackupException("A Google Drive access token is required.")
        val token = try {
            keystoreManager.decrypt(encrypted)
        } catch (_: Exception) {
            throw CloudBackupException("Could not unwrap the stored Drive token on this device.")
        }
        if (token.isBlank()) throw CloudBackupException("A Google Drive access token is required.")
        return driveFactory.create(token)
    }

    private suspend fun latestRemote(store: CloudBackupStore): CloudBackupFile? {
        val listed = store.list()
        if (listed.isNotEmpty()) {
            return listed.maxByOrNull { it.modifiedAtMillis ?: 0L } ?: listed.first()
        }
        val storedId = settingsDataStore.cloudBackupRemoteId.first()
        val storedName = settingsDataStore.cloudBackupRemoteName.first()
        if (storedId.isNullOrBlank() && storedName.isNullOrBlank()) return null
        return CloudBackupFile(
            id = storedId.orEmpty(),
            name = storedName ?: suggestedEncryptedBackupFileName(),
        )
    }
}
