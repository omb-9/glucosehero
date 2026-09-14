package com.omb9.glucosehero.crisis

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
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
import com.omb9.glucosehero.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HypoSosNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_hypo_sos_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_hypo_sos_channel_description)
            enableVibration(true)
            setBypassDnd(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun promptNotification(pending: HypoSosPending): Notification {
        val fullScreen = PendingIntent.getActivity(
            context,
            REQUEST_PROMPT,
            Intent(context, HypoSosPromptActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            context,
            REQUEST_DISMISS,
            Intent(context, HypoSosAlarmReceiver::class.java).apply {
                action = HypoSosManager.ACTION_DISMISS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = context.getString(
            R.string.notif_hypo_sos_prompt_body,
            pending.glucoseMgdl.toInt(),
            pending.trendLabel,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_glucosehero)
            .setContentTitle(context.getString(R.string.notif_hypo_sos_prompt_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(
                0,
                context.getString(R.string.notif_hypo_sos_im_ok),
                dismiss,
            )
            .build()
    }

    @SuppressLint("MissingPermission")
    fun notifyPrompt(pending: HypoSosPending) {
        if (!canNotify()) return
        NotificationManagerCompat.from(context).notify(PROMPT_ID, promptNotification(pending))
    }

    fun cancelPrompt() {
        NotificationManagerCompat.from(context).cancel(PROMPT_ID)
    }

    @SuppressLint("MissingPermission")
    fun notifyDispatchResult(results: List<RecipientSmsResult>) {
        if (!canNotify()) return
        val body = if (results.isEmpty()) {
            context.getString(R.string.notif_hypo_sos_failed_body)
        } else {
            results.joinToString("\n") { result ->
                when (result.status) {
                    SmsSendStatus.SENT ->
                        context.getString(R.string.notif_hypo_sos_sent_ok, result.label)
                    SmsSendStatus.NO_SERVICE ->
                        context.getString(R.string.notif_hypo_sos_no_service, result.label)
                    SmsSendStatus.RADIO_OFF ->
                        context.getString(R.string.notif_hypo_sos_radio_off, result.label)
                    SmsSendStatus.HANDED_TO_MESSAGING_APP ->
                        context.getString(R.string.notif_hypo_sos_handed_to_app, result.label)
                    SmsSendStatus.FAILED ->
                        context.getString(R.string.notif_hypo_sos_failed_recipient, result.label)
                }
            }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_glucosehero)
            .setContentTitle(context.getString(R.string.notif_hypo_sos_sent_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(RESULT_ID, notification)
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_ID = "hypo_sos"
        const val PROMPT_ID = 4310
        private const val RESULT_ID = 4311
        private const val REQUEST_PROMPT = 4312
        private const val REQUEST_DISMISS = 4313
    }
}
