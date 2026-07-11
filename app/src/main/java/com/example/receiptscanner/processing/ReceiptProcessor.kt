package com.example.receiptscanner.processing

import android.content.Context
import com.example.receiptscanner.ai.AiEngine
import com.example.receiptscanner.ai.AiParserFactory
import com.example.receiptscanner.ai.EngineCallResult
import com.example.receiptscanner.ai.ExtractionValidator
import com.example.receiptscanner.ai.ParsedFields
import com.example.receiptscanner.model.Transfer
import com.example.receiptscanner.storage.ApiKeyStore
import com.example.receiptscanner.storage.OriginalFileStore
import com.example.receiptscanner.storage.ProcessedFilesTracker
import com.example.receiptscanner.storage.TransferRepository
import java.io.File
import java.util.UUID

/**
 * خط المعالجة الكامل لملف واحد - بالكامل عبر الذكاء الاصطناعي (لا OCR ولا
 * Regex محليين). لكل إيصال: نجرّب المحرك النشط أولاً، ثم بقية المحركات
 * المُعَدّة بالترتيب إن كانت النتيجة غير كافية. داخل كل محرك، نجرّب حتى
 * مفتاحين مختلفين لهذا الإيصال تحديداً (سرعة معقولة)، بينما عداد الفشل
 * التراكمي بـ ApiKeyStore يتذكّر عبر كل الإيصالات: أي مفتاح يفشل 5 مرات
 * متتالية (راجع ApiKeyStore.FAILURE_THRESHOLD) يُتخطّى تلقائياً بالإيصالات
 * القادمة لصالح المفتاح التالي، أو المحرك التالي إن لم يتبقَّ مفتاح صالح.
 */
object ReceiptProcessor {

    suspend fun processFile(context: Context, file: File) {
        if (!file.exists()) return

        val key = "${file.absolutePath}_${file.lastModified()}_${file.length()}"
        if (ProcessedFilesTracker.isPermanentlyProcessed(context, key)) return
        if (!ProcessedFilesTracker.tryAcquire(key)) return // مصدر آخر يعالج هذا الملف بهذه اللحظة تحديداً
        try {
            if (!FileFilter.isCandidateReceipt(file)) return

            val isPdf = FileFilter.isPdf(file)
            val result = extractViaCascade(context, file, isPdf)

            val transferId = UUID.randomUUID().toString()
            val localFilePath = OriginalFileStore.save(context, file, transferId, isPdf)

            val transfer = Transfer(
                id = transferId,
                senderName = result.fields?.senderName,
                recipientName = result.fields?.recipientName,
                amount = result.fields?.amount,
                date = result.fields?.date,
                bankId = result.engine?.let { "ai_${it.name.lowercase()}" } ?: "no_ai_configured",
                confidence = confidenceFor(result),
                sourceFileName = file.name,
                processedAt = System.currentTimeMillis(),
                localFilePath = localFilePath
            )

            if (transfer.amount != null || transfer.date != null) {
                TransferRepository.addTransfer(context, transfer)
                // فقط الآن، بعد نجاح فعلي، نمنع إعادة معالجة هذا الملف مستقبلاً
                ProcessedFilesTracker.markPermanentlyProcessed(context, key)
            } else {
                OriginalFileStore.delete(localFilePath)
                // لم يُحفَظ أي شيء (مثلاً: لا مفتاح API مُعَدّ بعد) - لا تُدرَج بالقائمة الدائمة
                // كي تُعاد تجربتها تلقائياً بالمسح القادم بمجرد توفّر مفتاح صالح
            }
        } finally {
            ProcessedFilesTracker.release(key)
        }
    }

    private data class CascadeResult(val fields: ParsedFields?, val engine: AiEngine?)

    private suspend fun extractViaCascade(context: Context, file: File, isPdf: Boolean): CascadeResult {
        val activeEngine = ApiKeyStore.getActiveEngine(context)
        val engineOrder = listOf(activeEngine) + AiEngine.entries.filter { it != activeEngine }

        var best: ParsedFields? = null
        var bestEngine: AiEngine? = null
        var bestScore = -1

        for (engine in engineOrder) {
            val (fields, succeeded) = tryEngineWithKeyRotation(context, engine, file, isPdf)
            if (succeeded) {
                val score = ExtractionValidator.completenessScore(fields)
                if (score > bestScore) {
                    best = fields; bestEngine = engine; bestScore = score
                }
                if (ExtractionValidator.isGoodEnough(fields)) break // كافٍ - لا داعي لتجربة محرك آخر
            }
        }
        return CascadeResult(best, bestEngine)
    }

    /**
     * يجرّب حتى مفتاحين مختلفين لنفس المحرك لهذا الإيصال (يمنع بطء كل عملية
     * مسح بانتظار 5 محاولات فاشلة متتالية على نفس المفتاح). كل فشل يُسجَّل
     * بشكل تراكمي في ApiKeyStore بحيث تتراكم الخمس فشلات عبر عدة إيصالات
     * زمنياً، وعندها يتخطّى nextUsableKey هذا المفتاح تلقائياً بالمستقبل.
     */
    private suspend fun tryEngineWithKeyRotation(
        context: Context,
        engine: AiEngine,
        file: File,
        isPdf: Boolean
    ): Pair<ParsedFields?, Boolean> {
        repeat(2) {
            val apiKey = ApiKeyStore.nextUsableKey(context, engine) ?: return null to false
            val result = try {
                AiParserFactory.create(engine).extract(file, apiKey, isPdf)
            } catch (e: Exception) {
                EngineCallResult(null, apiCallSucceeded = false)
            }

            if (result.apiCallSucceeded) {
                ApiKeyStore.recordSuccess(context, engine, apiKey)
                return result.fields to true
            } else {
                ApiKeyStore.recordFailure(context, engine, apiKey)
                // جرّب مفتاحاً آخر لنفس المحرك في التكرار التالي، إن وُجد
            }
        }
        return null to false
    }

    private fun confidenceFor(result: CascadeResult): Float {
        if (result.engine == null) return 0.1f // لا مفتاح API صالح إطلاقاً لأي محرك
        return when (ExtractionValidator.completenessScore(result.fields)) {
            4 -> 0.97f
            3 -> 0.85f
            2 -> 0.6f
            1 -> 0.35f
            else -> 0.15f
        }
    }
}
