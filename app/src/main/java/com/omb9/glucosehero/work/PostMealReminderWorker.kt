package com.omb9.glucosehero.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omb9.glucosehero.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Fires the post-meal reminder two hours after a Meal or Bolus insulin entry
 * was saved — but only if the user still has reminders enabled by the time
 * the delay elapses. The DataStore check here is what makes disabling the
 * setting in the interim cancel the notification without any extra plumbing.
 */
@HiltWorker
class PostMealReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val notifier: PostMealReminderNotifier,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!settingsRepository.settings.first().postMealRemindersEnabled) {
            return Result.success()
        }

        val mealName = inputData.getString(KEY_MEAL_NAME)
        notifier.notifyPostMealCheck(mealName)
        return Result.success()
    }

    companion object {
        const val KEY_MEAL_NAME = "meal_name"
        const val KEY_TIMESTAMP = "timestamp"
    }
}
