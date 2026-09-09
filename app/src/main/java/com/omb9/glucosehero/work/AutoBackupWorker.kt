package com.omb9.glucosehero.work

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omb9.glucosehero.data.backup.CloudBackupProvider
import com.omb9.glucosehero.data.backup.EncryptedCloudBackupManager
import com.omb9.glucosehero.data.export.BackupManager
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Writes a timestamped JSON backup into the user-selected document tree and
 * prunes it to the most recent N files.
 *
 * The tree permission is persisted across reboots but not reinstalls, and the
 * user can revoke it in system settings. A revoked permission surfaces as a
 * [SecurityException] on the next write; we disable the schedule and report it
 * in Settings instead of retrying against a permission the user chose not to
 * grant.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupManager: BackupManager,
    private val encryptedCloudBackupManager: EncryptedCloudBackupManager,
    private val settingsDataStore: SettingsDataStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val enabled = settingsDataStore.backupEnabled.first()
        val treeUri = settingsDataStore.backupDirUri.first()
        if (!enabled || treeUri.isNullOrBlank()) return Result.success()

        return try {
            backupManager.exportToTree(Uri.parse(treeUri))
            settingsDataStore.setBackupLastRun(System.currentTimeMillis())
            uploadEncryptedCloudIfConfigured()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            // Permission was revoked in system settings; disable instead of
            // retrying forever. Settings surfaces the disabled state as a line
            // item so the user can re-pick a folder.
            settingsDataStore.setBackupEnabled(false)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun uploadEncryptedCloudIfConfigured() {
        val provider = settingsDataStore.cloudBackupProvider.first()
        if (provider == CloudBackupProvider.NONE) return
        try {
            encryptedCloudBackupManager.uploadEncrypted()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Log.w(UNIQUE_NAME, "Encrypted cloud upload failed")
        }
    }

    companion object {
        const val UNIQUE_NAME = "auto_backup_periodic"
    }
}
