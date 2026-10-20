package com.nawa.studio.core

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NawaAndroidProjectGeneratorTest {
    private val source = """
        app "Counter" {
            screen Home {
                state count: Int = 0
                text("Count: {count}")
                button("Increase") { count = count + 1 }
            }
        }
    """.trimIndent()

    @Test
    fun createsAndroidGradleSourceArchive() {
        val temp = createTempDir(prefix = "nawa-generator-test-")
        try {
            val archive = NawaAndroidProjectGenerator.export("Counter", source, temp)
            assertTrue(archive.isFile)
            ZipFile(archive).use { zip ->
                assertTrue(zip.getEntry("settings.gradle.kts") != null)
                assertTrue(zip.getEntry("app/build.gradle.kts") != null)
                assertTrue(zip.getEntry("app/src/main/java/org/nawa/generated/MainActivity.kt") != null)
                assertTrue(zip.getEntry(".github/workflows/android-build.yml") != null)
                val workflow = zip.getInputStream(zip.getEntry(".github/workflows/android-build.yml"))
                    .bufferedReader().use { it.readText() }
                assertTrue(workflow.contains("apksigner\" verify --verbose"))
                assertTrue(workflow.contains("test -s app/build/outputs/apk/debug/app-debug.apk"))
                val kotlin = zip.getInputStream(zip.getEntry("app/src/main/java/org/nawa/generated/MainActivity.kt"))
                    .bufferedReader().use { it.readText() }
                assertTrue(kotlin.contains("nawaState0.toLong() + (1L)"))
                assertTrue(kotlin.contains("Int.MAX_VALUE.toLong()"))
                assertTrue(kotlin.contains("Text(\"Count: ${'$'}{nawaState0}\""))
                val gradle = zip.getInputStream(zip.getEntry("app/build.gradle.kts"))
                    .bufferedReader().use { it.readText() }
                assertTrue(gradle.contains("applicationId = \"com.nawa.generated_counter_"))
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun appliesAndroidPropertiesToManifestAndGradleProject() {
        val temp = createTempDir(prefix = "nawa-config-generator-test-")
        try {
            val projectRoot = File(temp, "project")
            projectRoot.mkdirs()
            File(projectRoot, "android.properties").writeText(NawaAndroidConfig(
                label = "Configured App",
                applicationId = "com.example.configuredapp",
                versionName = "3.2.1",
                versionCode = 321,
                minSdk = 23,
                targetSdk = 34,
                permissions = listOf("android.permission.INTERNET", "android.permission.CAMERA"),
                dependencies = listOf("com.squareup.okhttp3:okhttp:4.12.0")
            ).serialize())
            val archive = NawaAndroidProjectGenerator.export("Configured", source, temp, projectRoot)
            ZipFile(archive).use { zip ->
                val gradle = zip.getInputStream(zip.getEntry("app/build.gradle.kts")).bufferedReader().use { it.readText() }
                assertTrue(gradle.contains("applicationId = \"com.example.configuredapp\""))
                assertTrue(gradle.contains("versionCode = 321"))
                assertTrue(gradle.contains("versionName = \"3.2.1\""))
                assertTrue(gradle.contains("minSdk = 23"))
                assertTrue(gradle.contains("targetSdk = 34"))
                assertTrue(gradle.contains("implementation(\"com.squareup.okhttp3:okhttp:4.12.0\")"))
                val manifest = zip.getInputStream(zip.getEntry("app/src/main/AndroidManifest.xml")).bufferedReader().use { it.readText() }
                assertTrue(manifest.contains("android:label=\"Configured App\""))
                assertTrue(manifest.contains("android.permission.INTERNET"))
                assertTrue(manifest.contains("android.permission.CAMERA"))
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun generatesRealComposeNavigationForMultipleScreens() {
        val temp = createTempDir(prefix = "nawa-multi-screen-generator-test-")
        try {
            val source = """
                app "Navigator" {
                    screen Home {
                        text("Start")
                        button("Open settings") { navigate("Settings") }
                    }
                    screen Settings {
                        text("Settings page")
                        button("Back") { navigate("Home") }
                    }
                }
            """.trimIndent()
            val archive = NawaAndroidProjectGenerator.export("Navigator", source, temp)
            ZipFile(archive).use { zip ->
                val entry = zip.getEntry("app/src/main/java/org/nawa/generated/MainActivity.kt")
                assertTrue(entry != null)
                val kotlin = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                assertTrue(kotlin.contains("private fun GeneratedScreen0(onNavigate: (String) -> Unit)"))
                assertTrue(kotlin.contains("private fun GeneratedScreen1(onNavigate: (String) -> Unit)"))
                assertTrue(kotlin.contains("onNavigate(\"Settings\")"))
                assertTrue(kotlin.contains("onNavigate(\"Home\")"))
                assertTrue(kotlin.contains("currentScreen by remember"))
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun generatesKotlinConditionalAndMultiStatementActions() {
        val source = """
            app "Flow" {
                screen Home {
                    state count: Int = 0
                    state enabled: Bool = true
                    text("{count}")
                    button("Run") {
                        if (count < 2 && enabled) {
                            count = count + 1
                        } else {
                            count = 0
                        }
                        enabled = !enabled
                    }
                }
            }
        """.trimIndent()
        val root = createTempDir(prefix = "nawa-flow-")
        try {
            val archive = NawaAndroidProjectGenerator.export("Flow", source, root)
            java.util.zip.ZipFile(archive).use { zip ->
                val generated = zip.getInputStream(zip.getEntry("app/src/main/java/org/nawa/generated/MainActivity.kt"))
                    .bufferedReader().use { it.readText() }
                assertTrue(generated.contains("nawaState0 < 2"))
                assertTrue(generated.contains("nawaState1"))
                assertTrue(generated.contains("nawaState0 = 0"))
                assertTrue(generated.contains("nawaState1 = !nawaState1"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun generatesAbsoluteCoordinatesAndNativeComposeControls() {
        val source = """
            app "Controls" {
                screen Home {
                    state name: String = "Nawa"
                    state enabled: Bool = true
                    state agreed: Bool = false
                    state volume: Int = 25
                    text("Name").at(12, 18)
                    input("Type your name", name).at(12, 64)
                    switch("Enabled", enabled)
                    checkbox("Agree", agreed)
                    slider("Volume", volume, 0, 100).at(16, 620)
                }
            }
        """.trimIndent()
        val temp = createTempDir(prefix = "nawa-positioned-controls-test-")
        try {
            val archive = NawaAndroidProjectGenerator.export("Controls", source, temp)
            ZipFile(archive).use { zip ->
                val kotlin = zip.getInputStream(zip.getEntry("app/src/main/java/org/nawa/generated/MainActivity.kt"))
                    .bufferedReader().use { it.readText() }
                assertTrue(kotlin.contains("absoluteOffset(x = 12.dp, y = 18.dp)"))
                assertTrue(kotlin.contains("OutlinedTextField(value = nawaState0"))
                assertTrue(kotlin.contains("Switch(checked = nawaState1"))
                assertTrue(kotlin.contains("Checkbox(checked = nawaState2"))
                assertTrue(kotlin.contains("Slider(value = nawaState3.toFloat()"))
                assertTrue(kotlin.contains("valueRange = 0f..100f"))
                assertTrue(kotlin.contains("height(710.dp)"))
                assertTrue(kotlin.contains("horizontalScroll(rememberScrollState())"))
                assertTrue(kotlin.contains("verticalScroll(rememberScrollState())"))
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun generatesBoundedRepeatLoopsFromNawaScript() {
        val source = """
            app "Loop" { screen Home {
                state count: Int = 0
                fun increment() { count = count + 1 }
                text("{count}")
                button("Add") { repeat(4) { increment() } }
            } }
        """.trimIndent()
        val temp = createTempDir(prefix = "nawa-repeat-test-")
        try {
            val archive = NawaAndroidProjectGenerator.export("Loop", source, temp)
            java.util.zip.ZipFile(archive).use { zip ->
                val generated = zip.getInputStream(zip.getEntry("app/src/main/java/org/nawa/generated/MainActivity.kt"))
                    .bufferedReader().use { it.readText() }
                assertTrue(generated.contains("repeat(4)"))
                assertTrue(generated.contains("nawaState0 = (nawaState0.toLong() + (1L)).toInt()"))
            }
        } finally { temp.deleteRecursively() }
    }

    @Test
    fun includesNativeAndroidSourcesAndAssetsInGeneratedProject() {
        val temp = createTempDir(prefix = "nawa-native-generator-test-")
        try {
            val sourceRoot = File(temp, "project")
            val activity = File(sourceRoot, "src/main/kotlin/com/example/MainActivity.kt")
            activity.parentFile.mkdirs()
            activity.writeText("""
                package com.example
                import android.os.Bundle
                import androidx.activity.ComponentActivity
                class MainActivity : ComponentActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
                }
            """.trimIndent())
            val asset = File(sourceRoot, "assets/welcome.txt")
            asset.parentFile.mkdirs()
            asset.writeText("NAWA native asset")
            val nativeLib = File(sourceRoot, "jniLibs/arm64-v8a/libexample.so")
            nativeLib.parentFile.mkdirs()
            nativeLib.writeBytes(byteArrayOf(1, 2, 3))
            val invalidDsl = "// Kotlin project; NawaScript is not this project's entry point"
            val archive = NawaAndroidProjectGenerator.export("Native demo", invalidDsl, temp, sourceRoot)
            ZipFile(archive).use { zip ->
                assertTrue(zip.getEntry("app/src/main/java/com/example/MainActivity.kt") != null)
                assertTrue(zip.getEntry("app/src/main/assets/welcome.txt") != null)
                assertTrue(zip.getEntry("app/src/main/jniLibs/arm64-v8a/libexample.so") != null)
                assertTrue(zip.getEntry("app/src/main/java/org/nawa/generated/MainActivity.kt") == null)
                val manifest = zip.getInputStream(zip.getEntry("app/src/main/AndroidManifest.xml"))
                    .bufferedReader().use { it.readText() }
                assertTrue(manifest.contains("com.example.MainActivity"))
                val gradle = zip.getInputStream(zip.getEntry("app/build.gradle.kts"))
                    .bufferedReader().use { it.readText() }
                assertTrue(gradle.contains("fileTree"))
                assertTrue(gradle.contains("namespace = \"com.example\""))
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun doesNotTreatAClassNamedMainActivityAsAnAndroidActivityUnlessItExtendsOne() {
        val temp = createTempDir(prefix = "nawa-false-activity-test-")
        try {
            val sourceRoot = File(temp, "project")
            val activity = File(sourceRoot, "src/main/java/com/example/MainActivity.java")
            activity.parentFile.mkdirs()
            activity.writeText("package com.example; public class MainActivity { public void hello() {} }")
            try {
                NawaAndroidProjectGenerator.export("Invalid native", "// no DSL app", temp, sourceRoot)
                fail("Expected the non-Activity class to be rejected as a launch target")
            } catch (_: IllegalArgumentException) {
                // expected: a class name alone does not make a valid Android launcher.
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun refusesSourceWithParserErrors() {
        val temp = createTempDir(prefix = "nawa-invalid-test-")
        try {
            try {
                NawaAndroidProjectGenerator.export("Invalid", "app \"Bad\" { screen Home { button(\"x\") { unknown = 2 } } }", temp)
                fail("Expected invalid source to be rejected")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        } finally {
            temp.deleteRecursively()
        }
    }
    @Test
    fun generatesSafeWhileLoopAndOverflowCheckedArithmeticForAndroid() {
        val temp = createTempDir(prefix = "nawa-loop-generator-test-")
        try {
            val source = """
                app "Loops" { screen Home {
                    state count: Int = 1
                    text("{count}")
                    button("run") {
                        count = count * 3
                        while (count < 10) { count = count + 1 }
                    }
                } }
            """.trimIndent()
            val archive = NawaAndroidProjectGenerator.export("Loop project", source, temp)
            ZipFile(archive).use { zip ->
                val path = zip.getEntry("app/src/main/java/org/nawa/generated/MainActivity.kt")
                assertTrue(path != null)
                val generated = zip.getInputStream(path).bufferedReader().use { it.readText() }
                assertTrue(generated.contains("nawaArithmeticResult"))
                assertTrue(generated.contains("nawaWhileGuard < ${NawaParser.MAX_WHILE_STEPS}"))
                assertTrue(generated.contains("while ("))
            }
        } finally { temp.deleteRecursively() }
    }

}
