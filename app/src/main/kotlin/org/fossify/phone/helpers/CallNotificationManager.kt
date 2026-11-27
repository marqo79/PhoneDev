package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.SystemClock
import android.telecom.Call
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import org.fossify.commons.extensions.adjustForContrast
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.setText
import org.fossify.commons.extensions.setVisibleIf
import org.fossify.phone.R
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.extensions.getAvailableSIMCardLabels
import org.fossify.phone.models.AudioRoute
import org.fossify.phone.receivers.CallActionReceiver

class CallNotificationManager(private val context: Context) {
    companion object {
        private const val CALL_NOTIFICATION_ID = 42
        private const val ACCEPT_CALL_CODE = 0
        private const val DECLINE_CALL_CODE = 1
    }

    private val notificationManager = context.notificationManager
    private val callContactAvatarHelper = CallContactAvatarHelper(context)
    private val usesXiaomiNotificationPanel = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    @SuppressLint("NewApi")
    fun setupNotification(lowPriority: Boolean = false) {
        val call = CallManager.getPrimaryCall()
        getCallContact(context.applicationContext, call) { callContact ->
            val callContactAvatar = callContactAvatarHelper.getCallContactAvatar(callContact)
            val callState = CallManager.getState()
            val isHighPriority = callState == Call.STATE_RINGING && !lowPriority
            val channelId =
                if (isHighPriority) "simple_dialer_call_high_priority" else "simple_dialer_call"
            createNotificationChannel(isHighPriority, channelId)

            val openAppIntent = CallActivity.getStartIntent(context)
            val openAppPendingIntent =
                PendingIntent.getActivity(context, 0, openAppIntent, PendingIntent.FLAG_MUTABLE)

            val acceptCallIntent = Intent(context, CallActionReceiver::class.java)
            acceptCallIntent.action = ACCEPT_CALL
            val acceptPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    ACCEPT_CALL_CODE,
                    acceptCallIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                )

            val declineCallIntent = Intent(context, CallActionReceiver::class.java)
            declineCallIntent.action = DECLINE_CALL
            val declinePendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    DECLINE_CALL_CODE,
                    declineCallIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                )

            var callerName = callContact.name.ifEmpty { context.getString(R.string.unknown_caller) }
            if (callContact.numberLabel.isNotEmpty()) {
                callerName += " - ${callContact.numberLabel}"
            }

            val contentTextId = when (callState) {
                Call.STATE_RINGING -> R.string.is_calling
                Call.STATE_CONNECTING, Call.STATE_DIALING -> R.string.dialing
                Call.STATE_DISCONNECTED -> R.string.call_ended
                Call.STATE_DISCONNECTING -> R.string.call_ending
                else -> R.string.ongoing_call
            }
            val connectedAt = call?.details?.connectTimeMillis?.takeIf { it > 0 }
            val showNumber = callState == Call.STATE_ACTIVE && callContact.number.isNotEmpty() && callContact.number != callContact.name
            val sims = context.getAvailableSIMCardLabels()
            val sim = sims.takeIf { it.size > 1 }
                ?.firstOrNull { it.handle == call?.details?.accountHandle }
            val simName = sim?.displayName
            val simColor = sim?.color?.adjustForContrast(context.getProperBackgroundColor())
            val status = if (showNumber) callContact.number else context.getString(contentTextId)
            val isActive = callState == Call.STATE_ACTIVE

            val notificationView = RemoteViews(context.packageName, R.layout.call_notification).apply {
                setText(R.id.notification_caller_name, callerName)
                setTextViewText(R.id.notification_call_status, status)
                setVisibleIf(R.id.notification_accept_call, callState == Call.STATE_RINGING)
                setVisibleIf(R.id.notification_answer_gap, callState == Call.STATE_RINGING)
                val hasSingleAction = !isActive && callState != Call.STATE_RINGING
                setVisibleIf(R.id.notification_action_space_start, hasSingleAction)
                setVisibleIf(R.id.notification_action_space_end, hasSingleAction)

                setOnClickPendingIntent(R.id.notification_decline_call, declinePendingIntent)
                setOnClickPendingIntent(R.id.notification_accept_call, acceptPendingIntent)
                setVisibleIf(R.id.notification_speaker, isActive)
                setVisibleIf(R.id.notification_microphone, isActive)
                val endDescription = if (callState == Call.STATE_RINGING) R.string.decline_call else R.string.end_call
                setContentDescription(R.id.notification_decline_call, context.getString(endDescription))

                if (isActive) {
                    val speakerOn = CallManager.getCallAudioRoute() == AudioRoute.SPEAKER
                    val muted = CallManager.isMicrophoneMuted()
                    setAudioAction(
                        R.id.notification_speaker,
                        TOGGLE_CALL_SPEAKER,
                        speakerOn,
                        if (speakerOn) R.drawable.ic_volume_up_vector else R.drawable.ic_speaker_off_vector,
                        if (speakerOn) R.string.turn_speaker_off else R.string.turn_speaker_on
                    )
                    setAudioAction(
                        R.id.notification_microphone,
                        TOGGLE_CALL_MICROPHONE,
                        muted,
                        if (muted) R.drawable.ic_microphone_off_vector else R.drawable.ic_microphone_vector,
                        if (muted) R.string.turn_microphone_on else R.string.turn_microphone_off
                    )
                }

                if (callContactAvatar != null) {
                    setViewVisibility(R.id.notification_thumbnail, View.VISIBLE)
                    setImageViewBitmap(R.id.notification_caller_photo, callContactAvatar)
                }
                if (simName != null && simColor != null) {
                    setViewVisibility(R.id.notification_metadata, View.VISIBLE)
                    setViewVisibility(R.id.notification_sim_name, View.VISIBLE)
                    setText(R.id.notification_sim_name, simName)
                    setViewPadding(R.id.notification_sim_name, dpToPx(4f), 0, dpToPx(4f), 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setInt(R.id.notification_sim_name, "setBackgroundResource", R.drawable.notification_sim_label)
                        setColorStateList(R.id.notification_sim_name, "setBackgroundTintList", ColorStateList.valueOf(simColor))
                        setTextColor(R.id.notification_sim_name, simColor.getContrastColor())
                    } else {
                        setTextColor(R.id.notification_sim_name, simColor)
                    }
                }
                if (isActive && connectedAt != null) {
                    setViewVisibility(R.id.notification_metadata, View.VISIBLE)
                    setViewVisibility(R.id.notification_timer, View.VISIBLE)
                    val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - connectedAt).coerceAtLeast(0)
                    setChronometer(R.id.notification_timer, base, null, true)
                }
            }

            val collapsedView = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RemoteViews(notificationView).apply {
                    setViewVisibility(R.id.notification_thumbnail, View.GONE)
                    setVisibleIf(R.id.notification_call_status, usesXiaomiNotificationPanel && !isActive)
                    setVisibleIf(R.id.notification_sim_name, simName != null && (usesXiaomiNotificationPanel || !isActive))
                    setTextViewTextSize(R.id.notification_caller_name, TypedValue.COMPLEX_UNIT_SP, if (usesXiaomiNotificationPanel) 16f else 13f)
                    val gap = if (usesXiaomiNotificationPanel) 6f else 0f
                    setViewLayoutMargin(R.id.notification_actions_holder, RemoteViews.MARGIN_TOP, gap, TypedValue.COMPLEX_UNIT_DIP)
                    if (usesXiaomiNotificationPanel) {
                        setViewPadding(R.id.notification_holder, 0, dpToPx(2f), 0, dpToPx(2f))
                        setActionSize(height = 48f, iconPadding = 8f)
                    } else {
                        setActionSize(height = 32f, iconPadding = 4f)
                    }
                }
            } else {
                notificationView
            }

            val builder = Notification.Builder(context, channelId)
                .setSmallIcon(if (callState == Call.STATE_ACTIVE) R.drawable.ic_call_person_badge else R.drawable.ic_phone_vector)
                .setContentIntent(openAppPendingIntent)
                .setCategory(Notification.CATEGORY_CALL)
                .setCustomContentView(collapsedView)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setUsesChronometer(callState == Call.STATE_ACTIVE)
                .setChannelId(channelId)
                .setStyle(Notification.DecoratedCustomViewStyle())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                notificationView.setActionSize(height = 48f, iconPadding = 8f)
            }
            builder.setCustomBigContentView(notificationView)
            if (isActive) {
                connectedAt?.let { builder.setWhen(it) }
            }

            if (isHighPriority) {
                builder.setCustomHeadsUpContentView(if (usesXiaomiNotificationPanel) collapsedView else notificationView)
                builder.setFullScreenIntent(openAppPendingIntent, true)
            }

            val notification = builder.build()
            // it's rare but possible for the call state to change by now
            if (CallManager.getPrimaryCall() == call && CallManager.getState() == callState) {
                notificationManager.notify(CALL_NOTIFICATION_ID, notification)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun RemoteViews.setActionSize(height: Float, iconPadding: Float) {
        val padding = dpToPx(iconPadding)
        for (viewId in intArrayOf(R.id.notification_speaker, R.id.notification_microphone, R.id.notification_decline_call, R.id.notification_accept_call)) {
            setViewLayoutHeight(viewId, height, TypedValue.COMPLEX_UNIT_DIP)
            setViewPadding(viewId, padding, padding, padding, padding)
        }
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()

    private fun RemoteViews.setAudioAction(viewId: Int, action: String, active: Boolean, icon: Int, description: Int) {
        val intent = Intent(context, CallActionReceiver::class.java).setAction(action)
        setOnClickPendingIntent(viewId, PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        setImageViewResource(viewId, icon)
        setContentDescription(viewId, context.getString(description))
        val background = if (active) R.drawable.notification_audio_active else R.drawable.notification_audio_background
        setInt(viewId, "setBackgroundResource", background)
    }

    fun createNotificationChannel(isHighPriority: Boolean, channelId: String) {
        val name = if (isHighPriority) {
            context.getString(R.string.call_notification_channel_high_priority)
        } else {
            context.getString(R.string.call_notification_channel)
        }

        val importance = if (isHighPriority) IMPORTANCE_HIGH else IMPORTANCE_DEFAULT
        NotificationChannel(channelId, name, importance).apply {
            setSound(null, null)
            notificationManager.createNotificationChannel(this)
        }
    }

    fun cancelNotification() {
        notificationManager.cancel(CALL_NOTIFICATION_ID)
    }
}
