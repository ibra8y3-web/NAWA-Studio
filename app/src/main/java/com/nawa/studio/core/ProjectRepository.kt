package com.nawa.studio.core

import android.content.Context
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Project data kept in the app's private storage. */
data class NawaProject(
    val id: String,
    val name: String,
    val directory: File,
    val updatedAt: Long
) {
    val sourceFile: File get() = File(directory, "src/main.nawa")
    val manifestFile: File get() = File(directory, "project.nawa")
}

enum class NawaProjectTemplate(val title: String, val description: String) {
    NAWA_COUNTER("عداد NawaScript", "مشروع تفاعلي بحالة وأزرار وشروط وحلقات محدودة"),
    NAWA_BLANK("شاشة فارغة", "بداية بسيطة بلغة نواة لإضافة عناصر الواجهة بنفسك"),
    NAWA_MULTISCREEN("تطبيق متعدد الشاشات", "شاشة رئيسية وصفحة إعدادات مع تنقّل بينهما"),
    ANDROID_COMPOSE("Android أصلي — Kotlin/Compose", "ملف MainActivity.kt أصلي قابل للتحرير والبناء عبر Gradle"),
    ANDROID_JAVA("Android أصلي — Java", "ملف MainActivity.java يستخدم Activity وواجهة Android الأصلية")
}

data class ProjectSearchMatch(val path: String, val lineNumber: Int, val snippet: String)
data class ProjectSearchResponse(
    val matches: List<ProjectSearchMatch>,
    val scannedFiles: Int,
    val truncated: Boolean
)

class ProjectRepository(context: Context) {
    private val appContext = context.applicationContext
    private val projectsRoot = File(appContext.filesDir, "nawa/projects")
    private val exportsRoot = File(appContext.cacheDir, "nawa/exports")
    private val revisionStore = NawaRevisionStore()

    fun listProjects(): List<NawaProject> {
        if (!projectsRoot.exists()) return emptyList()
        return projectsRoot.listFiles()
            ?.filter { it.isDirectory && File(it, "project.nawa").isFile }
            ?.mapNotNull(::readProject)
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    fun ensureStarterProject() {
        if (listProjects().isNotEmpty()) return
        createProject("مشروعي الأول")
    }

    fun createProject(nameInput: String, template: NawaProjectTemplate = NawaProjectTemplate.NAWA_COUNTER): NawaProject {
        val name = cleanDisplayName(nameInput)
        require(name.isNotEmpty()) { "اكتب اسمًا للمشروع." }
        ensureDirectory(projectsRoot)
        val id = "nawa-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        val directory = File(projectsRoot, id)
        check(directory.mkdirs()) { "تعذر إنشاء مجلد المشروع." }
        try {
            // Create a useful, navigable workspace up front: source, tests, docs, native Android
            // source/resource roots, local libraries, extension manifests and scripts.
            val starterDirectories = listOf(
                "src", "src/test", "src/main/java", "src/main/kotlin", "src/main/res",
                "src/main/res/drawable", "src/main/res/layout", "src/main/res/values",
                "src/main/assets", "src/main/aidl", "src/main/jniLibs",
                "ui", "assets", "tests", "docs", "libs", "plugins", "scripts",
                "android", "android/src", "android/src/main", "android/src/main/java",
                "android/src/main/kotlin", "android/src/main/res", "android/src/main/res/drawable",
                "android/src/main/res/layout", "android/src/main/res/values", "android/src/main/assets",
                "android/src/main/aidl", "android/src/main/jniLibs",
                "android/src/test/java", "android/src/androidTest/java"
            )
            starterDirectories.forEach { ensureDirectory(File(directory, it)) }
            writeAtomically(File(directory, "project.nawa"), manifest(id, name).toString(2))
            writeAtomically(File(directory, "src/main.nawa"), starterSource(name, template))
            when (template) {
                NawaProjectTemplate.ANDROID_COMPOSE -> writeAtomically(
                    File(directory, "src/main/kotlin/com/nawa/starter/MainActivity.kt"),
                    kotlinActivityTemplate("com.nawa.starter")
                )
                NawaProjectTemplate.ANDROID_JAVA -> writeAtomically(
                    File(directory, "src/main/java/com/nawa/starter/MainActivity.java"),
                    javaActivityTemplate("com.nawa.starter")
                )
                else -> Unit
            }
            writeAtomically(File(directory, "android.properties"), NawaAndroidConfig.defaultTemplate())
            writeAtomically(File(directory, "packages.lock"), "{\n  \"formatVersion\": 1,\n  \"dependencies\": []\n}\n")
            writeAtomically(File(directory, ".gitignore"), ".gradle/\nbuild/\nlocal.properties\n*.iml\n.idea/\n.DS_Store\n")
            writeAtomically(File(directory, ".editorconfig"), "root = true\n\n[*]\ncharset = utf-8\nend_of_line = lf\ninsert_final_newline = true\nindent_style = space\nindent_size = 4\n")
            writeAtomically(File(directory, "ui/README.md"), "# واجهات المشروع\n\nعرّف واجهات NawaScript في `src/main.nawa`. هذه النسخة تربط المصمم المرئي بالنصوص والأزرار، ولا تدّعي دعم كل عناصر Android بعد.\n")
            writeAtomically(File(directory, "tests/README.md"), "# الاختبارات\n\nضع سيناريوهات الاختبار اليدوية للمشروع هنا. اختبارات نواة الآلية تتحقق من مجموعة NawaScript المدعومة فقط.\n")
            writeAtomically(File(directory, "src/test/README.md"), "# اختبارات المصدر\n\nمجلد مُهيأ لاختبارات وحدة أو ملفات اختبار للمشروع. إعداد تشغيلها يعتمد على أداة البناء التي تختارها للمشروع المُصدَّر.\n")
            writeAtomically(File(directory, "docs/NAWASCRIPT_AR.md"), nawaLanguageReadme())
            writeAtomically(File(directory, "docs/ANDROID_SOURCES_AR.md"), androidSourcesReadme())
            writeAtomically(File(directory, "libs/README.md"), "# المكتبات المحلية\n\nضع ملفات JAR المحلية هنا. تُضمّن ملفات `libs/*.jar` عند تصدير مشروع Android. ملفات AAR تحتاج إعداد Gradle إضافيًا ولم تُفعّل تلقائيًا في هذه النسخة.\n")
            writeAtomically(File(directory, "plugins/README.md"), "# إضافات المشروع\n\nهذا مجلد لحفظ تعريفات ومصادر إضافاتك. هذه النسخة لا تشغّل شيفرات إضافات اعتباطية ولا تحتوي متجر إضافات؛ لا تعتبر وجود ملف هنا تثبيتًا أو تنفيذًا لإضافة.\n")
            writeAtomically(File(directory, "plugins/manifest.template.json"), "{\n  \"schemaVersion\": 1,\n  \"description\": \"Metadata-only extension manifest draft; not loaded as executable code.\",\n  \"requiredFields\": [\"id\", \"name\", \"version\", \"entry\"]\n}\n")
            writeAtomically(File(directory, "android/README.md"), androidSourcesReadme())
            writeAtomically(File(directory, "scripts/README.md"), "# سكربتات المشروع\n\nهذا مجلد لحفظ أدوات التطوير النصية. لا تشغّل نواة سكربتات shell تلقائيًا؛ مراجعتها وتشغيلها مسؤوليتك خارج المعاينة المحدودة.\n")
            writeAtomically(File(directory, "README.md"), projectReadme(name))
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
        return readProject(directory) ?: error("تم إنشاء المشروع لكن تعذرت قراءته.")
    }

    /** Lists real, non-hidden project directories for the workspace tree. */
    fun listFolders(project: NawaProject): List<String> {
        val root = checkedProjectRoot(project)
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .onEnter { dir -> dir == root || (runCatching { dir.canonicalPath.startsWith(root.canonicalPath + File.separator) }.getOrDefault(false) && !dir.relativeTo(root).invariantSeparatorsPath.split('/').any { part -> part.startsWith(".") }) }
            .filter { it.isDirectory && it != root && !it.relativeTo(root).invariantSeparatorsPath.split('/').any { part -> part.startsWith(".") } }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .filter { safeRelativePath(it) != null }
            .take(2_000)
            .sorted()
            .toList()
    }

    /** Creates a project subdirectory with traversal-safe canonical path checks. */
    fun createDirectory(project: NawaProject, relativePathInput: String): String {
        val relativePath = normalizeRelativePath(relativePathInput).trimEnd('/')
        require(relativePath.isNotBlank() && relativePath != "project.nawa" && !relativePath.startsWith(".nawa-history")) {
            "اسم المجلد محجوز أو غير صالح."
        }
        val target = resolveProjectFile(project, relativePath)
        require(!target.exists()) { "يوجد ملف أو مجلد بهذا الاسم بالفعل." }
        ensureDirectory(target)
        touchProject(project)
        return relativePath
    }

    /** Removes only empty user directories; files are never recursively deleted by this operation. */
    fun deleteEmptyDirectory(project: NawaProject, relativePathInput: String) {
        val relativePath = normalizeRelativePath(relativePathInput)
        require(relativePath !in setOf("src", "assets", "ui", "tests") && !relativePath.startsWith(".nawa-history")) {
            "هذا مجلد أساسي لا يمكن حذفه مباشرة."
        }
        val target = resolveProjectFile(project, relativePath)
        require(target.isDirectory) { "المجلد غير موجود." }
        require(target.list()?.isEmpty() == true) { "المجلد ليس فارغًا؛ احذف محتوياته أولًا." }
        check(target.delete()) { "تعذر حذف المجلد." }
        pruneEmptyParents(target.parentFile, checkedProjectRoot(project))
        touchProject(project)
    }

    fun listFiles(project: NawaProject): List<String> {
        val root = checkedProjectRoot(project)
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.name != "project.nawa" && !it.name.endsWith(".tmp") }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .filter { safeRelativePath(it) != null }
            .sortedWith(compareBy<String>({ it.substringBeforeLast('/', "") }, { it }))
            .toList()
    }

    /** Case-insensitive plain-text search over supported source/document files in the project. */
    fun searchInFiles(project: NawaProject, queryInput: String, resultLimit: Int = 200): ProjectSearchResponse {
        val query = queryInput.trim()
        require(query.isNotEmpty()) { "اكتب نصًا للبحث في المشروع." }
        require(query.length <= 160) { "عبارة البحث طويلة جدًا (الحد 160 حرفًا)." }
        require(resultLimit in 1..500) { "حد النتائج غير صالح." }
        val candidates = listFiles(project).filter { path ->
            path.substringAfterLast('.', "").lowercase() in SEARCHABLE_EXTENSIONS
        }
        val scannedLimit = 2_000
        val candidatesToScan = candidates.take(scannedLimit)
        val matches = mutableListOf<ProjectSearchMatch>()
        var scanned = 0
        var bytesRead = 0L
        var capped = candidates.size > scannedLimit
        val needle = query.lowercase(java.util.Locale.ROOT)
        for (path in candidatesToScan) {
            val file = runCatching { resolveProjectFile(project, path) }.getOrNull() ?: continue
            if (!file.isFile || file.length() > MAX_TEXT_FILE_BYTES) continue
            if (bytesRead + file.length() > MAX_SEARCH_TOTAL_BYTES) {
                capped = true
                break
            }
            bytesRead += file.length()
            val contents = runCatching { readFile(project, path) }.getOrNull() ?: continue
            scanned++
            contents.lineSequence().forEachIndexed { index, line ->
                if (line.lowercase(java.util.Locale.ROOT).contains(needle)) {
                    val cleaned = line.trim().replace('\t', ' ').take(220)
                    matches += ProjectSearchMatch(path, index + 1, cleaned)
                    if (matches.size >= resultLimit) {
                        capped = true
                        return ProjectSearchResponse(matches, scanned, capped)
                    }
                }
            }
        }
        return ProjectSearchResponse(matches, scanned, capped)
    }

    fun readFile(project: NawaProject, relativePath: String): String {
        val file = resolveProjectFile(project, relativePath)
        require(file.isFile) { "الملف غير موجود: $relativePath" }
        require(file.length() <= MAX_TEXT_FILE_BYTES) { "الملف أكبر من حد المحرر النصي (2 ميجابايت)." }
        return file.readText(StandardCharsets.UTF_8)
    }

    fun readSource(project: NawaProject): String = readFile(project, "src/main.nawa")

    fun listRevisions(project: NawaProject, relativePath: String): List<NawaRevision> {
        resolveProjectFile(project, relativePath)
        return revisionStore.list(checkedProjectRoot(project), relativePath)
    }

    /** Restoring a revision saves the displaced current file into history first. */
    fun restoreRevision(project: NawaProject, relativePath: String, revisionId: String): String {
        val restored = revisionStore.read(checkedProjectRoot(project), relativePath, revisionId)
        saveFile(project, relativePath, restored)
        return restored
    }

    fun saveSource(project: NawaProject, contents: String) = saveFile(project, "src/main.nawa", contents)

    fun saveFile(project: NawaProject, relativePath: String, contents: String) {
        require(relativePath != "project.nawa") { "ملف تعريف المشروع يُدار تلقائيًا." }
        require(contents.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_FILE_BYTES) {
            "حجم الملف يتجاوز حد الحفظ النصي البالغ 2 ميجابايت."
        }
        val file = resolveProjectFile(project, relativePath)
        require(file.name.isNotBlank()) { "اسم الملف غير صالح." }
        revisionStore.snapshot(checkedProjectRoot(project), relativePath, file, contents)
        writeAtomically(file, contents)
        val manifest = readManifest(project)
        writeAtomically(project.manifestFile, manifest.toString(2))
    }

    /** Imports a real binary resource into assets/, with a size limit and atomic installation. */
    fun importAsset(project: NawaProject, displayName: String, input: InputStream): String {
        val rawName = displayName.substringAfterLast('/').substringAfterLast('\\')
        val safeName = rawName.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").trim('_').take(120)
        require(safeName.isNotBlank() && safeName != "." && safeName != "..") { "اسم المورد غير صالح." }
        val relative = "assets/$safeName"
        val target = resolveProjectFile(project, relative)
        require(!target.exists()) { "يوجد مورد بهذا الاسم داخل assets بالفعل." }
        ensureDirectory(target.parentFile ?: error("مسار المورد غير صالح."))
        val temporary = File(target.parentFile, ".${target.name}.${UUID.randomUUID().toString().take(6)}.tmp")
        var copied = 0L
        try {
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= MAX_ASSET_BYTES) { "حجم المورد يتجاوز الحد المسموح (40 ميجابايت)." }
                    output.write(buffer, 0, count)
                }
            }
            require(copied > 0L) { "المورد المختار فارغ." }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath())
            }
            touchProject(project)
            return relative
        } finally {
            temporary.delete()
        }
    }

    /** Imports one file into any safe project path, including libs/, source and Android resources. */
    fun importFile(project: NawaProject, relativePathInput: String, input: InputStream): String {
        val relativePath = normalizeRelativePath(relativePathInput)
        require(relativePath != "project.nawa" && relativePath != "src/main.nawa" && !relativePath.startsWith(".nawa-history/")) {
            "المسار محجوز أو تابع لسجل النسخ السابقة."
        }
        require(relativePath.length <= 180) { "مسار الملف طويل جدًا." }
        val target = resolveProjectFile(project, relativePath)
        require(!target.exists()) { "يوجد ملف بهذا المسار بالفعل؛ أعد تسمية الملف أو اختر مسارًا جديدًا." }
        ensureDirectory(target.parentFile ?: error("مسار غير صالح."))
        val temporary = File(target.parentFile, ".${target.name}.${UUID.randomUUID().toString().take(8)}.tmp")
        var copied = 0L
        try {
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= MAX_ASSET_BYTES) { "الملف المستورد يتجاوز الحد المسموح (40 ميجابايت)." }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
            require(copied > 0L) { "الملف المختار فارغ." }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath())
            }
            touchProject(project)
            return relativePath
        } finally {
            temporary.delete()
        }
    }

    fun createFile(project: NawaProject, relativePathInput: String): String {
        val relativePath = normalizeRelativePath(relativePathInput)
        require(relativePath != "project.nawa") { "هذا الاسم محجوز لملف تعريف المشروع." }
        require(relativePath.length <= 180) { "مسار الملف طويل جدًا." }
        val file = resolveProjectFile(project, relativePath)
        require(!file.exists()) { "يوجد ملف بهذا الاسم بالفعل." }
        ensureDirectory(file.parentFile ?: error("مسار غير صالح."))
        val initial = when {
            file.extension.equals("kt", ignoreCase = true) && file.nameWithoutExtension == "MainActivity" &&
                (relativePath.startsWith("src/main/kotlin/") || relativePath.startsWith("src/main/java/")) ->
                kotlinActivityTemplate(packageForSourcePath(relativePath))
            file.extension.equals("java", ignoreCase = true) && file.nameWithoutExtension == "MainActivity" &&
                relativePath.startsWith("src/main/java/") -> javaActivityTemplate(packageForSourcePath(relativePath))
            file.extension.equals("nawa", ignoreCase = true) -> "// ملف NawaScript جديد\n"
            file.extension.equals("md", ignoreCase = true) -> "# ${file.nameWithoutExtension}\n"
            file.extension.equals("json", ignoreCase = true) -> "{\n  \n}\n"
            else -> ""
        }
        writeAtomically(file, initial)
        touchProject(project)
        return relativePath
    }

    fun renameFile(project: NawaProject, oldPathInput: String, newPathInput: String): String {
        val oldPath = normalizeRelativePath(oldPathInput)
        val newPath = normalizeRelativePath(newPathInput)
        require(oldPath != "src/main.nawa") { "لا يمكن تغيير مسار نقطة الدخول في هذه النسخة." }
        require(oldPath != "project.nawa" && newPath != "project.nawa") { "اسم project.nawa محجوز." }
        val oldFile = resolveProjectFile(project, oldPath)
        val newFile = resolveProjectFile(project, newPath)
        require(oldFile.isFile) { "الملف الأصلي غير موجود." }
        require(!newFile.exists()) { "الاسم الجديد مستخدم بالفعل." }
        ensureDirectory(newFile.parentFile ?: error("مسار غير صالح."))
        check(oldFile.renameTo(newFile)) { "تعذر إعادة تسمية الملف." }
        touchProject(project)
        return newPath
    }

    fun deleteFile(project: NawaProject, relativePathInput: String) {
        val relativePath = normalizeRelativePath(relativePathInput)
        require(relativePath != "src/main.nawa") { "لا يمكن حذف نقطة دخول المشروع." }
        require(relativePath != "project.nawa") { "لا يمكن حذف ملف تعريف المشروع." }
        val file = resolveProjectFile(project, relativePath)
        require(file.isFile) { "الملف غير موجود." }
        check(file.delete()) { "تعذر حذف الملف." }
        pruneEmptyParents(file.parentFile, checkedProjectRoot(project))
        touchProject(project)
    }

    fun deleteProject(project: NawaProject) {
        val root = checkedProjectRoot(project)
        check(root.canonicalPath.startsWith(projectsRoot.canonicalPath + File.separator)) {
            "رفض حذف مسار خارج مجلد المشاريع."
        }
        check(root.deleteRecursively()) { "تعذر حذف المشروع." }
    }

    fun exportProject(project: NawaProject): File {
        ensureDirectory(exportsRoot)
        val folders = listFolders(project)
        val entries = listFiles(project)
        require(entries.size + folders.size + 1 <= MAX_ARCHIVE_ENTRIES) { "المشروع يحتوي على ملفات أو مجلدات أكثر من الحد المسموح للتصدير." }
        var totalBytes = project.manifestFile.length()
        entries.forEach { relative ->
            totalBytes += resolveProjectFile(project, relative).length()
            require(totalBytes <= MAX_EXPORT_CONTENT_BYTES) {
                "حجم المشروع أكبر من حد الحزمة القابلة للمشاركة (45 ميجابايت). احذف الموارد غير المستخدمة أو قسّم المشروع."
            }
        }
        val zipFile = File(exportsRoot, "${safeFileName(project.name)}-${project.id.takeLast(8)}.nsqpack")
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            // Preserve empty source/resource directories as well as files in .nsqpack archives.
            for (folder in folders.sortedWith(compareBy<String>({ it.count { c -> c == '/' } }, { it }))) {
                zip.putNextEntry(ZipEntry("$folder/"))
                zip.closeEntry()
            }
            for (relative in entries) {
                val file = resolveProjectFile(project, relative)
                addToZip(zip, file, relative)
            }
            addToZip(zip, project.manifestFile, "project.nawa")
        }
        if (zipFile.length() > MAX_ARCHIVE_BYTES) {
            zipFile.delete()
            error("تعذر التصدير: الحزمة الناتجة تتجاوز الحد المدعوم للاستيراد.")
        }
        return zipFile
    }

    /** Safely import a .nsqpack archive. Zip Slip, oversized archives and duplicate entries are rejected. */
    fun importProject(zipFile: File): NawaProject {
        require(zipFile.isFile && zipFile.length() <= MAX_ARCHIVE_BYTES) { "ملف المشروع غير صالح أو أكبر من 50 ميجابايت." }
        ensureDirectory(projectsRoot)
        val id = "nawa-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        val tempRoot = File(projectsRoot, ".import-$id")
        ensureDirectory(tempRoot)
        var expandedBytes = 0L
        var entries = 0
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= MAX_ARCHIVE_ENTRIES) { "الأرشيف يحتوي على ملفات كثيرة جدًا." }
                    val entryName = if (entry.isDirectory) entry.name.trimEnd('/') else entry.name
                    val rel = safeRelativePath(entryName)
                        ?: throw IllegalArgumentException("مسار غير آمن داخل الحزمة: ${entry.name}")
                    if (entry.isDirectory) {
                        ensureDirectory(resolveSafe(tempRoot, rel))
                    } else {
                        val out = resolveSafe(tempRoot, rel)
                        require(!out.exists()) { "الحزمة تحتوي على مسار مكرر: $rel" }
                        ensureDirectory(out.parentFile ?: error("مسار غير صالح."))
                        FileOutputStream(out).use { sink ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                expandedBytes += count
                                require(expandedBytes <= MAX_EXPANDED_BYTES) { "حجم الملفات بعد فك الضغط أكبر من 100 ميجابايت." }
                                sink.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val incomingManifest = File(tempRoot, "project.nawa")
            val incomingSource = File(tempRoot, "src/main.nawa")
            require(incomingManifest.isFile && incomingSource.isFile) {
                "هذه ليست حزمة مشروع نواة صحيحة؛ يلزم project.nawa وsrc/main.nawa."
            }
            val oldJson = JSONObject(incomingManifest.readText(StandardCharsets.UTF_8))
            val name = cleanDisplayName(oldJson.optString("name", zipFile.nameWithoutExtension))
                .ifBlank { "مشروع مستورد" }
            writeAtomically(incomingManifest, manifest(id, name).toString(2))
            val destination = File(projectsRoot, id)
            check(tempRoot.renameTo(destination)) { "تعذر تثبيت ملفات المشروع المستورد." }
            return readProject(destination) ?: error("تعذر قراءة المشروع بعد استيراده.")
        } catch (error: Throwable) {
            tempRoot.deleteRecursively()
            throw error
        }
    }

    fun exportAndroidProject(project: NawaProject, source: String): File {
        return NawaAndroidProjectGenerator.export(project.name, source, exportsRoot, checkedProjectRoot(project))
    }

    private fun readProject(directory: File): NawaProject? = runCatching {
        val json = JSONObject(File(directory, "project.nawa").readText(StandardCharsets.UTF_8))
        val id = json.optString("id", directory.name)
        val name = json.optString("name", directory.name)
        val updatedAt = maxOf(
            json.optLong("updatedAt", 0L),
            directory.lastModified(),
            File(directory, "src/main.nawa").lastModified()
        )
        NawaProject(id, name, directory, updatedAt)
    }.getOrNull()

    private fun readManifest(project: NawaProject): JSONObject {
        val root = checkedProjectRoot(project)
        val json = JSONObject(File(root, "project.nawa").readText(StandardCharsets.UTF_8))
        return json.put("updatedAt", System.currentTimeMillis())
    }

    private fun touchProject(project: NawaProject) {
        writeAtomically(project.manifestFile, readManifest(project).toString(2))
    }

    private fun manifest(id: String, name: String) = JSONObject()
        .put("format", PROJECT_FORMAT)
        .put("id", id)
        .put("name", name)
        .put("entry", "src/main.nawa")
        .put("language", "NawaScript")
        .put("updatedAt", System.currentTimeMillis())

    private fun resolveProjectFile(project: NawaProject, relativePath: String): File =
        resolveSafe(checkedProjectRoot(project), normalizeRelativePath(relativePath))

    private fun checkedProjectRoot(project: NawaProject): File {
        val root = project.directory.canonicalFile
        require(root.path.startsWith(projectsRoot.canonicalPath + File.separator)) { "مسار المشروع غير آمن." }
        return root
    }

    private fun resolveSafe(root: File, relativePath: String): File {
        val safe = safeRelativePath(relativePath) ?: throw IllegalArgumentException("مسار ملف غير آمن.")
        val rootPath = root.canonicalPath + File.separator
        val resolved = File(root, safe).canonicalFile
        require(resolved.path.startsWith(rootPath)) { "رفض الوصول خارج مساحة المشروع." }
        return resolved
    }

    private fun normalizeRelativePath(path: String): String =
        safeRelativePath(path) ?: throw IllegalArgumentException("اكتب مسارًا نسبيًا صحيحًا مثل src/helpers.nawa.")

    private fun safeRelativePath(path: String): String? {
        val normalized = path.replace('\\', '/').trim()
        if (normalized.isBlank() || normalized.startsWith('/') || normalized.contains('\u0000')) return null
        if (normalized.contains(':')) return null
        val parts = normalized.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) return null
        val rootMetadataFile = parts.size == 1 && parts.firstOrNull() in setOf(".gitignore", ".editorconfig")
        if (parts.firstOrNull()?.startsWith(".") == true && parts.first() != ".well-known" && !rootMetadataFile) return null
        return parts.joinToString("/")
    }

    private fun writeAtomically(file: File, contents: String) {
        val parent = file.parentFile ?: error("المسار غير صالح.")
        ensureDirectory(parent)
        val temporary = File(parent, ".${file.name}.${UUID.randomUUID().toString().take(6)}.tmp")
        FileOutputStream(temporary).bufferedWriter(StandardCharsets.UTF_8).use { it.write(contents) }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    private fun addToZip(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.isFile) return
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun pruneEmptyParents(start: File?, stopAt: File) {
        var current = start
        while (current != null && current != stopAt && current.isDirectory && current.list()?.isEmpty() == true) {
            current.delete()
            current = current.parentFile
        }
    }

    private fun ensureDirectory(dir: File) {
        if (!dir.exists() && !dir.mkdirs()) error("تعذر إنشاء المجلد: ${dir.name}")
        check(dir.isDirectory) { "المسار ليس مجلدًا: ${dir.name}" }
    }

    private fun cleanDisplayName(value: String): String = value
        .replace(Regex("[\\r\\n\\t\\u0000]"), " ")
        .trim().take(48)

    private fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').take(36).ifBlank { "nawa-project" }

    private fun starterSource(name: String, template: NawaProjectTemplate): String = when (template) {
        NawaProjectTemplate.NAWA_COUNTER -> """
app "${escapeNawaString(name)}" {
    screen Home {
        state count: Int = 0
        state message: String = "أهلاً بك في نواة"
        state enabled: Bool = true

        text("{message}")
        text("العدد الحالي: {count}")
        text("الحالة: {enabled}")

        button("زيادة العدد") { count = count + 1 }
        button("إنقاص العدد") { count = count - 1 }
        button("تبديل الحالة") { enabled = !enabled }
        button("ضعف العدد") { count = count * 2 }
        button("زيادة حتى 10") {
            while (count < 10 && enabled) { count = count + 1 }
        }
        button("زيادة آمنة") {
            if (count < 10 && enabled) {
                count = count + 1
                message = "تمت الزيادة"
            } else {
                message = "لم تتغير القيمة"
            }
        }
        button("زيادة ثلاثية") { repeat(3) { count = count + 1 } }
        fun resetCount() { count = 0 }
        button("تصفير") { resetCount() }
    }
}
""".trimIndent() + "\n"
        NawaProjectTemplate.NAWA_BLANK -> """
app "${escapeNawaString(name)}" {
    screen Home {
        text("مرحبًا بك في نواة")
        text("ابدأ تعديل src/main.nawa لإضافة الحالات والعناصر.")
    }
}
""".trimIndent() + "\n"
        NawaProjectTemplate.NAWA_MULTISCREEN -> """
app "${escapeNawaString(name)}" {
    screen Home {
        state count: Int = 0
        text("الصفحة الرئيسية")
        text("العدد: {count}")
        button("زيادة") { count = count + 1 }
        button("فتح الإعدادات") { navigate("Settings") }
    }
    screen Settings {
        state enabled: Bool = true
        text("الإعدادات")
        text("مفعّل: {enabled}")
        button("تبديل") { enabled = !enabled }
        button("العودة للرئيسية") { navigate("Home") }
    }
}
""".trimIndent() + "\n"
        NawaProjectTemplate.ANDROID_COMPOSE, NawaProjectTemplate.ANDROID_JAVA -> """
app "${escapeNawaString(name)}" {
    screen Home {
        text("${if (template == NawaProjectTemplate.ANDROID_COMPOSE) "Android أصلي باستخدام Kotlin وCompose" else "Android أصلي باستخدام Java"}")
        text("عدّل Activity داخل مجلد src/main/ قبل تجهيز Android.")
    }
}
""".trimIndent() + "\n"
    }

    private fun escapeNawaString(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    private fun packageForSourcePath(relativePath: String): String {
        val prefix = when {
            relativePath.startsWith("src/main/kotlin/") -> "src/main/kotlin/"
            relativePath.startsWith("src/main/java/") -> "src/main/java/"
            else -> return "com.example.nawa"
        }
        val directory = relativePath.removePrefix(prefix).substringBeforeLast('/', "")
        val segments = directory.split('/').filter { it.isNotBlank() }
        val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
        return if (segments.isNotEmpty() && segments.all(identifier::matches)) {
            segments.joinToString(".")
        } else {
            "com.example.nawa"
        }
    }

    private fun kotlinActivityTemplate(packageName: String): String = """
        package $packageName

        import android.os.Bundle
        import androidx.activity.ComponentActivity
        import androidx.activity.compose.setContent
        import androidx.compose.foundation.layout.Box
        import androidx.compose.foundation.layout.fillMaxSize
        import androidx.compose.material3.MaterialTheme
        import androidx.compose.material3.Surface
        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Alignment
        import androidx.compose.ui.Modifier

        class MainActivity : ComponentActivity() {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContent {
                    MaterialTheme {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            NawaHome()
                        }
                    }
                }
            }
        }

        @Composable
        private fun NawaHome() {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("أهلاً بك في تطبيقك الجديد")
            }
        }
    """.trimIndent() + "\n"

    private fun javaActivityTemplate(packageName: String): String = """
        package $packageName;

        import android.os.Bundle;
        import android.view.Gravity;
        import android.widget.TextView;
        import android.app.Activity;

        public class MainActivity extends Activity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                TextView message = new TextView(this);
                message.setText("Hello from your new NAWA app");
                message.setTextSize(22f);
                message.setGravity(Gravity.CENTER);
                setContentView(message);
            }
        }
    """.trimIndent() + "\n"

    private fun projectReadme(name: String): String = """
# $name

مشروع نواة (NAWA Studio).

- نقطة دخول المعاينة: `src/main.nawa`
- تعريف المشروع: `project.nawa`
- `ui/`: وثائق وملفات الواجهة
- `assets/`: الصور والموارد
- `tests/`: مساحة الاختبارات
- `docs/`: دليل اللغة ومسارات Android
- `libs/`: مكتبات JAR المحلية
- `plugins/`: تعريفات/مصادر metadata فقط؛ لا يشغّل هذا الإصدار إضافات اعتباطية
- حزمة المشاركة `.nsqpack` تحفظ الملفات والمجلدات مع فحص المسارات والأحجام عند الاستيراد.

## مثال NawaScript

```nawa
app "Counter" {
    screen Home {
        state count: Int = 0
        text("Count: {count}")
        button("Increase") { count = count + 1 }
    }
}
```

يدعم المحرك عدة شاشات وحالات `Int` و`String` و`Bool`، وعناصر `text` و`button`، والتنقل، والشروط المحدودة، والعمليات الحسابية الصحيحة، و`repeat(n)` و`while` بحد آمن، ودوال إجراءات بلا معاملات أو قيم راجعة. راجع `docs/NAWASCRIPT_AR.md` لمعرفة القواعد والحدود بدقة.

تحرير Kotlin/Java لا يعني أن NawaScript تترجمها تلقائيًا؛ يُجهّز تصدير Android مصدرًا قابلًا للبناء عندما تكون كل الملفات الأصلية صحيحة ومتوافقة.
""".trimIndent() + "\n"

    private fun nawaLanguageReadme(): String = """
# NawaScript داخل نواة

هذه لغة تجريبية صغيرة لا تدّعي أنها بديل كامل لـ Kotlin أو Java. ملف الدخول هو `src/main.nawa`.

## بنية مختصرة

```nawa
app "Example" {
    screen Home {
        state count: Int = 0
        text("Count: {count}")
        button("Increase") { count = count + 1 }
        button("Square") { count = count * 2 }
        button("Bounded loop") { while (count < 10) { count = count + 1 } }
    }
}
```

## المدعوم في هذا الإصدار

- عدة شاشات وحالات محلية من الأنواع `Int` و`String` و`Bool`/`Boolean`.
- `text`, `button`, و`navigate("ScreenName")`.
- إسناد القيم، وزيادة/إنقاص الأعداد، وعمليات `*`, `/`, `%` بأعداد ثابتة غير صفرية.
- `if/else`، والمقارنات المنطقية `&&`, `||`, `!`.
- `repeat(n)` و`while(condition)` بحد تشغيل لا يتجاوز 1000 تكرار للحلقة.
- إجراءات الشاشة `fun name() { ... }` بلا معاملات أو قيمة راجعة؛ تُرفض الاستدعاءات الدائرية.

## الحدود

لا توجد بعد مصفوفات أو كائنات أو أنواع عامة أو دوال ذات معاملات/قيم راجعة أو مكتبات NawaScript أو ترجمة لأي كود اعتباطي. التصدير يولّد Compose للتركيبات المدعومة فقط. أي كود Kotlin/Java تضيفه يخضع لبناء Gradle الكامل، وليس لمفسّر NawaScript.
""".trimIndent() + "\n"

    private fun androidSourcesReadme(): String = """
# مصادر Android الأصلية

يمكن وضع الملفات في هذه المسارات قبل تصدير المشروع:

- `android/src/main/java/` أو `src/main/java/` لملفات Java.
- `android/src/main/kotlin/` أو `src/main/kotlin/` لملفات Kotlin.
- `android/src/main/res/` أو `src/main/res/` للموارد XML وdrawable وlayout وvalues.
- `android/src/main/assets/`, `src/main/assets/` أو `assets/` للأصول.
- `android/src/main/aidl/` و`android/src/main/jniLibs/` لواجهات AIDL والمكتبات الأصلية.
- `libs/*.jar` للمكتبات المحلية بصيغة JAR.
- `android/src/main/AndroidManifest.xml` لمانيفست مخصص. وجوده يعني أن عليك توفير Activity أصلية صالحة؛ لا يُنشئ المصدّر Activity تلقائيًا فوق مانيفست مخصص.

مجرد إضافة ملف لا يضمن بناءه؛ يجب أن يكون المصدر صحيحًا ومتوافقًا، وأن تكون الاعتماديات متاحة في مستودعات Gradle. AAR، وNDK/CMake، وتوليد bindings، وتشغيل Gradle داخل التطبيق ليست مفعّلة تلقائيًا في هذه النسخة.
""".trimIndent() + "\n"

    companion object {
        private const val PROJECT_FORMAT = 1
        private const val MAX_TEXT_FILE_BYTES = 2L * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 50L * 1024 * 1024
        private const val MAX_EXPANDED_BYTES = 100L * 1024 * 1024
        private const val MAX_EXPORT_CONTENT_BYTES = 45L * 1024 * 1024
        private const val MAX_ARCHIVE_ENTRIES = 2_000
        private const val MAX_ASSET_BYTES = 40L * 1024 * 1024
        private const val MAX_SEARCH_TOTAL_BYTES = 50L * 1024 * 1024
        private val SEARCHABLE_EXTENSIONS = setOf(
            "nawa", "kt", "kts", "java", "js", "jsx", "ts", "tsx", "json", "xml", "html", "htm",
            "css", "py", "pyw", "sh", "bash", "c", "h", "cc", "cpp", "cxx", "hpp", "hh", "hxx",
            "md", "markdown", "txt", "yaml", "yml", "sql", "toml", "gradle", "properties", "ini", "conf",
            "gitignore", "cfg", "html", "svg"
        )
    }
}
