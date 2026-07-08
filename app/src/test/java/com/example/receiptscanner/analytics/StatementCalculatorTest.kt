package com.example.receiptscanner.analytics

import com.example.receiptscanner.model.Transfer
import org.junit.Assert.assertEquals
import org.junit.Test

class StatementCalculatorTest {

    private fun transfer(
        id: String = "t1",
        sender: String? = null,
        recipient: String? = null,
        amount: Double? = 100.0,
        date: String? = "2026-01-01",
        processedAt: Long = 0L,
        confidence: Float = 0.9f
    ) = Transfer(
        id = id,
        senderName = sender,
        recipientName = recipient,
        amount = amount,
        date = date,
        bankId = "test",
        confidence = confidence,
        sourceFileName = "f.jpg",
        processedAt = processedAt
    )

    @Test
    fun `groupedByName merges a person appearing as both sender and recipient`() {
        val transfers = listOf(
            transfer(id = "1", recipient = "أحمد", amount = 30.0),
            transfer(id = "2", sender = "أحمد", amount = 50.0)
        )

        val groups = StatementCalculator.groupedByName(transfers)

        assertEquals(1, groups.size)
        assertEquals("أحمد", groups[0].name)
        assertEquals(80.0, groups[0].total, 0.001)
        assertEquals(2, groups[0].transfers.size)
    }

    @Test
    fun `groupedByName prefers recipient name when both are present on the same record`() {
        val transfers = listOf(transfer(sender = "محمد", recipient = "سالم", amount = 40.0))

        val groups = StatementCalculator.groupedByName(transfers)

        assertEquals(1, groups.size)
        assertEquals("سالم", groups[0].name)
    }

    @Test
    fun `groupedByName ignores records with no usable name`() {
        val transfers = listOf(transfer(sender = null, recipient = null, amount = 40.0))

        assertEquals(0, StatementCalculator.groupedByName(transfers).size)
    }

    @Test
    fun `groupedByName sorts groups by total descending`() {
        val transfers = listOf(
            transfer(id = "1", recipient = "صغير", amount = 10.0),
            transfer(id = "2", recipient = "كبير", amount = 500.0)
        )

        val groups = StatementCalculator.groupedByName(transfers)

        assertEquals("كبير", groups[0].name)
        assertEquals("صغير", groups[1].name)
    }

    @Test
    fun `topCounterparties respects the limit parameter`() {
        val transfers = (1..10).map { i -> transfer(id = "$i", recipient = "شخص$i", amount = i.toDouble()) }

        val top = StatementCalculator.topCounterparties(transfers, limit = 3)

        assertEquals(3, top.size)
    }

    @Test
    fun `monthlyTotals sums only transfers that have an amount`() {
        val transfers = listOf(
            transfer(id = "1", amount = 100.0, processedAt = 0L),
            transfer(id = "2", amount = null, processedAt = 0L),
            transfer(id = "3", amount = 50.0, processedAt = 0L)
        )

        val totals = StatementCalculator.monthlyTotals(transfers)

        assertEquals(1, totals.size)
        assertEquals(150.0, totals[0].second, 0.001)
    }

    @Test
    fun `qualitySummary counts low-confidence records as needing review`() {
        val transfers = listOf(
            transfer(id = "1", confidence = 0.95f),
            transfer(id = "2", confidence = 0.2f),
            transfer(id = "3", confidence = 1.0f)
        )

        val summary = StatementCalculator.qualitySummary(transfers)

        assertEquals(3, summary.total)
        assertEquals(1, summary.needsReview)
        assertEquals(1, summary.manuallyVerified)
    }
}
