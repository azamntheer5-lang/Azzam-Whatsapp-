package com.example.receiptscanner.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiPromptTest {

    @Test
    fun `parseJsonResponse reads a clean valid JSON object`() {
        val json = """{"sender_name":"محمد","receiver_name":"علي","amount":"250.50","date":"2026-02-15"}"""

        val result = AiPrompt.parseJsonResponse(json)

        assertEquals("محمد", result?.senderName)
        assertEquals("علي", result?.recipientName)
        assertEquals(250.50, result?.amount!!, 0.001)
        assertEquals("2026-02-15", result.date)
    }

    @Test
    fun `parseJsonResponse strips markdown code fences some models add`() {
        val json = "```json\n{\"sender_name\":\"\",\"receiver_name\":\"سلمى\",\"amount\":\"75\",\"date\":\"\"}\n```"

        val result = AiPrompt.parseJsonResponse(json)

        assertEquals("سلمى", result?.recipientName)
        assertEquals(75.0, result?.amount!!, 0.001)
    }

    @Test
    fun `parseJsonResponse treats empty string fields as null`() {
        val json = """{"sender_name":"","receiver_name":"","amount":"","date":""}"""

        val result = AiPrompt.parseJsonResponse(json)

        assertNull(result) // كل الحقول فارغة فعلياً = لا فائدة من النتيجة
    }

    @Test
    fun `parseJsonResponse handles amounts with thousands separators`() {
        val json = """{"sender_name":"","receiver_name":"فهد","amount":"1,250.75","date":""}"""

        val result = AiPrompt.parseJsonResponse(json)

        assertEquals(1250.75, result?.amount!!, 0.001)
    }

    @Test
    fun `parseJsonResponse returns null for malformed JSON instead of throwing`() {
        val notJson = "عذراً، لم أتمكن من قراءة الإيصال بوضوح."

        val result = AiPrompt.parseJsonResponse(notJson)

        assertNull(result)
    }

    @Test
    fun `parseJsonResponse returns null for an empty string`() {
        assertNull(AiPrompt.parseJsonResponse(""))
    }
}
