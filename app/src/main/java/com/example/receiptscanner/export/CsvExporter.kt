package com.example.receiptscanner.export

import android.content.Context
import com.example.receiptscanner.model.Transfer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object CsvExporter {

    /** يصدّر ملف CSV يفتح مباشرة في Excel/Google Sheets، مع BOM لضمان ظهور العربي صحيحاً. */
    fun export(context: Context, transfers: List<Transfer>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val file = File(dir, "transfers_$timestamp.csv")

        val sb = StringBuilder()
        sb.append("التاريخ,المرسل,المستلم,المبلغ,البنك\n")
        transfers.forEach { t ->
            sb.append(t.date.orEmpty()).append(",")
            sb.append(escapeCsv(t.senderName.orEmpty())).append(",")
            sb.append(escapeCsv(t.recipientName.orEmpty())).append(",")
            sb.append(t.amount?.toString().orEmpty()).append(",")
            sb.append(t.bankId).append("\n")
        }
        val total = transfers.sumOf { it.amount ?: 0.0 }
        sb.append(",,الإجمالي:,").append(total).append(",\n")

        file.outputStream().use { out ->
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // UTF-8 BOM لدعم Excel
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        return file
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }
}
