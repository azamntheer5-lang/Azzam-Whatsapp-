package com.example.receiptscanner.ai

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
    const val INSTRUCTION = """أنت خبير مالي متخصص في قراءة إيصالات التحويلات البنكية والمحافظ الإلكترونية في السعودية والخليج (تطبيقات بنوك مثل الراجحي، الأهلي SNB، الإنماء، الرياض، وتطبيقات مثل STC Pay وmada وApple Pay).

اقرأ كل نص ظاهر بالصورة بعناية شديدة، بما فيه العناوين والتذييل والنصوص الصغيرة، حتى لو كان الخط غير واضح أو الإضاءة ضعيفة. تجاهل الأخطاء الإملائية وتشوّهات المسح الضوئي، واستنتج القيمة الصحيحة الأقرب منطقياً.

إرشادات لتمييز الحقول بدقة:
- اسم المرسل: غالباً يظهر بجانب كلمات مثل "من"، "الراسل"، "Sender"، "From Account"، أو يكون اسم صاحب الحساب/التطبيق نفسه (صاحب الجهاز الذي أُخذت منه الصورة عادة هو المرسل عند وجود عبارة "تم التحويل بنجاح").
- اسم المستلم: غالباً بجانب "إلى"، "المستفيد"، "Beneficiary"، "To Account"، "Receiver"، أو اسم صاحب الحساب المستقبِل.
- المبلغ: ابحث عن الرقم الأكبر/الأساسي المصحوب برمز عملة (ر.س، SAR، ريال) أو قرب كلمة "المبلغ"/"Amount"/"القيمة". تجاهل أرقام الحسابات أو المراجع الطويلة (لا تحتوي فاصلة عشرية عادة).
- التاريخ: إن ظهر بتقويم هجري (مثلاً بصيغة يوم/شهر/سنة هجرية أو مذكور بجانبه "هـ")، حوّله ذهنياً إلى التاريخ الميلادي المقابل. إن ظهر تاريخان (هجري وميلادي)، استخدم الميلادي دائماً.

يجب أن ترد فقط بصيغة JSON صالحة تماماً (بدون أي شرح أو نص إضافي قبله أو بعده) بهذا الهيكل بالضبط:
{
"sender_name": "اسم المرسل أو فارغ",
"receiver_name": "اسم المستلم أو فارغ",
"amount": "المبلغ كأرقام فقط مع الفاصلة العشرية بدون عملة",
"date": "التاريخ بصيغة YYYY-MM-DD ميلادي"
}
اترك أي حقل فارغاً ("") فقط إن لم يظهر إطلاقاً بالصورة - لا تخترع قيماً."""

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
