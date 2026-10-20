package com.nawa.studio.core

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** A previous on-disk revision of a project text file. */
data class NawaRevision(val id: String, val timestamp: Long, val sizeBytes: Long)

/**
 * Bounded local source history. Snapshots live below a hidden directory and are intentionally
 * excluded by ProjectRepository's public file listing and .nsqpack export.
 */
class NawaRevisionStore(
    private val maxRevisionsPerFile: Int = 20,
    private val maxFileBytes: Long = 2L * 1024 * 1024
) {
    init { require(maxRevisionsPerFile in 1..100) }

    fun snapshot(projectRoot: File, relativePath: String, existingFile: File, replacement: String) {
        if (!existingFile.isFile || existingFile.length() > maxFileBytes) return
        val replacementBytes = replacement.toByteArray(StandardCharsets.UTF_8)
        if (replacementBytes.size.toLong() > maxFileBytes) return
        val existingBytes = existingFile.readBytes()
        if (existingBytes.contentEquals(replacementBytes)) return
        val directory = historyDirectory(projectRoot, relativePath)
        if (!directory.exists() && !directory.mkdirs()) error("تعذر إنشاء سجل النسخ السابق للملف.")
        val newestTimestamp = directory.listFiles()?.mapNotNull { it.name.substringBefore('-').toLongOrNull() }?.maxOrNull() ?: 0L
        val timestamp = maxOf(System.currentTimeMillis(), newestTimestamp + 1L)
        val id = "$timestamp-${UUID.randomUUID().toString().replace("-", "").take(8)}.snap"
        val temporary = File(directory, ".$id.tmp")
        val target = File(directory, id)
        try {
            temporary.writeBytes(existingBytes)
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath())
            }
            trim(directory)
        } finally {
            temporary.delete()
        }
    }

    fun list(projectRoot: File, relativePath: String): List<NawaRevision> {
        val directory = historyDirectory(projectRoot, relativePath)
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles()
            ?.filter { it.isFile && REVISION_ID.matches(it.name) && it.length() <= maxFileBytes }
            ?.sortedByDescending { it.name.substringBefore('-').toLongOrNull() ?: it.lastModified() }
            ?.map { file ->
                val timestamp = file.name.substringBefore('-').toLongOrNull() ?: file.lastModified()
                NawaRevision(file.name, timestamp, file.length())
            }
            .orEmpty()
    }

    fun read(projectRoot: File, relativePath: String, revisionId: String): String {
        require(REVISION_ID.matches(revisionId)) { "معرّف النسخة السابقة غير صالح." }
        val directory = historyDirectory(projectRoot, relativePath)
        val file = File(directory, revisionId).canonicalFile
        require(file.parentFile == directory.canonicalFile && file.isFile) { "النسخة السابقة غير موجودة لهذا الملف." }
        require(file.length() <= maxFileBytes) { "النسخة السابقة أكبر من حد المحرر النصي." }
        return file.readText(StandardCharsets.UTF_8)
    }

    private fun historyDirectory(projectRoot: File, relativePath: String): File {
        val normalized = relativePath.replace('\\', '/').trim()
        require(normalized.isNotBlank() && !normalized.startsWith('/') && !normalized.contains('\u0000') &&
            !normalized.split('/').any { it.isBlank() || it == "." || it == ".." } && !normalized.contains(':')) {
            "مسار الملف غير صالح لسجل النسخ السابقة."
        }
        val root = projectRoot.canonicalFile
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val history = File(root, ".nawa-history/$hash").canonicalFile
        require(history.path.startsWith(File(root, ".nawa-history").canonicalPath + File.separator)) {
            "مسار سجل النسخ السابقة غير آمن."
        }
        return history
    }

    private fun trim(directory: File) {
        val files = directory.listFiles()
            ?.filter { it.isFile && REVISION_ID.matches(it.name) }
            ?.sortedByDescending { it.name.substringBefore('-').toLongOrNull() ?: it.lastModified() }
            .orEmpty()
        files.drop(maxRevisionsPerFile).forEach { it.delete() }
    }

    companion object {
        private val REVISION_ID = Regex("[0-9]{10,16}-[a-f0-9]{8}\\.snap")
    }
}
