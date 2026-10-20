package com.nawa.studio.core

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

/** Static workspace findings produced without running the user's code or Gradle scripts. */
data class NawaProjectFinding(
    val path: String,
    val severity: Severity,
    val message: String,
    val line: Int? = null
) {
    enum class Severity { ERROR, WARNING, INFO }
}

/**
 * Bounded project audit for pre-build checks. It validates NawaScript, Android settings,
 * project paths, Android manifest XML, local JAR archives, resource sizes and common leaked secrets.
 * It is intentionally static: it never executes shell/Gradle/source files.
 */
object NawaProjectAudit {
    private const val MAX_FILES = 3_000
    private const val MAX_SCAN_BYTES = 24L * 1024 * 1024
    private const val MAX_FILE_BYTES = 40L * 1024 * 1024
    private const val MAX_EXPORT_BYTES = 45L * 1024 * 1024
    private val textualExtensions = setOf(
        "nawa", "kt", "kts", "java", "xml", "json", "properties", "gradle", "md", "txt", "js", "ts",
        "py", "sh", "yml", "yaml", "toml", "sql", "html", "css", "pro", "cfg", "ini"
    )
    private val likelySecretPatterns = listOf(
        Regex("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        Regex("\\bAKIA[0-9A-Z]{16}\\b"),
        Regex("(?i)\\b(?:api[_-]?key|access[_-]?token|client[_-]?secret)\\s*[:=]\\s*[\"'][^\"']{12,}[\"']"),
        Regex("\\bsk-[A-Za-z0-9_-]{24,}\\b")
    )

    fun scan(projectRoot: File, sourceOverride: String? = null): List<NawaProjectFinding> {
        val findings = mutableListOf<NawaProjectFinding>()
        val root = runCatching { projectRoot.canonicalFile }.getOrElse {
            return listOf(NawaProjectFinding("", NawaProjectFinding.Severity.ERROR, "تعذر تحديد مسار المشروع: ${it.message}"))
        }
        if (!root.isDirectory) return listOf(NawaProjectFinding("", NawaProjectFinding.Severity.ERROR, "مجلد المشروع غير موجود."))

        val files = root.walkTopDown()
            .onEnter { dir -> dir == root || (!dir.name.startsWith(".") && dir.name !in setOf("build", ".gradle", "node_modules")) }
            .filter { it.isFile }
            .take(MAX_FILES + 1)
            .toList()
        if (files.size > MAX_FILES) {
            findings += NawaProjectFinding("", NawaProjectFinding.Severity.WARNING, "الفحص محدود إلى أول $MAX_FILES ملفًا لتجنب استهلاك الذاكرة.")
        }
        val considered = files.take(MAX_FILES)
        var scannedBytes = 0L
        var totalBytes = 0L
        val relativePaths = mutableListOf<String>()
        for (file in considered) {
            val relative = runCatching { file.relativeTo(root).invariantSeparatorsPath }.getOrDefault(file.name)
            relativePaths += relative
            val canonicalOk = runCatching { file.canonicalPath.startsWith(root.path + File.separator) }.getOrDefault(false)
            if (!canonicalOk) {
                findings += NawaProjectFinding(relative, NawaProjectFinding.Severity.ERROR, "مسار الملف يتجاوز مجلد المشروع؛ افحص الروابط الرمزية.")
                continue
            }
            totalBytes += file.length()
            if (file.length() > MAX_FILE_BYTES) {
                findings += NawaProjectFinding(relative, NawaProjectFinding.Severity.ERROR, "الملف يتجاوز حد الملف المدعوم ($MAX_FILE_BYTES بايت).")
                continue
            }
            if (file.length() == 0L && relative.startsWith("assets/")) {
                findings += NawaProjectFinding(relative, NawaProjectFinding.Severity.WARNING, "المورد فارغ؛ تأكد أنه مقصود.")
            }
            if (relative.startsWith("libs/") && relative.endsWith(".jar", ignoreCase = true)) {
                val validJar = runCatching { ZipFile(file).use { zip -> zip.entries().hasMoreElements() } }.getOrDefault(false)
                if (!validJar) findings += NawaProjectFinding(relative, NawaProjectFinding.Severity.ERROR, "ملف JAR تالف أو ليس أرشيف ZIP صالحًا.")
            }
            val ext = file.extension.lowercase()
            if (ext in textualExtensions && file.length() <= 2L * 1024 * 1024 && scannedBytes + file.length() <= MAX_SCAN_BYTES) {
                val text = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull() ?: continue
                scannedBytes += file.length()
                likelySecretPatterns.forEach { pattern ->
                    if (pattern.containsMatchIn(text)) {
                        findings += NawaProjectFinding(relative, NawaProjectFinding.Severity.WARNING, "قد يحتوي الملف على مفتاح أو سر حساس. أزل الأسرار من المصدر قبل المشاركة.")
                    }
                }
            }
        }
        if (totalBytes > MAX_EXPORT_BYTES) {
            findings += NawaProjectFinding("", NawaProjectFinding.Severity.WARNING, "مجموع الملفات ($totalBytes بايت) يتجاوز حد تصدير المشروع الحالي ($MAX_EXPORT_BYTES بايت).")
        }
        val mainSourceFile = File(root, "src/main.nawa")
        val mainSource = sourceOverride ?: runCatching { if (mainSourceFile.isFile) mainSourceFile.readText(StandardCharsets.UTF_8) else null }.getOrNull()
        if (mainSource == null) {
            findings += NawaProjectFinding("src/main.nawa", NawaProjectFinding.Severity.WARNING, "ملف دخول NawaScript غير موجود. يلزم وجود Activity أصلية صالحة لتجهيز مشروع Android.")
        } else {
            val parsed = NawaParser.parse(mainSource)
            parsed.diagnostics.forEach { diagnostic ->
                findings += NawaProjectFinding(
                    "src/main.nawa", when (diagnostic.severity) {
                        NawaDiagnostic.Severity.ERROR -> NawaProjectFinding.Severity.ERROR
                        NawaDiagnostic.Severity.WARNING -> NawaProjectFinding.Severity.WARNING
                        NawaDiagnostic.Severity.INFO -> NawaProjectFinding.Severity.INFO
                    }, diagnostic.message, diagnostic.line
                )
            }
            if (!parsed.hasErrors) findings += NawaProjectFinding("src/main.nawa", NawaProjectFinding.Severity.INFO, "قواعد NawaScript المدعومة اجتازت الفحص الساكن.")
        }

        val configFile = File(root, "android.properties")
        if (configFile.isFile) {
            runCatching { NawaAndroidConfig.parse(configFile.readText(StandardCharsets.UTF_8)).validate() }
                .onFailure { findings += NawaProjectFinding("android.properties", NawaProjectFinding.Severity.ERROR, "إعدادات Android غير صالحة: ${it.message ?: "قيمة غير صحيحة"}") }
        }
        val manifests = considered.filter { it.name == "AndroidManifest.xml" && (it.path.contains("/src/main/") || it.path.startsWith("android/src/main/")) }
        manifests.forEach { manifest ->
            val relative = manifest.relativeTo(root).invariantSeparatorsPath
            val valid = runCatching {
                val factory = DocumentBuilderFactory.newInstance()
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                runCatching { factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
                runCatching { factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
                val builder = factory.newDocumentBuilder()
                // Parser defaults may print malformed user XML to stderr; route all parser
                // diagnostics through the audit result instead of leaking noisy logs.
                builder.setErrorHandler(object : DefaultHandler() {
                    override fun warning(e: SAXParseException) { throw e }
                    override fun error(e: SAXParseException) { throw e }
                    override fun fatalError(e: SAXParseException) { throw e }
                })
                builder.parse(manifest)
            }.isSuccess
            if (!valid) findings += NawaProjectFinding(relative, NawaProjectFinding.Severity.ERROR, "ملف AndroidManifest.xml ليس XML صالحًا أو لم يجتز التحقق الآمن.")
        }
        val activities = considered.filter { (it.extension.equals("kt", true) || it.extension.equals("java", true)) && it.name == "MainActivity.${it.extension}" }
        if (activities.size > 1) {
            findings += NawaProjectFinding("", NawaProjectFinding.Severity.WARNING, "وُجد أكثر من ملف باسم MainActivity؛ قد يحتاج مولّد Android إلى نقطة دخول واحدة واضحة: ${activities.joinToString { it.relativeTo(root).invariantSeparatorsPath }}")
        }
        return findings.distinctBy { listOf(it.path, it.severity, it.message, it.line) }
            .take(400)
    }
}
