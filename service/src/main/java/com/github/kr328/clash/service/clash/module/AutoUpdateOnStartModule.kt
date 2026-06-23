package com.github.kr328.clash.service.clash.module

import android.app.Service
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.store.ServiceStore

/**
 * Refresh all subscription profiles once, right after a Clash service starts.
 *
 * Gated by [ServiceStore.autoUpdateOnStart]. Performs a single update when the
 * service comes up and then returns; a normally-completing module does not
 * affect its siblings, so the rest of the runtime keeps running.
 */
class AutoUpdateOnStartModule(service: Service) : Module<Unit>(service) {
    private val store = ServiceStore(service)

    override suspend fun run() {
        if (!store.autoUpdateOnStart)
            return

        Log.i("AutoUpdateOnStart: service started, refreshing subscriptions")

        service.updateAllSubscriptions()
    }
}
