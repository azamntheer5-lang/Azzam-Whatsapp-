package com.example.receiptscanner.ai

/** نتيجة استخراج موحَّدة يعيدها أي محرك ذكاء اصطناعي. */
data class ParsedFields(
    val senderName: String?,
    val recipientName: String?,
    val amount: Double?,
    val date: String?
)
