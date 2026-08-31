package com.omb9.glucosehero.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.omb9.glucosehero.domain.model.LogEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around WorkManager for the optional post-meal glucose check.
 *
 * Scheduling is done with a single unique work name and
 * [ExistingWorkPolicy.REPLACE]: logging another meal or bolus before the
 * previous reminder fires simply replaces the pending work, so the user gets
 * exactly one notification two hours after their *latest* meal/bolus.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun schedulePostMealCheck(event: LogEvent) {
        if (!event.qualifiesForPostMealReminder) return

        val mealName = event.mealDescription?.takeIf { it.isNotBlank() }
        val inputData = Data.Builder()
            .putLong(PostMealReminderWorker.KEY_TIMESTAMP, event.timestamp)
            .apply {
                mealName?.let { putString(PostMealReminderWorker.KEY_MEAL_NAME, it) }
            }
            .build()

        val request = OneTimeWorkRequestBuilder<PostMealReminderWorker>()
            .setInitialDelay(Duration.ofHours(2))
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            TAG_POST_MEAL_CHECK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelPostMealCheck() {
        WorkManager.getInstance(context).cancelUniqueWork(TAG_POST_MEAL_CHECK)
    }

    /**
     * Cancels only when the deleted entry could have been the one that
     * scheduled the currently pending reminder (a meal/bolus logged within the
     * two-hour window). Deleting older history does not disturb a newer
     * reminder.
     */
    fun cancelIfTriggering(event: LogEvent) {
        if (event.qualifiesForPostMealReminder &&
            System.currentTimeMillis() - event.timestamp <= REMINDER_WINDOW_MILLIS
        ) {
            cancelPostMealCheck()
        }
    }

    private val LogEvent.qualifiesForPostMealReminder: Boolean
        get() = glucoseMgdl == null &&
            (carbsGrams != null || proteinGrams != null || fatGrams != null ||
                !mealDescription.isNullOrBlank() || insulinBolusUnits != null)

    companion object {
        const val TAG_POST_MEAL_CHECK = "post_meal_check"
        const val REMINDER_WINDOW_MILLIS = 2 * 60 * 60 * 1000L
    }
}
