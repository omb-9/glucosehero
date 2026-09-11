package com.omb9.glucosehero.data.cgm.xdrip

import android.content.Intent

/**
 * Sender-agnostic extras for [XdripBroadcastMapper].
 *
 * Unit tests construct this from a plain map (no device, no Bundle). The
 * receiver copies Intent extras into [extras] without logging values.
 *
 * FEATURE: cgm-direct-ingest
 */
data class XdripBroadcastPayload(
    val action: String?,
    val extras: Map<String, Any?> = emptyMap(),
)

/**
 * Copies Intent extras into a [XdripBroadcastPayload] without reading
 * glucose values for logs.
 *
 * FEATURE: cgm-direct-ingest
 */
fun Intent.toXdripBroadcastPayload(): XdripBroadcastPayload {
    val bundle = extras ?: return XdripBroadcastPayload(action = action)
    val extras = HashMap<String, Any?>(bundle.size())
    for (key in bundle.keySet()) {
        @Suppress("DEPRECATION")
        extras[key] = bundle.get(key)
    }
    return XdripBroadcastPayload(action = action, extras = extras)
}
