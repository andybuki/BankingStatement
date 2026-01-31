package com.bankwise.app.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for PDF processing related classes.
 * Note: PdfProcessor is an expect/actual class, so platform-specific functionality
 * cannot be tested in common code. These tests focus on FilePickerResult and
 * related data structures.
 */
class PdfProcessingTest {

    // ===== FilePickerResult tests =====

    @Test
    fun testFilePickerResult_Creation() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val result = FilePickerResult(
            fileName = "statement.pdf",
            bytes = bytes
        )

        assertEquals("statement.pdf", result.fileName)
        assertEquals(5, result.bytes.size)
    }

    @Test
    fun testFilePickerResult_EmptyBytes() {
        val result = FilePickerResult(
            fileName = "empty.pdf",
            bytes = byteArrayOf()
        )

        assertEquals(0, result.bytes.size)
    }

    @Test
    fun testFilePickerResult_LargeFile() {
        val largeBytes = ByteArray(1024 * 1024) { it.toByte() }  // 1MB
        val result = FilePickerResult(
            fileName = "large_statement.pdf",
            bytes = largeBytes
        )

        assertEquals(1024 * 1024, result.bytes.size)
    }

    @Test
    fun testFilePickerResult_Equality_SameContent() {
        val bytes = byteArrayOf(1, 2, 3)
        val result1 = FilePickerResult("test.pdf", bytes.copyOf())
        val result2 = FilePickerResult("test.pdf", bytes.copyOf())

        assertEquals(result1, result2)
    }

    @Test
    fun testFilePickerResult_Equality_DifferentFileName() {
        val bytes = byteArrayOf(1, 2, 3)
        val result1 = FilePickerResult("test1.pdf", bytes)
        val result2 = FilePickerResult("test2.pdf", bytes)

        assertNotEquals(result1, result2)
    }

    @Test
    fun testFilePickerResult_Equality_DifferentBytes() {
        val result1 = FilePickerResult("test.pdf", byteArrayOf(1, 2, 3))
        val result2 = FilePickerResult("test.pdf", byteArrayOf(4, 5, 6))

        assertNotEquals(result1, result2)
    }

    @Test
    fun testFilePickerResult_Equality_SameInstance() {
        val result = FilePickerResult("test.pdf", byteArrayOf(1, 2, 3))

        assertEquals(result, result)
    }

    @Test
    fun testFilePickerResult_Equality_NullComparison() {
        val result = FilePickerResult("test.pdf", byteArrayOf(1, 2, 3))

        assertFalse(result.equals(null))
    }

    @Test
    fun testFilePickerResult_Equality_DifferentType() {
        val result = FilePickerResult("test.pdf", byteArrayOf(1, 2, 3))

        assertFalse(result.equals("test.pdf"))
    }

    @Test
    fun testFilePickerResult_HashCode_SameContent() {
        val bytes = byteArrayOf(1, 2, 3)
        val result1 = FilePickerResult("test.pdf", bytes.copyOf())
        val result2 = FilePickerResult("test.pdf", bytes.copyOf())

        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun testFilePickerResult_HashCode_DifferentContent() {
        val result1 = FilePickerResult("test1.pdf", byteArrayOf(1, 2, 3))
        val result2 = FilePickerResult("test2.pdf", byteArrayOf(4, 5, 6))

        // Different content should (usually) have different hash codes
        // Note: Hash collisions are possible but unlikely for this test
        assertNotEquals(result1.hashCode(), result2.hashCode())
    }

    // ===== File extension tests =====

    @Test
    fun testFilePickerResult_PDFExtension() {
        val result = FilePickerResult("statement.pdf", byteArrayOf())

        assertTrue(result.fileName.endsWith(".pdf"))
    }

    @Test
    fun testFilePickerResult_CSVExtension() {
        val result = FilePickerResult("export.csv", byteArrayOf())

        assertTrue(result.fileName.endsWith(".csv"))
    }

    @Test
    fun testFilePickerResult_UppercaseExtension() {
        val result = FilePickerResult("STATEMENT.PDF", byteArrayOf())

        assertTrue(result.fileName.uppercase().endsWith(".PDF"))
    }

    @Test
    fun testFilePickerResult_NoExtension() {
        val result = FilePickerResult("statement", byteArrayOf())

        assertFalse(result.fileName.contains("."))
    }

    @Test
    fun testFilePickerResult_MultipleDotsInName() {
        val result = FilePickerResult("bank.statement.2024.pdf", byteArrayOf())

        assertTrue(result.fileName.endsWith(".pdf"))
        assertEquals(3, result.fileName.count { it == '.' })
    }

    // ===== File name validation tests =====

    @Test
    fun testFilePickerResult_SpecialCharactersInName() {
        val result = FilePickerResult("statement (1).pdf", byteArrayOf())

        assertTrue(result.fileName.contains("("))
        assertTrue(result.fileName.contains(")"))
    }

    @Test
    fun testFilePickerResult_GermanCharactersInName() {
        val result = FilePickerResult("Kontoauszug_März.pdf", byteArrayOf())

        assertTrue(result.fileName.contains("ä"))
    }

    @Test
    fun testFilePickerResult_SpacesInName() {
        val result = FilePickerResult("my bank statement.pdf", byteArrayOf())

        assertTrue(result.fileName.contains(" "))
    }

    @Test
    fun testFilePickerResult_PathLikeName() {
        // Sometimes file names might look like paths
        val result = FilePickerResult("Downloads/statement.pdf", byteArrayOf())

        assertTrue(result.fileName.contains("/"))
    }

    // ===== PDF magic bytes tests =====

    @Test
    fun testPdfMagicBytes_ValidPdfHeader() {
        // PDF files start with "%PDF-"
        val pdfMagicBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)  // %PDF-

        val result = FilePickerResult("test.pdf", pdfMagicBytes)

        // Check that bytes start with PDF signature
        assertTrue(result.bytes.size >= 5)
        assertEquals(0x25.toByte(), result.bytes[0])  // %
        assertEquals(0x50.toByte(), result.bytes[1])  // P
        assertEquals(0x44.toByte(), result.bytes[2])  // D
        assertEquals(0x46.toByte(), result.bytes[3])  // F
        assertEquals(0x2D.toByte(), result.bytes[4])  // -
    }

    @Test
    fun testPdfMagicBytes_NotPdf() {
        // A CSV file doesn't start with PDF magic bytes
        val csvContent = "date,amount,description\n2024-01-15,-50.00,Test".encodeToByteArray()

        val result = FilePickerResult("test.csv", csvContent)

        // Should not have PDF magic bytes
        assertTrue(result.bytes.size >= 5)
        assertNotEquals(0x25.toByte(), result.bytes[0])
    }

    // ===== Byte array immutability tests =====

    @Test
    fun testFilePickerResult_ByteArrayModification() {
        val originalBytes = byteArrayOf(1, 2, 3)
        val result = FilePickerResult("test.pdf", originalBytes)

        // Modify original array
        originalBytes[0] = 99

        // Check if FilePickerResult holds reference (it does, not a copy)
        // This test documents the current behavior
        assertEquals(99, result.bytes[0])
    }

    // ===== Size tests =====

    @Test
    fun testFilePickerResult_SmallFile() {
        val bytes = byteArrayOf(1)  // 1 byte

        val result = FilePickerResult("tiny.pdf", bytes)

        assertEquals(1, result.bytes.size)
    }

    @Test
    fun testFilePickerResult_FileSize_Calculation() {
        val kilobytes = 100
        val bytes = ByteArray(kilobytes * 1024)

        val result = FilePickerResult("medium.pdf", bytes)

        assertEquals(kilobytes * 1024, result.bytes.size)
    }

    // ===== Integration with file type detection =====

    @Test
    fun testFileExtension_ForImportFileType() {
        val pdfResult = FilePickerResult("statement.pdf", byteArrayOf())
        val csvResult = FilePickerResult("export.csv", byteArrayOf())
        val xlsResult = FilePickerResult("data.xlsx", byteArrayOf())

        assertTrue(pdfResult.fileName.substringAfterLast('.') == "pdf")
        assertTrue(csvResult.fileName.substringAfterLast('.') == "csv")
        assertTrue(xlsResult.fileName.substringAfterLast('.') == "xlsx")
    }
}
