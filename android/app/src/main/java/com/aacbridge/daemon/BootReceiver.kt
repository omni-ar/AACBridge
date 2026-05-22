package com.aacbridge.daemon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aacbridge.AACBridgeApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val app =
            context.applicationContext
                    as AACBridgeApplication

        val activeSweep =
            app.appContainer.activeSweep

        val pendingResult =
            goAsync()

        GlobalScope.launch(Dispatchers.IO) {

            try {

                activeSweep.executeSweep()

            } catch (e: Exception) {

                e.printStackTrace()

            } finally {

                pendingResult.finish()
            }
        }
    }
}