package org.fossify.phone.receivers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.ContactsContract.PhoneLookup
import android.telecom.TelecomManager
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import org.fossify.commons.extensions.notificationManager
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity

class MissedCallNotificationReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val manager = context.notificationManager
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        if (count <= 0) {
            manager.cancel(NOTIFICATION_ID)
            return
        }

        val status = if (count == 1) context.getString(R.string.missed_call_notification)
        else context.getString(R.string.missed_calls_notification, count)
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER).orEmpty()
        val caller = if (count == 1 && number.isNotBlank()) findContactName(context, number) ?: number else null
        val callback = intent.getParcelableExtra<PendingIntent>(EXTRA_CALL_BACK_INTENT)
        val clear = intent.getParcelableExtra<PendingIntent>(EXTRA_CLEAR_MISSED_CALLS_INTENT)
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openApp = PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_IMMUTABLE)
        val view = RemoteViews(context.packageName, R.layout.call_notification).apply {
            setTextViewText(R.id.notification_caller_name, caller ?: status)
            setTextViewText(R.id.notification_call_status, status)
            setViewVisibility(R.id.notification_call_status, if (caller == null) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.notification_decline_call, View.GONE)
            setViewVisibility(R.id.notification_accept_call, if (callback == null) View.GONE else View.VISIBLE)
            if (callback != null) {
                setViewVisibility(R.id.notification_action_space_start, View.VISIBLE)
                setViewVisibility(R.id.notification_action_space_end, View.VISIBLE)
                setOnClickPendingIntent(R.id.notification_accept_call, callback)
                setContentDescription(R.id.notification_accept_call, context.getString(R.string.missed_call_callback))
            }
        }
        val compactView = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
            RemoteViews(view).apply {
                setViewVisibility(R.id.notification_call_status, View.GONE)
                setTextViewTextSize(R.id.notification_caller_name, TypedValue.COMPLEX_UNIT_SP, 13f)
                setViewLayoutHeight(R.id.notification_accept_call, 32f, TypedValue.COMPLEX_UNIT_DIP)
            }
        } else {
            view
        }
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.missed_calls_channel), NotificationManager.IMPORTANCE_HIGH))
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_missed_vector)
            .setContentTitle(caller ?: status)
            .setContentText(status)
            .setCustomContentView(compactView)
            .setCustomBigContentView(view)
            .setCustomHeadsUpContentView(view)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp)
            .setDeleteIntent(clear)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun findContactName(context: Context, number: String): String? {
        val uri = PhoneLookup.CONTENT_FILTER_URI.buildUpon().appendPath(number).build()
        return runCatching {
            context.contentResolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private companion object {
        // Telecom includes these PendingIntents in the missed-call broadcast to the default dialer.
        const val EXTRA_CALL_BACK_INTENT = "android.telecom.extra.CALL_BACK_INTENT"
        const val EXTRA_CLEAR_MISSED_CALLS_INTENT = "android.telecom.extra.CLEAR_MISSED_CALLS_INTENT"
        const val CHANNEL_ID = "missed_calls"
        const val NOTIFICATION_ID = 43
    }
}
