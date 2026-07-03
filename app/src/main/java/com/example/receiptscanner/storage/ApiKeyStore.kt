package com.example.receiptscanner.storage

import android.content.Context
import java.io.File

/** يخزّن مفتاح API مشفَّراً بنفس آلية SecureStorage المستخدمة لبيانات التحويلات. */
object ApiKeyStore {
    private fun keyFile(context: Context) = File(context.filesDir, "api_key.enc")

    fun getKey(context: Context): String? =
        SecureStorage.readAndDecrypt(keyFile(context))?.takeIf { it.isNotBlank() }

    fun setKey(context: Context, apiKey: String) {
        SecureStorage.encryptAndWrite(keyFile(context), apiKey.trim())
    }

    fun clearKey(context: Context) {
        keyFile(context).delete()
    }
}
