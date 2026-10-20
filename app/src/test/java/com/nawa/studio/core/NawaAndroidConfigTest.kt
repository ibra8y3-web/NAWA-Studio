package com.nawa.studio.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NawaAndroidConfigTest {
    @Test
    fun serializesAndParsesValidatedAndroidSettings() {
        val config = NawaAndroidConfig(
            label = "تطبيق نواة",
            applicationId = "com.example.nawaapp",
            versionName = "2.4.1-beta",
            versionCode = 24,
            minSdk = 23,
            targetSdk = 35,
            permissions = listOf("android.permission.INTERNET", "android.permission.CAMERA"),
            dependencies = listOf("com.squareup.okhttp3:okhttp:4.12.0")
        )
        val decoded = NawaAndroidConfig.parse(config.serialize())
        assertEquals(config, decoded)
    }

    @Test
    fun defaultsAllowAutomaticApplicationIdAndNoExtraPermissions() {
        val config = NawaAndroidConfig.parse("")
        assertTrue(config.applicationId.isBlank())
        assertTrue(config.permissions.isEmpty())
        assertTrue(config.dependencies.isEmpty())
        assertEquals(26, config.minSdk)
        assertEquals(35, config.targetSdk)
    }

    @Test
    fun rejectsUnsafeApplicationIdAndGradleInjection() {
        assertRejected { NawaAndroidConfig(applicationId = "com.example; println(1)").validate() }
        assertRejected { NawaAndroidConfig(dependencies = listOf("x:y:z\n tasks.register('bad'){}")).validate() }
        assertRejected { NawaAndroidConfig(permissions = listOf("android.permission.INTERNET\"><application")).validate() }
    }

    @Test
    fun rejectsInconsistentSdkRangesAndDuplicateItems() {
        assertRejected { NawaAndroidConfig(minSdk = 30, targetSdk = 29).validate() }
        assertRejected { NawaAndroidConfig(permissions = listOf("android.permission.INTERNET", "android.permission.INTERNET")).validate() }
        assertRejected { NawaAndroidConfig(dependencies = listOf("org.example:lib:1.0", "org.example:lib:1.0")).validate() }
    }

    private fun assertRejected(block: () -> Unit) {
        var rejected = false
        try { block() } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue("Expected configuration to be rejected", rejected)
    }
}
