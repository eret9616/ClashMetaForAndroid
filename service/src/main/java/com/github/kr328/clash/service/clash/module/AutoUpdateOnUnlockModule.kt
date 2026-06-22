package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.ProfileReceiver
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.store.ServiceStore
import java.util.concurrent.TimeUnit

/**
 * Refresh all subscription profiles when the user unlocks the device, but only
 * when the screen has been idle for at least [IDLE_THRESHOLD] since the previous
 * unlock observed while Clash is running.
 *
 * Gated by [ServiceStore.autoUpdateOnUnlock]. Only active while a Clash service
 * is running (Android forbids registering ACTION_USER_PRESENT in the manifest).
 */
class AutoUpdateOnUnlockModule(service: Service) : Module<Unit>(service) {
    private val store = ServiceStore(service)

    // Timestamp (ms) of the previous unlock; 0 means "no baseline yet".
    private var lastUnlockAt: Long = 0L

    override suspend fun run() {
        val unlocks = receiveBroadcast(false) {
            addAction(Intent.ACTION_USER_PRESENT)
        }

        while (true) {
            unlocks.receive()

            if (!store.autoUpdateOnUnlock)
                continue

            val now = System.currentTimeMillis()
            val last = lastUnlockAt

            lastUnlockAt = now

            // First unlock since the service started: just record the baseline.
            if (last == 0L)
                continue

            val thresholdMs = TimeUnit.MINUTES.toMillis(
                store.autoUpdateOnUnlockInterval.coerceAtLeast(1).toLong()
            )

            val idle = now - last
            if (idle < thresholdMs)
                continue

            Log.i("AutoUpdateOnUnlock: idle ${idle / 1000}s >= ${thresholdMs / 1000}s threshold, updating subscriptions")

            updateAllSubscriptions()
        }
    }

    private suspend fun updateAllSubscriptions() {
        ImportedDao().queryAllUUIDs()
            .mapNotNull { ImportedDao().queryByUUID(it) }
            .filter { it.type != Profile.Type.File }
            .forEach { ProfileReceiver.schedule(service, it) }
    }
}
