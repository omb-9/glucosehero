package com.omb9.glucosehero.data.cgm.xdrip

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memoizes [XdripBroadcastAllowlist.isAnyAllowlistedAppInstalled] so the
 * receive path does not issue five [android.content.pm.PackageManager]
 * IPCs on every CGM reading.
 *
 * Semantics match the uncached probe: the first call (and each call after
 * [invalidate]) resolves installation the same way. [XdripBroadcastRuntime]
 * invalidates on `PACKAGE_ADDED` / `PACKAGE_REMOVED` / `PACKAGE_CHANGED`.
 *
 * FEATURE: cgm-direct-ingest
 */
@Singleton
class XdripInstalledAppsCache(
    private val probe: () -> Boolean,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        probe = { XdripBroadcastAllowlist.isAnyAllowlistedAppInstalled(context) },
    )

    private val lock = Any()
    private var cached: Boolean? = null

    fun get(): Boolean = synchronized(lock) {
        cached ?: refreshLocked()
    }

    fun refresh(): Boolean = synchronized(lock) { refreshLocked() }

    fun invalidate() {
        synchronized(lock) { cached = null }
    }

    private fun refreshLocked(): Boolean {
        val value = probe()
        cached = value
        return value
    }
}
