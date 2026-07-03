package com.example.receiptscanner.parser

class StcPayParser : BankReceiptParser {
    override val bankId = "stc_pay"

    override fun matches(text: String) =
        text.contains("STC Pay", ignoreCase = true) || text.contains("stc pay", ignoreCase = true)

    override fun parse(text: String): ParsedFields? {
        val amount = Regex("""(?:SAR|ر\.س|Amount)\D{0,6}([\d,]+\.?\d*)""")
            .find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?: return null

        val date = Regex("""(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""").find(text)?.value

        return ParsedFields(senderName = null, recipientName = null, amount = amount, date = date)
    }
}
