package com.example.receiptscanner.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractionValidatorTest {

    @Test
    fun `completenessScore is zero for null fields`() {
        assertEquals(0, ExtractionValidator.completenessScore(null))
    }

    @Test
    fun `completenessScore is four for a fully valid result`() {
        val fields = ParsedFields(
            senderName = "خالد",
            recipientName = "سعيد",
            amount = 250.0,
            date = "2026-03-01"
        )
        assertEquals(4, ExtractionValidator.completenessScore(fields))
    }

    @Test
    fun `completenessScore rejects non-positive amounts`() {
        val zero = ParsedFields(null, "سعيد", 0.0, null)
        val negative = ParsedFields(null, "سعيد", -50.0, null)
        // فقط الاسم صالح في الحالتين (نقطة واحدة)، لا المبلغ
        assertEquals(1, ExtractionValidator.completenessScore(zero))
        assertEquals(1, ExtractionValidator.completenessScore(negative))
    }

    @Test
    fun `completenessScore rejects unrealistically large amounts`() {
        val fields = ParsedFields(null, "سعيد", 50_000_000.0, null)
        assertEquals(1, ExtractionValidator.completenessScore(fields))
    }

    @Test
    fun `completenessScore rejects junk placeholder names`() {
        val fields = ParsedFields(senderName = "N/A", recipientName = "فارغ", amount = 100.0, date = null)
        // المبلغ فقط صالح؛ كلا الاسمين "junk"
        assertEquals(1, ExtractionValidator.completenessScore(fields))
    }

    @Test
    fun `completenessScore rejects a name made only of digits`() {
        val fields = ParsedFields(senderName = "12345", recipientName = null, amount = 100.0, date = null)
        assertEquals(1, ExtractionValidator.completenessScore(fields))
    }

    @Test
    fun `completenessScore rejects malformed dates`() {
        val fields = ParsedFields(null, "سعيد", 100.0, date = "01/03/2026")
        // التاريخ يجب أن يكون بصيغة YYYY-MM-DD تحديداً
        assertEquals(2, ExtractionValidator.completenessScore(fields))
    }

    @Test
    fun `isGoodEnough requires both a plausible amount and at least one name`() {
        val amountOnly = ParsedFields(null, null, 100.0, null)
        val nameOnly = ParsedFields(null, "سعيد", null, null)
        val both = ParsedFields(null, "سعيد", 100.0, null)

        assertFalse(ExtractionValidator.isGoodEnough(amountOnly))
        assertFalse(ExtractionValidator.isGoodEnough(nameOnly))
        assertTrue(ExtractionValidator.isGoodEnough(both))
    }
}
