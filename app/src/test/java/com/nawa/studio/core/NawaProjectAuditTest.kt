package com.nawa.studio.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NawaProjectAuditTest {
    @Test
    fun reportsNawaSyntaxErrorsAndInvalidAndroidSettings() {
        val root = createTempDir(prefix = "nawa-audit-test-")
        try {
            File(root, "src").mkdirs()
            File(root, "src/main.nawa").writeText("app \"bad\" { screen Home { button(\"x\") { missing = 1 } } }")
            File(root, "android.properties").writeText("minSdk=35\ntargetSdk=26\n")
            val findings = NawaProjectAudit.scan(root)
            assertTrue(findings.any { it.path == "src/main.nawa" && it.severity == NawaProjectFinding.Severity.ERROR })
            assertTrue(findings.any { it.path == "android.properties" && it.severity == NawaProjectFinding.Severity.ERROR })
        } finally { root.deleteRecursively() }
    }

    @Test
    fun warnsAboutLikelySecretsAndRejectsMalformedManifestXml() {
        val root = createTempDir(prefix = "nawa-audit-security-test-")
        try {
            File(root, "src/main/java/com/example").mkdirs()
            File(root, "src/main/java/com/example/Config.kt").writeText("const val api_key = \"THIS_IS_A_DEMO_SECRET_123456789\"")
            val manifest = File(root, "android/src/main/AndroidManifest.xml")
            manifest.parentFile.mkdirs()
            manifest.writeText("<manifest><application></manifest>")
            val findings = NawaProjectAudit.scan(root, "app \"ok\" { screen Home { text(\"hello\") } }")
            assertTrue(findings.any { it.severity == NawaProjectFinding.Severity.WARNING && it.message.contains("مفتاح أو سر") })
            assertTrue(findings.any { it.path == "android/src/main/AndroidManifest.xml" && it.severity == NawaProjectFinding.Severity.ERROR })
        } finally { root.deleteRecursively() }
    }

    @Test
    fun reportsValidDslAsInformationNotAnError() {
        val root = createTempDir(prefix = "nawa-audit-valid-test-")
        try {
            val findings = NawaProjectAudit.scan(root, "app \"ok\" { screen Home { text(\"hello\") } }")
            assertTrue(findings.any { it.path == "src/main.nawa" && it.severity == NawaProjectFinding.Severity.INFO })
            assertEquals(0, findings.count { it.severity == NawaProjectFinding.Severity.ERROR })
        } finally { root.deleteRecursively() }
    }
}
