package com.example.receiptscanner.storage

import android.content.Context
import com.example.receiptscanner.model.Transfer
import com.example.receiptscanner.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

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

    /** يجبر إعادة التحميل من القرص - مفيد بعد استعادة نسخة احتياطية. */
    @Synchronized
    fun forceReload(context: Context) {
        loaded = false
        loadIfNeeded(context)
    }

    @Synchronized
    fun addTransfer(context: Context, transfer: Transfer) {
        loadIfNeeded(context)
        val updated = _transfers.value + transfer
        _transfers.value = updated
        persist(context, updated)
    }

    @Synchronized
    fun updateTransfer(context: Context, updated: Transfer) {
        loadIfNeeded(context)
        val newList = _transfers.value.map { if (it.id == updated.id) updated else it }
        _transfers.value = newList
        persist(context, newList)
    }

    @Synchronized
    fun deleteTransfer(context: Context, id: String) {
        loadIfNeeded(context)
        val updated = _transfers.value.filterNot { it.id == id }
        _transfers.value = updated
        persist(context, updated)
    }

    /** يستبدل القائمة كاملة دفعة واحدة - يُستخدم عند استعادة نسخة احتياطية. */
    @Synchronized
    fun replaceAll(context: Context, newList: List<Transfer>) {
        loaded = true
        _transfers.value = newList
        persist(context, newList)
    }

    private fun persist(context: Context, list: List<Transfer>) {
        val text = json.encodeToString(list)
        SecureStorage.encryptAndWrite(dataFile(context), text)
        WidgetUpdater.notifyDataChanged(context)
    }

    fun totalAmount(): Double = _transfers.value.sumOf { it.amount ?: 0.0 }

    fun rawJsonForBackup(): String = json.encodeToString(_transfers.value)

    fun parseBackupJson(text: String): List<Transfer> = json.decodeFromString(text)

    /** يجمّع المبالغ حسب الشهر (yyyy-MM) لعرضها في رسم بياني، مرتّبة زمنياً. */
    fun monthlyTotals(): List<Pair<String, Double>> {
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val grouped = _transfers.value
            .filter { it.amount != null }
            .groupBy { monthFormat.format(it.processedAt) }
            .mapValues { entry -> entry.value.sumOf { it.amount ?: 0.0 } }
        return grouped.toList().sortedBy { it.first }
    }

    /** أكثر 5 مستلمين/مرسلين تكراراً، لعرضهم في رسم دائري. */
    fun topCounterparties(limit: Int = 5): List<Pair<String, Double>> {
        return _transfers.value
            .mapNotNull { t ->
                val name = t.recipientName?.takeIf { it.isNotBlank() }
                    ?: t.senderName?.takeIf { it.isNotBlank() }
                if (name != null && t.amount != null) name to t.amount else null
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)
    }
}
