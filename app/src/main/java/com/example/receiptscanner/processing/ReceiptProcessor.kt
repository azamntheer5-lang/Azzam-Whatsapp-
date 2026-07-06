package com.example.receiptscanner.processing

import android.content.Context
import com.example.receiptscanner.ai.AiEngine
import com.example.receiptscanner.ai.AiParserFactory
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
 * خط المعالجة الكامل لملف واحد - الآن بالكامل عبر الذكاء الاصطناعي، بلا أي
 * قراءة أو استخراج محلي (لا ML Kit، لا Regex): فلترة -> تسلسل عبر محركات
 * الذكاء الاصطناعي المهيَّأة (الأولوية للمحرك النشط بالإعدادات، ثم البقية
 * تلقائياً إن كانت نتيجة الأول غير كافية) -> حفظ نسخة من الملف الأصلي
 * للمعاينة لاحقاً -> حفظ بالقاعدة.
 *
 * بدون أي مفتاح API مُعَدّ، لن يُستخرَج شيء - هذا مقصود؛ لا يوجد أي مسار
 * بديل محلي بعد الآن.
 */
object ReceiptProcessor {

    suspend fun processFile(context: Context, file: File) {
        if (!file.exists()) return

        val key = "${file.absolutePath}_${file.lastModified()}_${file.length()}"
        if (ProcessedFilesTracker.isProcessed(context, key)) return
        if (!FileFilter.isCandidateReceipt(file)) return

        // علّم كمعالَج فوراً لمنع سباق (race) بين المسح الفوري والدوري لنفس الملف
        ProcessedFilesTracker.markProcessed(context, key)

        val isPdf = FileFilter.isPdf(file)
        val result = extractViaAiCascade(context, file, isPdf)

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

        // احفظ إذا استخرجنا شيئاً مفيداً على الأقل (مبلغ أو تاريخ)
        if (transfer.amount != null || transfer.date != null) {
            TransferRepository.addTransfer(context, transfer)
        } else {
            // لا فائدة من الاحتفاظ بنسخة ملف لسجل لن يُحفَظ
            OriginalFileStore.delete(localFilePath)
        }
    }

    private data class CascadeResult(val fields: ParsedFields?, val engine: AiEngine?)

    /**
     * يجرّب المحرك النشط أولاً، ثم بقية المحركات المُهيَّأة بمفاتيح بالترتيب،
     * محتفظاً بأفضل نتيجة رآها حتى الآن، ويتوقف مبكراً إذا وصل لنتيجة "كافية"
     * (مبلغ صالح + اسم واحد على الأقل) فلا داعي لاستهلاك استدعاءات إضافية.
     */
    private suspend fun extractViaAiCascade(context: Context, file: File, isPdf: Boolean): CascadeResult {
        val activeEngine = ApiKeyStore.getActiveEngine(context)
        val engineOrder = listOf(activeEngine) + AiEngine.entries.filter { it != activeEngine }

        var best: ParsedFields? = null
        var bestEngine: AiEngine? = null
        var bestScore = -1

        for (engine in engineOrder) {
            val apiKey = ApiKeyStore.getKey(context, engine) ?: continue

            val fields = try {
                AiParserFactory.create(engine).extract(file, apiKey, isPdf)
            } catch (e: Exception) {
                null
            }

            val score = ExtractionValidator.completenessScore(fields)
            if (score > bestScore) {
                best = fields
                bestEngine = engine
                bestScore = score
            }
            if (ExtractionValidator.isGoodEnough(fields)) break // كافٍ - لا داعي لتجربة محرك آخر
        }

        return CascadeResult(best, bestEngine)
    }

    private fun confidenceFor(result: CascadeResult): Float {
        if (result.engine == null) return 0.1f // لا مفتاح API مُعَدّ إطلاقاً
        return when (ExtractionValidator.completenessScore(result.fields)) {
            4 -> 0.97f
            3 -> 0.85f
            2 -> 0.6f
            1 -> 0.35f
            else -> 0.15f
        }
    }
}
