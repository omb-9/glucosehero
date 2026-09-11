package com.omb9.glucosehero.data.cgm.nightscout

import com.omb9.glucosehero.data.cgm.CgmPublicError
import java.io.IOException

/**
 * Typed Nightscout failures. Messages never include credentials, tokens,
 * hosts with query strings, or glucose values.
 *
 * FEATURE: cgm-direct-ingest
 */
sealed class NightscoutException(
    override val publicErrorCode: String,
    message: String,
    val retryable: Boolean = false,
) : IOException(message), CgmPublicError

class NightscoutCleartextException : NightscoutException(
    publicErrorCode = NightscoutErrorCodes.CLEARTEXT,
    message = "Nightscout must be reached over HTTPS unless it is on your local network.",
)

class NightscoutHostUnacknowledgedException : NightscoutException(
    publicErrorCode = NightscoutErrorCodes.HOST_UNACKNOWLEDGED,
    message = "This Nightscout host has not been acknowledged.",
)

class NightscoutAuthException : NightscoutException(
    publicErrorCode = NightscoutErrorCodes.AUTH,
    message = "Nightscout rejected the access token or API secret.",
)

class NightscoutV1UnsupportedException : NightscoutException(
    publicErrorCode = NightscoutErrorCodes.V1_ONLY,
    message = "This Nightscout instance does not serve API v1 entries.",
)

class NightscoutNetworkException : NightscoutException(
    publicErrorCode = NightscoutErrorCodes.NETWORK,
    message = "Could not reach Nightscout.",
    retryable = true,
)

class NightscoutHttpException : NightscoutException(
    publicErrorCode = NightscoutErrorCodes.HTTP,
    message = "Nightscout returned an unexpected HTTP status.",
    retryable = true,
)

class NightscoutInvalidUrlException : NightscoutException(
    publicErrorCode = NightscoutErrorCodes.INVALID_URL,
    message = "The Nightscout URL is not valid.",
)

class NightscoutMissingCredentialException : NightscoutException(
    publicErrorCode = NightscoutErrorCodes.MISSING_CREDENTIAL,
    message = "No Nightscout access token or API secret is stored.",
)

class NightscoutMissingUrlException : NightscoutException(
    publicErrorCode = NightscoutErrorCodes.MISSING_URL,
    message = "No Nightscout URL is configured.",
)

class NightscoutParseException : NightscoutException(
    publicErrorCode = NightscoutErrorCodes.PARSE,
    message = "Nightscout returned a response that could not be read.",
)
