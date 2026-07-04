package com.example.receiptscanner.processing

import android.content.Context
import android.graphics.BitmapFactory
import com.example.receiptscanner.model.Transfer
import com.example.receiptscanner.ocr.CloudExtractor
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
 * فلترة -> OCR محلي -> Regex -> (عند الحاجة فقط) Claude API كطبقة ثانية -> حفظ.
 *
 * قابل للاستدعاء بأمان من مصادر متعددة (الخدمة الفورية، المسح الدوري،
 * زر "مسح الآن") لأن كل خطوة idempotent (يتحقق من التكرار بنفسه).
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
            "" // فشل القراءة (ملف تالف مثلاً) - أكمل، فقد يظل استخراج السحابة ممكناً
        }

        var bankId = "unknown"
        var fields: ParsedFields? = null
        if (ocrText.isNotBlank()) {
            val extraction = registry.extract(ocrText)
            bankId = extraction?.first ?: "unknown"
            fields = extraction?.second
        }

        // نحتاج مساعدة السحابة إذا لم نحصل على مبلغ، أو حصلنا على مبلغ لكن
        // بلا أي اسم مرسل/مستلم (الحالة الأكثر شيوعاً مع الإيصالات العربية
        // التي لا يقرأها ML Kit بشكل صحيح)
        val needsCloudHelp = fields?.amount == null ||
            (fields.recipientName.isNullOrBlank() && fields.senderName.isNullOrBlank())

        var usedCloud = false
        if (needsCloudHelp) {
            val apiKey = ApiKeyStore.getKey(context)
            if (apiKey != null) {
                val cloudFields = try {
                    CloudExtractor.extract(file, apiKey, FileFilter.isPdf(file))
                } catch (e: Exception) {
                    null
                }
                if (cloudFields != null) {
                    fields = mergeFields(fields, cloudFields)
                    if (bankId == "unknown") bankId = "cloud_ai"
                    usedCloud = true
                }
            }
        }

        val transfer = Transfer(
            id = UUID.randomUUID().toString(),
            senderName = fields?.senderName,
            recipientName = fields?.recipientName,
            amount = fields?.amount,
            date = fields?.date,
            bankId = bankId,
            confidence = when {
                fields?.amount != null && usedCloud -> 0.9f
                fields?.amount != null -> 0.7f
                else -> 0.2f
            },
            sourceFileName = file.name,
            processedAt = System.currentTimeMillis(),
            rawText = ocrText.take(500)
        )

        // احفظ فقط إذا استخرجنا شيئاً مفيداً على الأقل (مبلغ أو تاريخ)
        if (transfer.amount != null || transfer.date != null) {
            TransferRepository.addTransfer(context, transfer)
        }
    }

    /** يفضّل قيم Regex المحلية عند توفرها (أسرع/بلا تكلفة)، ويملأ الفراغات من نتيجة السحابة. */
    private fun mergeFields(original: ParsedFields?, cloud: ParsedFields): ParsedFields {
        return ParsedFields(
            senderName = original?.senderName?.takeIf { it.isNotBlank() } ?: cloud.senderName,
            recipientName = original?.recipientName?.takeIf { it.isNotBlank() } ?: cloud.recipientName,
            amount = original?.amount ?: cloud.amount,
            date = original?.date?.takeIf { it.isNotBlank() } ?: cloud.date
        )
    }

    private suspend fun extractText(file: File): String {
        return if (FileFilter.isPdf(file)) {
            val pages = PdfHelper.renderPages(file)
            // ملاحظة: joinToString ليست inline، فلا تدعم استدعاء دالة suspend
            // داخل lambda الخاص بها مباشرة - لهذا نجمع النصوص بحلقة عادية أولاً
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
