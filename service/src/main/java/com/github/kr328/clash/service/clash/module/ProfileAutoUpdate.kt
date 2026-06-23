package com.github.kr328.clash.service.clash.module

import android.app.Service
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.ProfileReceiver
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.model.Profile

/**
 * Trigger an immediate refresh of every subscription profile (every imported
 * profile that is not a local [Profile.Type.File]).
 *
 * Shared by [AutoUpdateOnStartModule] and [AutoUpdateOnUnlockModule]. Any
 * failure is logged and swallowed on purpose: modules are launched as plain
 * (non-supervised) children of the ClashRuntime scope, so an exception escaping
 * a module's run() would cancel that scope and tear down the running service.
 */
internal suspend fun Service.updateAllSubscriptions() {
    try {
        ImportedDao().queryAllUUIDs()
            .mapNotNull { ImportedDao().queryByUUID(it) }
            .filter { it.type != Profile.Type.File }
            .forEach { ProfileReceiver.schedule(this, it) }
    } catch (e: Exception) {
        Log.w("AutoUpdate: failed to refresh subscriptions", e)
    }
}
