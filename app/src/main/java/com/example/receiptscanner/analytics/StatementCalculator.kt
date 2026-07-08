package com.example.receiptscanner.analytics

import com.example.receiptscanner.model.Transfer
import java.text.SimpleDateFormat
import java.util.Locale

data class NameGroup(val name: String, val total: Double, val transfers: List<Transfer>)

/**
 * منطق حسابي بحت (pure functions) بلا أي اعتماد على Context أو Room أو أي
 * حالة (state) - مفصول عمداً عن TransferRepository ليكون قابلاً للاختبار
 * الآلي (unit tests) مباشرة بإعطائه قائمة بيانات ثابتة، دون الحاجة لتشغيل
 * قاعدة بيانات حقيقية أو محاكي أندرويد.
 */
object StatementCalculator {

    fun monthlyTotals(transfers: List<Transfer>): List<Pair<String, Double>> {
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        return transfers
            .filter { it.amount != null }
            .groupBy { monthFormat.format(it.processedAt) }
            .mapValues { entry -> entry.value.sumOf { it.amount ?: 0.0 } }
            .toList()
            .sortedBy { it.first }
    }

    fun topCounterparties(transfers: List<Transfer>, limit: Int = 5): List<Pair<String, Double>> {
        return transfers
            .mapNotNull { t ->
                val name = t.recipientName?.takeIf { it.isNotBlank() }
                    ?: t.senderName?.takeIf { it.isNotBlank() }
                if (name != null && t.amount != null) name to t.amount else null
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)
    }

    /** يجمّع كل التحويلات حسب اسم الجهة (سواء ظهرت كمرسِل أو مستلِم عبر إيصالات مختلفة). */
    fun groupedByName(transfers: List<Transfer>): List<NameGroup> {
        val byName = mutableMapOf<String, MutableList<Transfer>>()
        transfers.forEach { t ->
            val name = t.recipientName?.takeIf { it.isNotBlank() }
                ?: t.senderName?.takeIf { it.isNotBlank() }
            if (name != null) {
                byName.getOrPut(name) { mutableListOf() }.add(t)
            }
        }
        return byName.map { (name, list) ->
            NameGroup(
                name = name,
                total = list.sumOf { it.amount ?: 0.0 },
                transfers = list.sortedByDescending { it.processedAt }
            )
        }.sortedByDescending { it.total }
    }

    /** إحصاءات جودة البيانات: كم سجلاً موثوق به مقابل محتاج مراجعة يدوية. */
    fun qualitySummary(transfers: List<Transfer>): QualitySummary {
        val reviewed = transfers.count { it.confidence >= 0.85f }
        val needsReview = transfers.count { it.confidence < 0.5f }
        val manuallyVerified = transfers.count { it.confidence >= 1.0f }
        return QualitySummary(
            total = transfers.size,
            highConfidence = reviewed,
            needsReview = needsReview,
            manuallyVerified = manuallyVerified
        )
    }
}

data class QualitySummary(
    val total: Int,
    val highConfidence: Int,
    val needsReview: Int,
    val manuallyVerified: Int
)
