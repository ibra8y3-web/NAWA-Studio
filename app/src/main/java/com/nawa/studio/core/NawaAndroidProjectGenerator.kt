package com.nawa.studio.core

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Generates a real Gradle Android source project for the explicitly supported NawaScript subset. */
object NawaAndroidProjectGenerator {
    fun export(projectName: String, source: String, exportsRoot: File): File =
        export(projectName, source, exportsRoot, null)

    /**
     * Generates a real Android/Gradle project from the supported NawaScript subset and any
     * conventional native Android sources/resources found in the project's private directory.
     * The native source layout is deliberately explicit; arbitrary files are never silently
     * treated as compilable Kotlin.
     */
    fun export(projectName: String, source: String, exportsRoot: File, projectRoot: File?): File {
        val program = NawaParser.parse(source)
        val nativeFiles = projectRoot?.let(::collectNativeFiles).orEmpty()
        val customManifest = nativeFiles.any { it.outputPath == "app/src/main/AndroidManifest.xml" }
        val androidConfigFile = projectRoot?.let { File(it, "android.properties") }
        val androidConfig = NawaAndroidConfig.parse(
            if (androidConfigFile?.isFile == true) androidConfigFile.readText(StandardCharsets.UTF_8) else ""
        ).validate()
        val nativeActivity = nativeFiles.firstNotNullOfOrNull { entry ->
            if (entry.outputPath.endsWith(".kt") || entry.outputPath.endsWith(".java")) {
                detectMainActivity(entry.source.readText(StandardCharsets.UTF_8), entry.outputPath.endsWith(".java"))
            } else null
        }
        val dslValid = program.hasAppDeclaration && program.hasScreenDeclaration &&
            !program.hasErrors && (program.screens.any { it.elements.isNotEmpty() } || program.elements.isNotEmpty())
        require(dslValid || nativeActivity != null) {
            if (program.hasErrors) "لا يمكن توليد المشروع: أصلح أخطاء NawaScript أو أضف Activity أصلية صالحة ضمن android/src/main/java أو src/main/java."
            else "لا يوجد تطبيق قابل للبناء. أضف واجهة NawaScript صحيحة أو Activity أصلية ضمن android/src/main/java أو src/main/java."
        }

        if (!exportsRoot.exists() && !exportsRoot.mkdirs()) error("تعذر إنشاء مجلد التصدير.")
        val workDir = File(exportsRoot, "android-project-${UUID.randomUUID()}")
        check(workDir.mkdirs()) { "تعذر إنشاء مشروع Android المؤقت." }
        try {
            val label = xmlEscape(androidConfig.label.ifBlank { if (dslValid) program.appName else projectName }.take(80))
            val applicationId = androidConfig.applicationId.ifBlank { safeApplicationId(projectName, projectRoot?.name ?: projectName) }
            write(workDir, "settings.gradle.kts", settingsGradle())
            write(workDir, "build.gradle.kts", rootBuildGradle())
            write(workDir, "gradle.properties", gradleProperties())
            val namespace = nativeActivity?.substringBeforeLast('.') ?: "org.nawa.generated"
            write(
                workDir,
                "app/build.gradle.kts",
                appBuildGradle(applicationId, namespace, androidConfig)
            )
            if (!customManifest) {
                write(workDir, "app/src/main/AndroidManifest.xml", manifest(label, nativeActivity ?: "org.nawa.generated.MainActivity", androidConfig.permissions))
            }
            if (nativeActivity == null) {
                write(workDir, "app/src/main/java/org/nawa/generated/MainActivity.kt", generateMainActivity(program))
            }
            write(workDir, "src/main.nawa", source)
            nativeFiles.forEach { entry ->
                val destination = File(workDir, entry.outputPath)
                require(destination.canonicalPath.startsWith(workDir.canonicalPath + File.separator)) {
                    "مسار ملف غير آمن أثناء تجهيز Android: ${entry.outputPath}"
                }
                destination.parentFile?.let { if (!it.exists() && !it.mkdirs()) error("تعذر إنشاء ${it.path}") }
                entry.source.inputStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            }
            if (customManifest && androidConfig.permissions.isNotEmpty()) {
                val manifestFile = File(workDir, "app/src/main/AndroidManifest.xml")
                require(manifestFile.isFile) { "ملف AndroidManifest.xml المخصص غير موجود بعد النسخ." }
                val originalManifest = manifestFile.readText(StandardCharsets.UTF_8)
                write(workDir, "app/src/main/AndroidManifest.xml", mergePermissions(originalManifest, androidConfig.permissions))
            }
            write(workDir, "README_AR.md", readme(label, nativeActivity, nativeFiles.map { it.outputPath }, customManifest, androidConfig))
            write(workDir, ".github/workflows/android-build.yml", workflow())
            write(workDir, ".gitignore", generatedGitIgnore())

            val target = File(exportsRoot, "${safeFileName(projectName)}-${UUID.randomUUID().toString().take(8)}-android-source.zip")
            ZipOutputStream(FileOutputStream(target)).use { zip ->
                workDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val entryName = file.relativeTo(workDir).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            return target
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun generateMainActivity(program: NawaProgram): String {
        val screens = program.screens.ifEmpty {
            listOf(NawaScreen(program.screenName, program.states, program.elements, 0, 0, program.diagnostics))
        }
        val out = StringBuilder()
        out.appendLine("package org.nawa.generated")
        out.appendLine()
        out.appendLine("import android.os.Bundle")
        out.appendLine("import androidx.activity.ComponentActivity")
        out.appendLine("import androidx.activity.compose.setContent")
        out.appendLine("import androidx.compose.foundation.layout.Arrangement")
        out.appendLine("import androidx.compose.foundation.layout.Box")
        out.appendLine("import androidx.compose.foundation.layout.absoluteOffset")
        out.appendLine("import androidx.compose.foundation.layout.Column")
        out.appendLine("import androidx.compose.foundation.layout.Row")
        out.appendLine("import androidx.compose.foundation.layout.fillMaxSize")
        out.appendLine("import androidx.compose.foundation.layout.fillMaxWidth")
        out.appendLine("import androidx.compose.foundation.layout.width")
        out.appendLine("import androidx.compose.foundation.horizontalScroll")
        out.appendLine("import androidx.compose.foundation.rememberScrollState")
        out.appendLine("import androidx.compose.foundation.verticalScroll")
        out.appendLine("import androidx.compose.foundation.layout.height")
        out.appendLine("import androidx.compose.foundation.layout.padding")
        out.appendLine("import androidx.compose.foundation.layout.widthIn")
        out.appendLine("import androidx.compose.material3.Button")
        out.appendLine("import androidx.compose.material3.Checkbox")
        out.appendLine("import androidx.compose.material3.MaterialTheme")
        out.appendLine("import androidx.compose.material3.OutlinedTextField")
        out.appendLine("import androidx.compose.material3.Slider")
        out.appendLine("import androidx.compose.material3.Surface")
        out.appendLine("import androidx.compose.material3.Switch")
        out.appendLine("import androidx.compose.material3.Text")
        out.appendLine("import androidx.compose.runtime.Composable")
        out.appendLine("import androidx.compose.runtime.getValue")
        out.appendLine("import androidx.compose.runtime.mutableStateOf")
        out.appendLine("import androidx.compose.runtime.mutableIntStateOf")
        out.appendLine("import androidx.compose.runtime.remember")
        out.appendLine("import androidx.compose.runtime.setValue")
        out.appendLine("import androidx.compose.runtime.saveable.rememberSaveableStateHolder")
        out.appendLine("import androidx.compose.ui.Modifier")
        out.appendLine("import androidx.compose.ui.Alignment")
        out.appendLine("import androidx.compose.ui.text.font.FontWeight")
        out.appendLine("import androidx.compose.ui.unit.dp")
        out.appendLine("import kotlin.math.roundToInt")
        out.appendLine()
        out.appendLine("class MainActivity : ComponentActivity() {")
        out.appendLine("    override fun onCreate(savedInstanceState: Bundle?) {")
        out.appendLine("        super.onCreate(savedInstanceState)")
        out.appendLine("        setContent { MaterialTheme { Surface(modifier = Modifier.fillMaxSize()) { GeneratedApp() } } }")
        out.appendLine("    }")
        out.appendLine("}")
        out.appendLine()
        out.appendLine("@Composable")
        out.appendLine("private fun GeneratedApp() {")
        out.appendLine("    var currentScreen by remember { mutableStateOf(${kotlinString(screens.first().name)}) }")
        out.appendLine("    val stateHolder = rememberSaveableStateHolder()")
        out.appendLine("    when (currentScreen) {")
        screens.forEachIndexed { index, screen ->
            out.appendLine("        ${kotlinString(screen.name)} -> stateHolder.SaveableStateProvider(${kotlinString(screen.name)}) { GeneratedScreen$index(onNavigate = { currentScreen = it }) }")
        }
        out.appendLine("        else -> stateHolder.SaveableStateProvider(${kotlinString(screens.first().name)}) { GeneratedScreen0(onNavigate = { currentScreen = it }) }")
        out.appendLine("    }")
        out.appendLine("}")
        screens.forEachIndexed { index, screen ->
            out.appendLine()
            out.append(generateScreenComposable(program.appName, screen, index, screens.size > 1))
        }
        return out.toString()
    }

    private fun generateScreenComposable(
        appName: String,
        screen: NawaScreen,
        screenIndex: Int,
        multipleScreens: Boolean
    ): String {
        val stateSymbols = screen.states.mapIndexed { index, state ->
            state.name to if (multipleScreens) "nawaScreen${screenIndex}State$index" else "nawaState$index"
        }.toMap()
        val out = StringBuilder()
        out.appendLine("@Composable")
        out.appendLine("private fun GeneratedScreen$screenIndex(onNavigate: (String) -> Unit) {")
        screen.states.forEach { state ->
            val symbol = stateSymbols.getValue(state.name)
            when (val value = state.initialValue) {
                is NawaValue.NumberValue -> out.appendLine("    var $symbol by remember { mutableIntStateOf(${value.value}) }")
                is NawaValue.StringValue -> out.appendLine("    var $symbol by remember { mutableStateOf(${kotlinString(value.value)}) }")
                is NawaValue.BooleanValue -> out.appendLine("    var $symbol by remember { mutableStateOf(${value.value}) }")
            }
        }
        out.appendLine("    Column(")
        out.appendLine("        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),")
        out.appendLine("        verticalArrangement = Arrangement.spacedBy(14.dp)")
        out.appendLine("    ) {")
        out.appendLine("        Text(${kotlinString(appName)}, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)")
        out.appendLine("        Text(${kotlinString(screen.name)}, style = MaterialTheme.typography.titleMedium)")
        val hasAbsoluteLayout = screen.elements.any { element ->
            element.x != null && element.y != null
        }
        if (hasAbsoluteLayout) {
            // Coordinates are relative to this screen's content area, in dp, matching the designer canvas.
            val canvasHeight = (screen.elements.mapIndexed { index, element -> (element.y ?: (16 + index * 64)) + 90 }.maxOrNull() ?: 520).coerceIn(520, 4200)
            val canvasWidth = (screen.elements.map { (it.x ?: 16) + 300 }.maxOrNull() ?: 520).coerceIn(340, 4200)
            out.appendLine("        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {")
            out.appendLine("            Box(modifier = Modifier.width(${canvasWidth}.dp).height(${canvasHeight}.dp)) {")
            screen.elements.forEachIndexed { index, element ->
                val x = element.x ?: 16
                val y = element.y ?: (16 + index * 64)
                appendGeneratedElement(out, element, stateSymbols, "Modifier.absoluteOffset(x = ${x}.dp, y = ${y}.dp).widthIn(max = 280.dp)", "            ")
            }
            out.appendLine("            }")
            out.appendLine("        }")
        } else {
            screen.elements.forEach { element ->
                appendGeneratedElement(out, element, stateSymbols, "Modifier.fillMaxWidth()", "        ")
            }
        }
        out.appendLine("    }")
        out.appendLine("}")
        return out.toString()
    }

    private fun appendGeneratedElement(
        out: StringBuilder,
        element: NawaElement,
        symbols: Map<String, String>,
        modifier: String,
        indent: String
    ) {
        when (element) {
            is NawaElement.Text -> out.appendLine("${indent}Text(${kotlinTemplate(element.value, symbols)}, modifier = $modifier)")
            is NawaElement.Button -> {
                val body = element.action?.let { generateAction(it, symbols) }.orEmpty()
                out.appendLine("${indent}Button(onClick = onClick@{ $body }, enabled = ${element.action != null}, modifier = $modifier) {")
                out.appendLine("${indent}    Text(${kotlinString(element.label)})")
                out.appendLine("${indent}}")
            }
            is NawaElement.Control -> {
                val state = symbols[element.stateName] ?: "nawaMissingState"
                when (element.kind) {
                    NawaControlKind.INPUT -> out.appendLine("${indent}OutlinedTextField(value = $state, onValueChange = { $state = it }, label = { Text(${kotlinString(element.label)}) }, modifier = $modifier, singleLine = true)")
                    NawaControlKind.SWITCH -> out.appendLine("${indent}Row(modifier = $modifier, verticalAlignment = Alignment.CenterVertically) { Text(${kotlinString(element.label)}, modifier = Modifier.weight(1f)); Switch(checked = $state, onCheckedChange = { $state = it }) }")
                    NawaControlKind.CHECKBOX -> out.appendLine("${indent}Row(modifier = $modifier, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = $state, onCheckedChange = { $state = it }); Text(${kotlinString(element.label)}) }")
                    NawaControlKind.SLIDER -> {
                        val minimum = element.minimum ?: 0
                        val maximum = element.maximum ?: 100
                        out.appendLine("${indent}Column(modifier = $modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {")
                        out.appendLine("${indent}    Text(${kotlinString(element.label)})")
                        out.appendLine("${indent}    Slider(value = $state.toFloat().coerceIn(${minimum}f, ${maximum}f), onValueChange = { $state = it.roundToInt().coerceIn($minimum, $maximum) }, valueRange = ${minimum}f..${maximum}f)")
                        out.appendLine("${indent}    Text($state.toString())")
                        out.appendLine("${indent}}")
                    }
                }
            }
        }
    }

    private fun generateAction(action: NawaAction, symbols: Map<String, String>): String = when (action) {
        is NawaAction.Navigate -> "onNavigate(${kotlinString(action.targetScreen)}); return@onClick"
        is NawaAction.AdjustNumber -> symbols[action.stateName]?.let { variable ->
            val delta = "(${action.delta}L)"
            val sum = "$variable.toLong() + $delta"
            "if ($sum in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { $variable = ($sum).toInt() }"
        }.orEmpty()
        is NawaAction.SetNumber -> symbols[action.stateName]?.let { "$it = ${action.value}" }.orEmpty()
        is NawaAction.SetString -> symbols[action.stateName]?.let { "$it = ${kotlinString(action.value)}" }.orEmpty()
        is NawaAction.SetBoolean -> symbols[action.stateName]?.let { "$it = ${action.value}" }.orEmpty()
        is NawaAction.ToggleBoolean -> symbols[action.stateName]?.let { "$it = !$it" }.orEmpty()
        is NawaAction.ArithmeticNumber -> symbols[action.stateName]?.let { variable ->
            val operand = action.operand.toLong()
            val result = when (action.operator) {
                "*" -> "$variable.toLong() * ($operand)"
                "/" -> "$variable.toLong() / ($operand)"
                "%" -> "$variable.toLong() % ($operand)"
                else -> "0L"
            }
            "run { val nawaArithmeticResult = $result; if (nawaArithmeticResult in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { $variable = nawaArithmeticResult.toInt() } }"
        }.orEmpty()
        is NawaAction.Sequence -> action.actions.joinToString("; ") { generateAction(it, symbols) }
        is NawaAction.Conditional -> buildString {
            append("if (").append(generateCondition(action.condition, symbols)).append(") { ")
            append(generateAction(action.thenAction, symbols)).append(" }")
            action.elseAction?.let { append(" else { ").append(generateAction(it, symbols)).append(" }") }
        }
        is NawaAction.Repeat -> "repeat(${action.times}) { ${generateAction(action.action, symbols)} }"
        is NawaAction.While -> "run { var nawaWhileGuard = 0; while (${generateCondition(action.condition, symbols)} && nawaWhileGuard < ${NawaParser.MAX_WHILE_STEPS}) { ${generateAction(action.action, symbols)}; nawaWhileGuard++ } }"
    }

    private fun generateCondition(condition: NawaCondition, symbols: Map<String, String>): String = when (condition) {
        is NawaCondition.BooleanState -> symbols[condition.stateName] ?: "false"
        is NawaCondition.Not -> "!(${generateCondition(condition.condition, symbols)})"
        is NawaCondition.Compare -> {
            val symbol = symbols[condition.stateName] ?: "false"
            val right = when (val value = condition.value) {
                is NawaValue.NumberValue -> value.value.toString()
                is NawaValue.StringValue -> kotlinString(value.value)
                is NawaValue.BooleanValue -> value.value.toString()
            }
            "($symbol ${condition.operator} $right)"
        }
        is NawaCondition.Logical -> "(${generateCondition(condition.left, symbols)}) ${condition.operator} (${generateCondition(condition.right, symbols)})"
    }

    private fun kotlinTemplate(value: String, symbols: Map<String, String>): String {
        val pattern = Regex("""\{([A-Za-z_][A-Za-z0-9_]*)}""")
        val out = StringBuilder("\"")
        var cursor = 0
        for (match in pattern.findAll(value)) {
            out.append(escapeKotlin(value.substring(cursor, match.range.first)))
            val symbol = symbols[match.groupValues[1]]
            if (symbol == null) {
                out.append(escapeKotlin(match.value))
            } else {
                out.append("\${").append(symbol).append('}')
            }
            cursor = match.range.last + 1
        }
        out.append(escapeKotlin(value.substring(cursor)))
        out.append('"')
        return out.toString()
    }

    private fun kotlinString(value: String): String = "\"${escapeKotlin(value)}\""

    private fun escapeKotlin(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("$", "\\$")

    private fun settingsGradle() = """
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "NawaGeneratedApp"
include(":app")
""".trimIndent() + "\n"

    private fun rootBuildGradle() = """
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
""".trimIndent() + "\n"

    private fun gradleProperties() = """
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
org.gradle.parallel=true
""".trimIndent() + "\n"

    private fun appBuildGradle(applicationId: String, namespace: String, config: NawaAndroidConfig): String {
        val extraDependencies = config.dependencies.joinToString("\n") { "    implementation(\"$it\")" }
        return """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "$namespace"
    compileSdk = 35
    defaultConfig {
        applicationId = "$applicationId"
        minSdk = ${config.minSdk}
        targetSdk = ${config.targetSdk}
        versionCode = ${config.versionCode}
        versionName = "${config.versionName}"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
$extraDependencies
}
""".trimIndent() + "\n"
    }

    private fun manifest(appLabel: String, activityName: String, permissions: List<String>): String {
        val permissionEntries = permissions.distinct().joinToString("\n") { "    <uses-permission android:name=\"${xmlEscape(it)}\" />" }
        return """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
$permissionEntries
    <application android:allowBackup="false" android:label="$appLabel" android:supportsRtl="true" android:theme="@style/Theme.AppCompat.Light.NoActionBar">
        <activity android:name="$activityName" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""".trimIndent() + "\n"
    }

    private fun mergePermissions(manifestText: String, permissions: List<String>): String {
        require(Regex("<manifest\\b").containsMatchIn(manifestText) && Regex("<application\\b").containsMatchIn(manifestText)) {
            "ملف AndroidManifest.xml المخصص يجب أن يحتوي عنصر manifest وعنصر application صالحين."
        }
        var result = manifestText
        val permissionPattern = Regex("<uses-permission\\b[^>]*android:name\\s*=\\s*['\"]([^'\"]+)['\"][^>]*/?>")
        val existing = permissionPattern.findAll(manifestText).map { it.groupValues[1] }.toSet()
        val missing = permissions.filterNot { it in existing }
        if (missing.isEmpty()) return result
        val application = Regex("<application\\b").find(result)
            ?: throw IllegalArgumentException("لا يمكن دمج الصلاحيات في ملف manifest المخصص.")
        val added = missing.joinToString("\n") { "    <uses-permission android:name=\"${xmlEscape(it)}\" />\n" }
        result = result.substring(0, application.range.first) + added + result.substring(application.range.first)
        return result
    }

    private fun workflow() = """
name: Build generated Android app
on:
  workflow_dispatch:
  push:
    branches: [ main, master ]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: android-actions/setup-android@v3
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.13'
      - name: Install Android SDK packages
        run: sdkmanager "platforms;android-35" "build-tools;35.0.0"
      - name: Build APK
        run: gradle --no-daemon assembleDebug
      - name: Verify APK exists and is signed
        run: |
          test -s app/build/outputs/apk/debug/app-debug.apk
          "${'$'}ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
          sha256sum app/build/outputs/apk/debug/app-debug.apk
      - uses: actions/upload-artifact@v4
        with:
          name: generated-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
""".trimIndent() + "\n"

    private fun readme(name: String, nativeActivity: String?, copiedFiles: List<String>, customManifest: Boolean, config: NawaAndroidConfig) = """
# $name — Android project generated by NAWA Studio

This archive contains a real Android/Gradle source project, not a prebuilt APK.

## Build

- Open the folder in Android Studio and sync Gradle, or install JDK 17, Gradle 8.13 and Android SDK 35.
- Run `gradle --no-daemon assembleDebug` from the project root.
- Output: `app/build/outputs/apk/debug/app-debug.apk`.
- `.github/workflows/android-build.yml` builds and uploads the debug APK when run from a GitHub repository.

## Included project source

- NawaScript source: `src/main.nawa`.
- Native Android Activity detected from the project source: `${nativeActivity ?: "none (generated from NawaScript)"}`.
- Custom Android manifest supplied by the project: `$customManifest`.
- Native source/resource files copied into the generated Gradle project: ${copiedFiles.size}.

## Supported source locations

- `android/src/main/**` is copied into `app/src/main/**`.
- `src/main/java/**` and `src/main/kotlin/**` are compiled as Android source.
- `.kt` and `.java` files placed elsewhere under `src/` are included as source files.
- `res/**` maps to Android resources, `assets/**` maps to Android assets, and `libs/*.jar` maps to local libraries.
- If no project Activity is found, NAWA generates Compose screens from the supported NawaScript subset, including `navigate("ScreenName")` actions between screens.
- Build settings come from `android.properties`: label, application ID (optional), `versionName`, `versionCode`, `minSdk`, `targetSdk`, permissions and Maven dependencies.
- ${config.dependencies.size} additional Maven dependencies and ${config.permissions.size} manifest permissions are configured for this output.

Dependencies must exist in the configured repositories, and all native source files must compile. Including a Kotlin/Java file does not guarantee it is valid source.
""".trimIndent() + "\n"

    private data class NativeFile(val source: File, val outputPath: String)

    private fun collectNativeFiles(projectRoot: File): List<NativeFile> {
        if (!projectRoot.isDirectory) return emptyList()
        val root = projectRoot.canonicalFile
        val mappings = mutableListOf<NativeFile>()
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val canonical = file.canonicalFile
            if (!canonical.path.startsWith(root.path + File.separator)) return@forEach
            val rel = file.relativeTo(root).invariantSeparatorsPath
            val output = when {
                rel.startsWith("android/src/main/") -> "app/src/main/" + rel.removePrefix("android/src/main/")
                rel.startsWith("src/main/java/") -> "app/src/main/java/" + rel.removePrefix("src/main/java/")
                rel.startsWith("src/main/kotlin/") -> "app/src/main/java/" + rel.removePrefix("src/main/kotlin/")
                rel.startsWith("src/main/res/") -> "app/src/main/res/" + rel.removePrefix("src/main/res/")
                rel.startsWith("src/main/assets/") -> "app/src/main/assets/" + rel.removePrefix("src/main/assets/")
                rel.startsWith("src/main/aidl/") -> "app/src/main/aidl/" + rel.removePrefix("src/main/aidl/")
                rel.startsWith("src/main/jniLibs/") -> "app/src/main/jniLibs/" + rel.removePrefix("src/main/jniLibs/")
                rel.startsWith("jniLibs/") -> "app/src/main/jniLibs/" + rel.removePrefix("jniLibs/")
                rel == "proguard-rules.pro" -> "app/proguard-rules.pro"
                rel.startsWith("src/") && (rel.endsWith(".kt") || rel.endsWith(".java")) ->
                    "app/src/main/java/" + rel.removePrefix("src/")
                rel.startsWith("res/") -> "app/src/main/res/" + rel.removePrefix("res/")
                rel.startsWith("assets/") -> "app/src/main/assets/" + rel.removePrefix("assets/")
                rel.startsWith("libs/") && rel.endsWith(".jar") -> "app/libs/" + rel.removePrefix("libs/")
                else -> null
            }
            if (output != null && output != "app/src/main.nawa") {
                require(file.length() <= MAX_NATIVE_FILE_BYTES) { "الملف $rel أكبر من الحد المسموح لتصدير Android." }
                if (output.endsWith("AndroidManifest.xml") || output.endsWith(".kt") || output.endsWith(".java") ||
                    output.endsWith(".xml") || output.startsWith("app/src/main/res/") ||
                    output.startsWith("app/src/main/assets/") || output.startsWith("app/src/main/aidl/") ||
                    output.startsWith("app/src/main/jniLibs/") || output.endsWith(".jar") || output.endsWith(".so") ||
                    output == "app/proguard-rules.pro") {
                    mappings += NativeFile(file, output)
                }
            }
        }
        require(mappings.size <= MAX_NATIVE_FILE_COUNT) { "عدد ملفات Android في المشروع أكبر من الحد المسموح للتصدير." }
        require(mappings.sumOf { it.source.length() } <= MAX_NATIVE_TOTAL_BYTES) {
            "إجمالي ملفات المصدر والموارد المضافة إلى مشروع Android يتجاوز 150 ميجابايت."
        }
        val duplicate = mappings.groupBy { it.outputPath }.filterValues { it.size > 1 }.keys
        require(duplicate.isEmpty()) { "توجد ملفات متعارضة في مسار Android: ${duplicate.joinToString()}" }
        return mappings.sortedBy { it.outputPath }
    }

    private fun detectMainActivity(content: String, java: Boolean): String? {
        // Never infer a launcher Activity from a class name alone: that can generate an APK
        // whose manifest points to a class that cannot be launched by Android.
        val classPattern = if (java) {
            Regex("(?:public\\s+)?class\\s+MainActivity\\s+extends\\s+[A-Za-z0-9_.]*Activity\\b")
        } else {
            Regex("class\\s+MainActivity\\s*(?:\\([^)]*\\))?\\s*:\\s*[A-Za-z0-9_.]*Activity\\b")
        }
        if (!classPattern.containsMatchIn(content)) return null
        val pkg = Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)").find(content)?.groupValues?.get(1)
        // A package declaration is required so the generated manifest can point to the class
        // unambiguously. Default-package Activities are rejected rather than silently guessed.
        if (pkg.isNullOrBlank()) return null
        return "$pkg.MainActivity"
    }

    /** A valid, stable, project-specific application id that avoids APK install collisions. */
    private fun safeApplicationId(projectName: String, projectIdentity: String): String {
        val slug = projectName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(24)
            .ifBlank { "app" }
        val suffix = projectIdentity.hashCode().toUInt().toString(16).takeLast(8)
        return "com.nawa.generated_${slug}_$suffix"
    }

    private fun generatedGitIgnore() = """
.gradle/
.idea/
local.properties
build/
app/build/
*.iml
.DS_Store
""".trimIndent() + "\n"

    private fun write(root: File, relativePath: String, content: String) {
        val file = File(root, relativePath)
        val parent = file.parentFile ?: error("Invalid output path")
        if (!parent.exists() && !parent.mkdirs()) error("Could not create ${parent.path}")
        file.writeText(content, StandardCharsets.UTF_8)
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').take(36).ifBlank { "nawa-project" }

    private const val MAX_NATIVE_FILE_BYTES = 25L * 1024 * 1024
    private const val MAX_NATIVE_TOTAL_BYTES = 150L * 1024 * 1024
    private const val MAX_NATIVE_FILE_COUNT = 2_000
}
