package com.omb9.glucosehero.data.cgm.xdrip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XdripBroadcastAllowlistTest {

    @Test
    fun `xdrip and aaps packages are allowed`() {
        assertTrue(XdripBroadcastAllowlist.isAllowedPackage("com.eveningoutpost.dexdrip"))
        assertTrue(XdripBroadcastAllowlist.isAllowedPackage("info.nightscout.androidaps"))
        assertTrue(XdripBroadcastAllowlist.isAllowedPackage("info.nightscout.aapsclient"))
        assertTrue(XdripBroadcastAllowlist.isAllowedPackage("info.nightscout.aapsclient2"))
        assertTrue(XdripBroadcastAllowlist.isAllowedPackage("info.nightscout.aapspumpcontrol"))
        assertTrue(XdripBroadcastAllowlist.isAllowedPackage("info.nightscout.androidaps.debug"))
    }

    @Test
    fun `unknown packages are rejected`() {
        assertFalse(XdripBroadcastAllowlist.isAllowedPackage("com.malware.spoof"))
        assertFalse(XdripBroadcastAllowlist.isAllowedPackage("com.eveningoutpost.dexdrip.wear"))
        assertFalse(XdripBroadcastAllowlist.isAllowedPackage(""))
    }

    @Test
    fun `known sender is required when identity is present`() {
        assertTrue(
            XdripBroadcastAllowlist.acceptsSender(
                identity = XdripSenderIdentity.Known("com.eveningoutpost.dexdrip"),
                allowlistedAppInstalled = false,
            ),
        )
        assertFalse(
            XdripBroadcastAllowlist.acceptsSender(
                identity = XdripSenderIdentity.Known("com.malware.spoof"),
                allowlistedAppInstalled = true,
            ),
        )
    }

    @Test
    fun `unknown sender requires an allowlisted app to be installed`() {
        assertFalse(
            XdripBroadcastAllowlist.acceptsSender(
                identity = XdripSenderIdentity.Unknown,
                allowlistedAppInstalled = false,
            ),
        )
        assertTrue(
            XdripBroadcastAllowlist.acceptsSender(
                identity = XdripSenderIdentity.Unknown,
                allowlistedAppInstalled = true,
            ),
        )
    }

    @Test
    fun `known not allowlisted is rejected even when an allowlisted app is installed`() {
        assertFalse(
            XdripBroadcastAllowlist.acceptsSender(
                identity = XdripSenderIdentity.KnownNotAllowlisted,
                allowlistedAppInstalled = true,
            ),
        )
    }
}
