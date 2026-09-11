package com.omb9.glucosehero.data.local.entity

/**
 * Origin of a [GlucoseSampleEntity] row.
 *
 * Persisted as the enum **name** (TEXT), never the ordinal, so a later source
 * can be inserted without shifting existing rows. Health Connect remains the
 * only writer in Phase 0; Nightscout / xDrip+ / LibreLinkUp / file import are
 * reserved for later ingest paths.
 *
 * Uniqueness is `(source, external_id)`, so the same vendor id may exist once
 * per source (a Nightscout sgv and an xDrip+ broadcast can both use `"x"`).
 *
 * FEATURE: cgm-direct-ingest
 */
enum class GlucoseSampleSource {
    HEALTH_CONNECT,
    NIGHTSCOUT,
    XDRIP_BROADCAST,
    LIBRE_LINK_UP,
    MANUAL_IMPORT,
}
