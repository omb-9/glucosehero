package com.omb9.glucosehero.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.omb9.glucosehero.MainActivity
import com.omb9.glucosehero.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the optional "+2 hour post-meal glucose check" reminder. The channel
 * uses [NotificationManager.IMPORTANCE_LOW] so it arrives silently — no sound,
 * no vibration — and the tap action deep-links straight into the add-entry
 * sheet with the Glucose tab pre-selected.
 */
@Singleton
class PostMealReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_post_meal_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_post_meal_channel_description)
            setShowBadge(false)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun notifyPostMealCheck(mealName: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_ADD_GLUCOSE)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = if (mealName.isNullOrBlank()) {
            context.getString(R.string.notif_post_meal_body)
        } else {
            context.getString(R.string.notif_post_meal_body_with_name, mealName)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_glucosehero)
            .setContentTitle(context.getString(R.string.notif_post_meal_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "post_meal_reminders"
        private const val NOTIFICATION_ID = 4210
        private const val REQUEST_CODE = 4211
    }
}
