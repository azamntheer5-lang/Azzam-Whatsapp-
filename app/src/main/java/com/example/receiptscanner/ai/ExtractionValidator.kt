package com.example.receiptscanner.ai


/**
 * يقيّم "اكتمال" نتيجة استخراج (0-4) بدل قبولها كما هي. يُستخدم لمقارنة
 * نتائج عدة محركات واختيار الأفضل، وللتأكد أن المبلغ/التاريخ منطقيان
 * (وليسا مجرد نص عشوائي اجتازه الـ JSON بالخطأ).
 */
object ExtractionValidator {

    private val plausibleDateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun completenessScore(fields: ParsedFields?): Int {
        if (fields == null) return 0
        var score = 0
        if (isPlausibleAmount(fields.amount)) score++
        if (!fields.senderName.isNullOrBlank() && isPlausibleName(fields.senderName)) score++
        if (!fields.recipientName.isNullOrBlank() && isPlausibleName(fields.recipientName)) score++
        if (fields.date != null && plausibleDateRegex.matches(fields.date.trim())) score++
        return score
    }

    /** نعتبر النتيجة "كافية" ولا داعي لتجربة محرك آخر إذا فيها مبلغ صالح + اسم واحد على الأقل. */
    fun isGoodEnough(fields: ParsedFields?): Boolean {
        if (fields == null) return false
        val hasName = (!fields.senderName.isNullOrBlank() && isPlausibleName(fields.senderName)) ||
            (!fields.recipientName.isNullOrBlank() && isPlausibleName(fields.recipientName))
        return isPlausibleAmount(fields.amount) && hasName
    }

    private fun isPlausibleAmount(amount: Double?): Boolean =
        amount != null && amount > 0 && amount < 10_000_000

    /** يرفض نتائج مثل "فارغ" أو "N/A" أو نصوصاً رقمية بالكامل التي أحياناً يُرجعها النموذج بالخطأ. */
    private fun isPlausibleName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.length < 2) return false
        val junkValues = setOf("فارغ", "غير معروف", "n/a", "na", "null", "unknown", "-")
        if (trimmed.lowercase() in junkValues) return false
        if (trimmed.all { it.isDigit() || it.isWhitespace() }) return false
        return true
    }
}
