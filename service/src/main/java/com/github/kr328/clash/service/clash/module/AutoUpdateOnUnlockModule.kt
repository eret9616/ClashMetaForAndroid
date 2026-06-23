package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.store.ServiceStore
import java.util.concurrent.TimeUnit

/**
 * Refresh all subscription profiles when the user unlocks the device, but only
 * when the screen has actually been off (idle) for at least the configured
 * threshold since it was last turned off.
 *
 * The idle period is measured from [Intent.ACTION_SCREEN_OFF] to the following
 * [Intent.ACTION_USER_PRESENT], so it reflects real sleep time rather than the
 * gap between unlocks (which would also count active on-screen usage).
 *
 * Gated by [ServiceStore.autoUpdateOnUnlock]. Only active while a Clash service
 * is running (Android 8+ forbids registering screen/unlock broadcasts in the
 * manifest, so they are registered at runtime here).
 */
class AutoUpdateOnUnlockModule(service: Service) : Module<Unit>(service) {
    private val store = ServiceStore(service)

    // Timestamp (ms) when the screen turned off and the current idle period
    // began; 0 means no idle period is in progress (screen on, or already
    // consumed by an unlock).
    private var screenOffAt: Long = 0L

    override suspend fun run() {
        val events = receiveBroadcast(false) {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        while (true) {
            when (events.receive().action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Remember the start of the idle period. Don't overwrite an
                    // in-progress one, so a brief ambient wake / glance does not
                    // reset the idle clock.
                    if (screenOffAt == 0L)
                        screenOffAt = System.currentTimeMillis()
                }
                Intent.ACTION_USER_PRESENT -> {
                    val offAt = screenOffAt
                    screenOffAt = 0L

                    if (!store.autoUpdateOnUnlock)
                        continue

                    // No recorded idle period (service started with screen on, or
                    // already consumed): nothing to do.
                    if (offAt == 0L)
                        continue

                    val thresholdMs = TimeUnit.MINUTES.toMillis(
                        store.autoUpdateOnUnlockInterval.coerceAtLeast(1).toLong()
                    )

                    val idle = System.currentTimeMillis() - offAt
                    if (idle < thresholdMs)
                        continue

                    Log.i("AutoUpdateOnUnlock: screen idle ${idle / 1000}s >= ${thresholdMs / 1000}s threshold, updating subscriptions")

                    service.updateAllSubscriptions()
                }
            }
        }
    }
}
