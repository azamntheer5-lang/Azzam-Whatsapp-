package com.example.receiptscanner.processing

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileFilterTest {

    private val createdFiles = mutableListOf<File>()

    private fun fileOfSize(name: String, sizeBytes: Int): File {
        val file = File.createTempFile("test_", "_$name")
        file.writeBytes(ByteArray(sizeBytes))
        createdFiles += file
        return file
    }

    @After
    fun cleanup() {
        createdFiles.forEach { it.delete() }
    }

    @Test
    fun `pdf under 1MB is accepted`() {
        val file = fileOfSize("receipt.pdf", 500 * 1024)
        assertTrue(FileFilter.isCandidateReceipt(file))
    }

    @Test
    fun `pdf over 1MB is rejected`() {
        val file = fileOfSize("receipt.pdf", 2 * 1024 * 1024)
        assertFalse(FileFilter.isCandidateReceipt(file))
    }

    @Test
    fun `image under 5MB is accepted`() {
        val file = fileOfSize("receipt.jpg", 3 * 1024 * 1024)
        assertTrue(FileFilter.isCandidateReceipt(file))
    }

    @Test
    fun `image over 5MB is rejected`() {
        val file = fileOfSize("receipt.jpg", 6 * 1024 * 1024)
        assertFalse(FileFilter.isCandidateReceipt(file))
    }

    @Test
    fun `non-image non-pdf extension is always rejected regardless of size`() {
        val file = fileOfSize("notes.txt", 100)
        assertFalse(FileFilter.isCandidateReceipt(file))
    }

    @Test
    fun `empty zero-byte file is rejected`() {
        val file = fileOfSize("empty.jpg", 0)
        assertFalse(FileFilter.isCandidateReceipt(file))
    }

    @Test
    fun `isPdf correctly identifies extension case-insensitively`() {
        assertTrue(FileFilter.isPdf(File("receipt.PDF")))
        assertTrue(FileFilter.isPdf(File("receipt.pdf")))
        assertFalse(FileFilter.isPdf(File("receipt.jpg")))
    }
}
