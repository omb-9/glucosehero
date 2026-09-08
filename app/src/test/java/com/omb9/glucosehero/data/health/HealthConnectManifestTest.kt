package com.omb9.glucosehero.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class HealthConnectManifestTest {

    private fun loadManifest(): Element {
        val manifestFile = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml"),
        ).firstOrNull { it.exists() } ?: error("AndroidManifest.xml not found")

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(manifestFile).documentElement
    }

    @Test
    fun `manifest declares all required health read permissions including background read`() {
        val manifest = loadManifest()
        val usesPermissions = manifest.getElementsByTagName("uses-permission")

        val declaredPermissions = mutableSetOf<String>()
        for (i in 0 until usesPermissions.length) {
            val element = usesPermissions.item(i) as Element
            declaredPermissions.add(element.getAttributeNS("http://schemas.android.com/apk/res/android", "name"))
        }

        val requiredHealthPermissions = setOf(
            "android.permission.health.READ_BLOOD_GLUCOSE",
            "android.permission.health.READ_NUTRITION",
            "android.permission.health.READ_EXERCISE",
            "android.permission.health.READ_HEALTH_DATA_HISTORY",
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND",
        )

        for (permission in requiredHealthPermissions) {
            assertTrue("Expected manifest to declare $permission", declaredPermissions.contains(permission))
        }
    }

    @Test
    fun `manifest declares all required health write permissions`() {
        val manifest = loadManifest()
        val usesPermissions = manifest.getElementsByTagName("uses-permission")

        val declaredPermissions = mutableSetOf<String>()
        for (i in 0 until usesPermissions.length) {
            val element = usesPermissions.item(i) as Element
            declaredPermissions.add(element.getAttributeNS("http://schemas.android.com/apk/res/android", "name"))
        }

        val requiredWritePermissions = setOf(
            "android.permission.health.WRITE_BLOOD_GLUCOSE",
            "android.permission.health.WRITE_NUTRITION",
            "android.permission.health.WRITE_EXERCISE",
        )

        for (permission in requiredWritePermissions) {
            assertTrue("Expected manifest to declare $permission", declaredPermissions.contains(permission))
        }
    }

    @Test
    fun `manifest queries Health Connect APK package for pre-Android 14 availability`() {
        val manifest = loadManifest()
        val queriesNodes = manifest.getElementsByTagName("queries")
        assertTrue("Manifest must declare a <queries> tag", queriesNodes.length > 0)

        var foundHealthConnectPackage = false
        for (i in 0 until queriesNodes.length) {
            val queriesElement = queriesNodes.item(i) as Element
            val packages = queriesElement.getElementsByTagName("package")
            for (j in 0 until packages.length) {
                val pkgElement = packages.item(j) as Element
                if (pkgElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name") == "com.google.android.apps.healthdata") {
                    foundHealthConnectPackage = true
                    break
                }
            }
        }

        assertTrue(
            "Expected <queries> to contain <package android:name=\"com.google.android.apps.healthdata\" />",
            foundHealthConnectPackage,
        )
    }

    @Test
    fun `manifest wires Android 14+ ViewPermissionUsageActivity alias with HEALTH_PERMISSIONS category`() {
        val manifest = loadManifest()
        val aliases = manifest.getElementsByTagName("activity-alias")

        var viewPermissionUsageAlias: Element? = null
        for (i in 0 until aliases.length) {
            val alias = aliases.item(i) as Element
            val name = alias.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name == "ViewPermissionUsageActivity" || name == ".ViewPermissionUsageActivity") {
                viewPermissionUsageAlias = alias
                break
            }
        }

        assertNotNull("Expected activity-alias ViewPermissionUsageActivity in manifest", viewPermissionUsageAlias)
        val alias = viewPermissionUsageAlias!!

        assertEquals(".MainActivity", alias.getAttributeNS("http://schemas.android.com/apk/res/android", "targetActivity"))
        assertEquals("true", alias.getAttributeNS("http://schemas.android.com/apk/res/android", "exported"))
        assertEquals(
            "android.permission.START_VIEW_PERMISSION_USAGE",
            alias.getAttributeNS("http://schemas.android.com/apk/res/android", "permission"),
        )

        val intentFilters = alias.getElementsByTagName("intent-filter")
        assertTrue("activity-alias must contain an intent-filter", intentFilters.length > 0)

        var hasViewPermissionUsageAction = false
        var hasHealthPermissionsCategory = false

        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as Element
            val actions = filter.getElementsByTagName("action")
            for (j in 0 until actions.length) {
                val action = actions.item(j) as Element
                if (action.getAttributeNS("http://schemas.android.com/apk/res/android", "name") == "android.intent.action.VIEW_PERMISSION_USAGE") {
                    hasViewPermissionUsageAction = true
                }
            }
            val categories = filter.getElementsByTagName("category")
            for (j in 0 until categories.length) {
                val category = categories.item(j) as Element
                if (category.getAttributeNS("http://schemas.android.com/apk/res/android", "name") == "android.intent.category.HEALTH_PERMISSIONS") {
                    hasHealthPermissionsCategory = true
                }
            }
        }

        assertTrue("Expected intent-filter with ACTION_VIEW_PERMISSION_USAGE", hasViewPermissionUsageAction)
        assertTrue("Expected intent-filter with HEALTH_PERMISSIONS category", hasHealthPermissionsCategory)
    }

    @Test
    fun `manifest wires pre-Android 14 ACTION_SHOW_PERMISSIONS_RATIONALE on MainActivity`() {
        val manifest = loadManifest()
        val activities = manifest.getElementsByTagName("activity")

        var mainActivityElement: Element? = null
        for (i in 0 until activities.length) {
            val act = activities.item(i) as Element
            val name = act.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name == ".MainActivity" || name == "com.omb9.glucosehero.MainActivity") {
                mainActivityElement = act
                break
            }
        }

        assertNotNull("Expected .MainActivity in manifest", mainActivityElement)
        val mainActivity = mainActivityElement!!

        val intentFilters = mainActivity.getElementsByTagName("intent-filter")
        var hasRationaleAction = false

        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as Element
            val actions = filter.getElementsByTagName("action")
            for (j in 0 until actions.length) {
                val action = actions.item(j) as Element
                if (action.getAttributeNS("http://schemas.android.com/apk/res/android", "name") == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE") {
                    hasRationaleAction = true
                }
            }
        }

        assertTrue("Expected MainActivity to have ACTION_SHOW_PERMISSIONS_RATIONALE intent-filter", hasRationaleAction)
    }
}
