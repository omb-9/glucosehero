package com.omb9.glucosehero.data.cgm.xdrip

import android.os.Build
import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XdripBroadcastSenderTest {

    private val xdrip = "com.eveningoutpost.dexdrip"
    private val malware = "com.malware.spoof"
    private val other = "com.example.other"

    @Test
    fun `api below 34 is unknown even when packages are available`() {
        val identity = XdripBroadcastSender.identityFromPlatform(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            sentFromPackage = xdrip,
            sentFromUid = 10_001,
            uidPackages = { arrayOf(xdrip) },
        )
        assertEquals(XdripSenderIdentity.Unknown, identity)
    }

    @Test
    fun `api 34 sentFromPackage wins`() {
        val identity = XdripBroadcastSender.identityFromPlatform(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            sentFromPackage = xdrip,
            sentFromUid = 10_001,
            uidPackages = { error("uid packages should not be read") },
        )
        assertEquals(XdripSenderIdentity.Known(xdrip), identity)
    }

    @Test
    fun `multi-package uid with an allowlisted member`() {
        val identity = XdripBroadcastSender.identityFromUidPackages(
            arrayOf(malware, xdrip, other),
        )
        assertEquals(XdripSenderIdentity.Known(xdrip), identity)
    }

    @Test
    fun `multi-package uid with none allowlisted is known not allowlisted`() {
        val identity = XdripBroadcastSender.identityFromUidPackages(
            arrayOf(malware, other),
        )
        assertEquals(XdripSenderIdentity.KnownNotAllowlisted, identity)
        assertTrue(
            !XdripBroadcastAllowlist.acceptsSender(
                identity = identity,
                allowlistedAppInstalled = true,
            ),
        )
    }

    @Test
    fun `single non-allowlisted package is known and rejected`() {
        val identity = XdripBroadcastSender.identityFromUidPackages(arrayOf(malware))
        assertEquals(XdripSenderIdentity.Known(malware), identity)
        assertTrue(
            !XdripBroadcastAllowlist.acceptsSender(
                identity = identity,
                allowlistedAppInstalled = true,
            ),
        )
    }

    @Test
    fun `getPackagesForUid null is unknown`() {
        assertEquals(
            XdripSenderIdentity.Unknown,
            XdripBroadcastSender.identityFromUidPackages(null),
        )
    }

    @Test
    fun `getPackagesForUid empty is unknown`() {
        assertEquals(
            XdripSenderIdentity.Unknown,
            XdripBroadcastSender.identityFromUidPackages(emptyArray()),
        )
    }

    @Test
    fun `system uid is unknown on api 34`() {
        val identity = XdripBroadcastSender.identityFromPlatform(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            sentFromPackage = null,
            sentFromUid = Process.SYSTEM_UID,
            uidPackages = { error("system uid should not be resolved") },
        )
        assertEquals(XdripSenderIdentity.Unknown, identity)
    }
}
