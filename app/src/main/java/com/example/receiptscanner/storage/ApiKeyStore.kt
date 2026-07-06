package com.example.receiptscanner.storage

import android.content.Context
import com.example.receiptscanner.ai.AiEngine
import java.io.File

/** يخزّن مفاتيح المحركات الأربعة + المحرك النشط، كل قيمة مشفَّرة في ملفها الخاص عبر SecureStorage. */
object ApiKeyStore {

    private fun keyFile(context: Context, engine: AiEngine) =
        File(context.filesDir, "api_key_${engine.name.lowercase()}.enc")

    private fun activeEngineFile(context: Context) =
        File(context.filesDir, "active_engine.enc")

    fun getKey(context: Context, engine: AiEngine): String? =
        SecureStorage.readAndDecrypt(keyFile(context, engine))?.takeIf { it.isNotBlank() }

    fun setKey(context: Context, engine: AiEngine, apiKey: String) {
        SecureStorage.encryptAndWrite(keyFile(context, engine), apiKey.trim())
    }

    fun clearKey(context: Context, engine: AiEngine) {
        keyFile(context, engine).delete()
    }

    fun getActiveEngine(context: Context): AiEngine =
        AiEngine.fromNameOrDefault(SecureStorage.readAndDecrypt(activeEngineFile(context)))

    fun setActiveEngine(context: Context, engine: AiEngine) {
        SecureStorage.encryptAndWrite(activeEngineFile(context), engine.name)
    }

    /** المفتاح الفعّال حالياً (للمحرك النشط)، أو null إن لم يُدخَل بعد. */
    fun getActiveKey(context: Context): String? =
        getKey(context, getActiveEngine(context))
}
