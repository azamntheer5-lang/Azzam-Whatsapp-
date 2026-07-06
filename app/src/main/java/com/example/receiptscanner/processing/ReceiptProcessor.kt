package com.example.receiptscanner.processing

import android.content.Context
import android.graphics.BitmapFactory
import com.example.receiptscanner.ai.AiParserFactory
import com.example.receiptscanner.model.Transfer
import com.example.receiptscanner.ocr.MlKitOcrHelper
import com.example.receiptscanner.ocr.PdfHelper
import com.example.receiptscanner.parser.ParsedFields
import com.example.receiptscanner.parser.ParserRegistry
import com.example.receiptscanner.storage.ApiKeyStore
import com.example.receiptscanner.storage.ProcessedFilesTracker
import com.example.receiptscanner.storage.TransferRepository
import java.io.File
import java.util.UUID

/**
 * خط المعالجة الكامل لملف واحد:
 * فلترة -> OCR محلي + Regex (مرجع احتياطي مجاني) -> محرك الذكاء الاصطناعي
 * النشط (Claude/Gemini/Groq/HuggingFace حسب اختيار المستخدم بالإعدادات) كمصدر
 * أساسي للدقة -> حفظ في Room.
 *
 * لماذا الذكاء الاصطناعي أولاً وليس Regex؟ نص OCR من صور واتساب غالباً فوضوي
 * (أسطر متداخلة، عربي غير مقروء بواسطة ML Kit) فلا يمكن الاعتماد عليه لحل
 * مشكلة الحقول الفارغة/المشوَّهة جذرياً - قراءة الصورة مباشرة عبر نموذج ذكاء
 * اصطناعي أدق بكثير ولا تتأثر بفوضى تحليل النص.
 */
object ReceiptProcessor {

    private val registry = ParserRegistry()

    suspend fun processFile(context: Context, file: File) {
        if (!file.exists()) return

        val key = "${file.absolutePath}_${file.lastModified()}_${file.length()}"
        if (ProcessedFilesTracker.isProcessed(context, key)) return
        if (!FileFilter.isCandidateReceipt(file)) return

        // علّم كمعالَج فوراً لمنع سباق (race) بين المسح الفوري والدوري لنفس الملف
        ProcessedFilesTracker.markProcessed(context, key)

        val ocrText = try {
            extractText(file)
        } catch (e: Exception) {
            "" // فشل القراءة المحلية - قد يظل الذكاء الاصطناعي قادراً على القراءة من الصورة مباشرة
        }

        // مرجع احتياطي محلي مجاني (Regex على نص OCR) - غالباً غير مكتمل مع إيصالات عربية
        var bankId = "unknown"
        var regexFields: ParsedFields? = null
        if (ocrText.isNotBlank()) {
            val extraction = registry.extract(ocrText)
            bankId = extraction?.first ?: "unknown"
            regexFields = extraction?.second
        }

        // المصدر الأساسي: محرك الذكاء الاصطناعي النشط يقرأ الصورة/PDF مباشرة
        var fields = regexFields
        var usedAi = false
        val activeEngine = ApiKeyStore.getActiveEngine(context)
        val apiKey = ApiKeyStore.getKey(context, activeEngine)

        if (apiKey != null) {
            val aiFields = try {
                AiParserFactory.create(activeEngine).extract(file, apiKey, FileFilter.isPdf(file))
            } catch (e: Exception) {
                null
            }
            if (aiFields != null && hasAnyValue(aiFields)) {
                fields = aiFields // نعتمد نتيجة الذكاء الاصطناعي كاملة كمرجع أساسي، لا دمج جزئي
                bankId = "ai_${activeEngine.name.lowercase()}"
                usedAi = true
            }
        }

        val transfer = Transfer(
            id = UUID.randomUUID().toString(),
            senderName = fields?.senderName,
            recipientName = fields?.recipientName,
            amount = fields?.amount,
            date = fields?.date,
            bankId = bankId,
            confidence = calculateConfidence(fields, usedAi),
            sourceFileName = file.name,
            processedAt = System.currentTimeMillis(),
            rawText = ocrText.take(500)
        )

        // احفظ إذا استخرجنا شيئاً مفيداً على الأقل (مبلغ أو تاريخ)
        if (transfer.amount != null || transfer.date != null) {
            TransferRepository.addTransfer(context, transfer)
        }
    }

    private fun hasAnyValue(f: ParsedFields): Boolean =
        !f.senderName.isNullOrBlank() || !f.recipientName.isNullOrBlank() ||
            f.amount != null || !f.date.isNullOrBlank()

    private fun calculateConfidence(fields: ParsedFields?, usedAi: Boolean): Float {
        if (fields == null) return 0.1f
        val fieldsPresent = listOf(
            !fields.senderName.isNullOrBlank(),
            !fields.recipientName.isNullOrBlank(),
            fields.amount != null,
            !fields.date.isNullOrBlank()
        ).count { it }
        return when {
            usedAi && fieldsPresent >= 3 -> 0.95f
            usedAi && fieldsPresent == 2 -> 0.7f
            usedAi -> 0.4f
            fieldsPresent >= 3 -> 0.7f
            fields.amount != null -> 0.5f
            else -> 0.2f
        }
    }

    private suspend fun extractText(file: File): String {
        return if (FileFilter.isPdf(file)) {
            val pages = PdfHelper.renderPages(file)
            val texts = mutableListOf<String>()
            for (page in pages) {
                texts.add(MlKitOcrHelper.recognize(page))
            }
            texts.joinToString("\n")
        } else {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return ""
            MlKitOcrHelper.recognize(bitmap)
        }
    }
}
