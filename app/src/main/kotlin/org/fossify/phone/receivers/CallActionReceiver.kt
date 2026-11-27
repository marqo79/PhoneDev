package org.fossify.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.CallAudioState
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.helpers.ACCEPT_CALL
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.helpers.DECLINE_CALL
import org.fossify.phone.helpers.TOGGLE_CALL_MICROPHONE
import org.fossify.phone.helpers.TOGGLE_CALL_SPEAKER
import org.fossify.phone.models.AudioRoute

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACCEPT_CALL -> {
                context.startActivity(CallActivity.getStartIntent(context))
                CallManager.accept()
            }

            DECLINE_CALL -> CallManager.reject()
            TOGGLE_CALL_MICROPHONE -> CallManager.toggleMicrophoneMute()
            TOGGLE_CALL_SPEAKER -> {
                val route = if (CallManager.getCallAudioRoute() == AudioRoute.SPEAKER) {
                    CallAudioState.ROUTE_WIRED_OR_EARPIECE
                } else {
                    CallAudioState.ROUTE_SPEAKER
                }
                CallManager.setAudioRoute(route)
            }
        }
    }
}
