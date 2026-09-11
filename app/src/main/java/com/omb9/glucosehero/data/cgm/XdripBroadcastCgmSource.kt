package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.data.cgm.xdrip.XdripBroadcastHandler
import com.omb9.glucosehero.data.cgm.xdrip.XdripBroadcastRuntime
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Push [CgmSource] for xDrip+ local broadcasts and AAPS-compatible SGV
 * broadcasts. Mapping lives in `data/cgm/xdrip`; this type exists here
 * because [CgmSource] is sealed to this package.
 *
 * [registerPush] is invoked by [CgmIngestService.bindPush]. The enabled
 * flag is collected by [XdripBroadcastBootstrap], not here.
 *
 * FEATURE: cgm-direct-ingest
 */
@Singleton
class XdripBroadcastCgmSource @Inject constructor(
    private val runtime: XdripBroadcastRuntime,
    private val handler: XdripBroadcastHandler,
) : CgmSource {

    override val key: GlucoseSampleSource = GlucoseSampleSource.XDRIP_BROADCAST

    override fun registerPush(sink: CgmPushSink) {
        handler.setSink(sink)
        runtime.register()
    }

    override fun unregisterPush() {
        runtime.unregister()
        handler.setSink(null)
    }
}
