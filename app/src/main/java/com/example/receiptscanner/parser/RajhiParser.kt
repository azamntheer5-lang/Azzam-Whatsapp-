package com.example.receiptscanner.parser

class RajhiParser : BankReceiptParser {
    override val bankId = "al_rajhi"

    override fun matches(text: String) =
        text.contains("الراجحي") || text.contains("Rajhi", ignoreCase = true)

    override fun parse(text: String): ParsedFields? {
        val amount = Regex("""(?:Amount|المبلغ)\D{0,10}([\d,]+\.?\d*)""")
            .find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?: return null // بدون مبلغ موثوق، لا تُرجع بيانات مضللة

        val date = Regex("""(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""").find(text)?.value
        val recipient = Regex("""(?:Beneficiary|المستفيد)\D{0,5}([^\n]{3,40})""")
            .find(text)?.groupValues?.get(1)?.trim()

        return ParsedFields(senderName = null, recipientName = recipient, amount = amount, date = date)
    }
}
