package com.nawa.studio.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NawaRevisionStoreTest {
    @Test
    fun capturesChangedContentAndRestoresPreviousRevision() {
        val root = createTempDir(prefix = "nawa-revision-test-")
        try {
            val file = File(root, "src/main.nawa").apply { parentFile.mkdirs(); writeText("version one") }
            val store = NawaRevisionStore(maxRevisionsPerFile = 5)
            store.snapshot(root, "src/main.nawa", file, "version two")
            file.writeText("version two")
            assertTrue(store.list(root, "src/main.nawa").isEmpty().not())
            val revision = store.list(root, "src/main.nawa").single()
            assertEquals("version one", store.read(root, "src/main.nawa", revision.id))
            store.snapshot(root, "src/main.nawa", file, "version two")
            assertEquals(1, store.list(root, "src/main.nawa").size) // identical saves do not create extra snapshots
            assertFalse(File(root, ".nawa-history").walkTopDown().any { it.isFile && it.name.endsWith(".kt") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun boundsTheNumberOfRevisionsForEachFile() {
        val root = createTempDir(prefix = "nawa-revision-retention-test-")
        try {
            val file = File(root, "src/main.nawa").apply { parentFile.mkdirs(); writeText("v0") }
            val store = NawaRevisionStore(maxRevisionsPerFile = 3)
            repeat(6) { index ->
                store.snapshot(root, "src/main.nawa", file, "v${index + 1}")
                file.writeText("v${index + 1}")
            }
            assertEquals(3, store.list(root, "src/main.nawa").size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsTraversalAndForeignRevisionIds() {
        val root = createTempDir(prefix = "nawa-revision-safety-test-")
        try {
            val store = NawaRevisionStore()
            var traversalRejected = false
            try { store.list(root, "../outside.txt") } catch (_: IllegalArgumentException) { traversalRejected = true }
            assertTrue(traversalRejected)
            var idRejected = false
            try { store.read(root, "src/main.nawa", "../../secret") } catch (_: IllegalArgumentException) { idRejected = true }
            assertTrue(idRejected)
        } finally {
            root.deleteRecursively()
        }
    }
}
