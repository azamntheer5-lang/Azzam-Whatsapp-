package com.example.receiptscanner.ai

import com.example.receiptscanner.parser.ParsedFields
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** واجهة موحّدة لكل محركات الذكاء الاصطناعي - كل محرك يقرأ الصورة/PDF مباشرة ويعيد الحقول الأربعة. */
interface AiReceiptParser {
    val engine: AiEngine
    suspend fun extract(file: File, apiKey: String, isPdf: Boolean): ParsedFields?
}

/**
 * الـ Prompt الموحَّد المستخدَم من كل المحركات (بالضبط كما طُلب) - صارم على
 * هيكل JSON محدد لحل مشكلة الحقول الفارغة/المشوَّهة نهائياً، بدل الاعتماد
 * على تحليل نص OCR فوضوي بـ Regex.
 */
object AiPrompt {
    const val INSTRUCTION = """أنت خبير مالي. استخرج البيانات التالية من نص إيصال الحوالة الفوضوي. تجاهل الأخطاء الإملائية. يجب أن ترد فقط بصيغة JSON صالحة تماماً بهذا الهيكل:
{
"sender_name": "اسم المرسل أو فارغ",
"receiver_name": "اسم المستلم أو فارغ",
"amount": "المبلغ كأرقام فقط مع الفاصلة العشرية بدون عملة",
"date": "التاريخ بصيغة YYYY-MM-DD"
}"""

    /** يحوّل استجابة JSON بالهيكل أعلاه (sender_name/receiver_name/amount/date) إلى ParsedFields الداخلي. */
    fun parseJsonResponse(jsonText: String): ParsedFields? {
        return try {
            val cleaned = jsonText.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val obj = Json.parseToJsonElement(cleaned).jsonObject

            val sender = obj["sender_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val receiver = obj["receiver_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val amountText = obj["amount"]?.jsonPrimitive?.contentOrNull
            val amount = amountText?.replace(",", "")
                ?.filter { it.isDigit() || it == '.' }
                ?.toDoubleOrNull()
            val date = obj["date"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

            if (sender == null && receiver == null && amount == null && date == null) return null
            ParsedFields(senderName = sender, recipientName = receiver, amount = amount, date = date)
        } catch (e: Exception) {
            null
        }
    }
}
