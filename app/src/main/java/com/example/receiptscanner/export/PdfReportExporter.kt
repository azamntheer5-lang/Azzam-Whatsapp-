package com.example.receiptscanner.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.receiptscanner.model.Transfer
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** تقرير PDF بسيط باستخدام PdfDocument الأصلية في أندرويد - بلا أي مكتبة خارجية. */
object PdfReportExporter {

    private const val PAGE_WIDTH = 595  // A4 تقريباً بدقة 72
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun export(context: Context, transfers: List<Transfer>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val file = File(dir, "receipt_report_$timestamp.pdf")

        val document = PdfDocument()
        val formatter = NumberFormat.getCurrencyInstance(Locale("ar", "SA"))
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
        val headerPaint = Paint().apply { textSize = 11f; isFakeBoldText = true; color = Color.DKGRAY }
        val textPaint = Paint().apply { textSize = 10f; color = Color.BLACK }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        canvas.drawText("تقرير التحويلات البنكية", MARGIN, y, titlePaint)
        y += 20
        val total = transfers.sumOf { it.amount ?: 0.0 }
        canvas.drawText(
            "الإجمالي: ${formatter.format(total)}   |   عدد السجلات: ${transfers.size}",
            MARGIN, y, headerPaint
        )
        y += 25
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 15

        canvas.drawText("التاريخ", MARGIN, y, headerPaint)
        canvas.drawText("المرسل/المستلم", MARGIN + 90, y, headerPaint)
        canvas.drawText("المبلغ", MARGIN + 260, y, headerPaint)
        canvas.drawText("البنك", MARGIN + 340, y, headerPaint)
        y += 15
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 15

        for (t in transfers) {
            if (y > PAGE_HEIGHT - MARGIN - 20) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
            }
            canvas.drawText(t.date ?: "—", MARGIN, y, textPaint)
            canvas.drawText((t.recipientName ?: t.senderName ?: "—").take(20), MARGIN + 90, y, textPaint)
            canvas.drawText(t.amount?.let { "%.2f".format(it) } ?: "—", MARGIN + 260, y, textPaint)
            canvas.drawText(t.bankId, MARGIN + 340, y, textPaint)
            y += 18
        }

        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }
}
