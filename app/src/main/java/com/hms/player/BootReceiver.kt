package com.hms.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Opens the app automatically when the TV starts (if the option is on in Settings, TV mode only). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED &&
            a != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            a != "android.intent.action.QUICKBOOT_POWERON" &&
            a != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return
        val store = Store(context)
        if (!store.autoStart || store.mode(context) != DeviceMode.TV) return
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (e: Exception) { /* background start blocked by the system */ }
    }
}
