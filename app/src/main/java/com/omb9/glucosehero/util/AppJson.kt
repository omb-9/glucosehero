package com.omb9.glucosehero.util

import kotlinx.serialization.json.Json

/** Shared lenient Json instance for Room converters and API payloads. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "kind"
}
