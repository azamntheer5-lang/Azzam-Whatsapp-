package com.example.receiptscanner.parser

/** خط الدفاع الأخير: يُستخدم عندما لا يتطابق النص مع أي بنك معروف. */
class GenericParser : BankReceiptParser {
    override val bankId = "generic"

    override fun matches(text: String) = true

    override fun parse(text: String): ParsedFields? {
        val amount = Regex("""([\d,]+\.\d{2})\s*(?:SAR|ر\.س|ريال)?""")
            .find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        val date = Regex("""(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""").find(text)?.value

        if (amount == null && date == null) return null
        return ParsedFields(senderName = null, recipientName = null, amount = amount, date = date)
    }
}
