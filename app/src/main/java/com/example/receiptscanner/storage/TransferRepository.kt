package com.example.receiptscanner.storage

import android.content.Context
import com.example.receiptscanner.model.Transfer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * مخزن بيانات بسيط: قائمة التحويلات محفوظة كملف JSON واحد مشفّر بالكامل
 * (عبر SecureStorage). لا حاجة لقاعدة بيانات حقيقية بحجم بيانات شخصي كهذا.
 */
object TransferRepository {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers.asStateFlow()

    private var loaded = false

    private fun dataFile(context: Context): File =
        File(context.filesDir, "transfers.enc")

    @Synchronized
    fun loadIfNeeded(context: Context) {
        if (loaded) return
        loaded = true
        val decrypted = SecureStorage.readAndDecrypt(dataFile(context))
        if (decrypted != null) {
            runCatching {
                _transfers.value = json.decodeFromString<List<Transfer>>(decrypted)
            }
        }
    }

    @Synchronized
    fun addTransfer(context: Context, transfer: Transfer) {
        loadIfNeeded(context)
        val updated = _transfers.value + transfer
        _transfers.value = updated
        persist(context, updated)
    }

    @Synchronized
    fun deleteTransfer(context: Context, id: String) {
        loadIfNeeded(context)
        val updated = _transfers.value.filterNot { it.id == id }
        _transfers.value = updated
        persist(context, updated)
    }

    private fun persist(context: Context, list: List<Transfer>) {
        val text = json.encodeToString(list)
        SecureStorage.encryptAndWrite(dataFile(context), text)
    }

    fun totalAmount(): Double = _transfers.value.sumOf { it.amount ?: 0.0 }
}
