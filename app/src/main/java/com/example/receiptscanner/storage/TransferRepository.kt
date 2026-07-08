package com.example.receiptscanner.storage

import android.content.Context
import com.example.receiptscanner.analytics.NameGroup
import com.example.receiptscanner.analytics.QualitySummary
import com.example.receiptscanner.analytics.StatementCalculator
import com.example.receiptscanner.data.AppDatabase
import com.example.receiptscanner.data.ReceiptDao
import com.example.receiptscanner.model.Transfer
import com.example.receiptscanner.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * الآن مبني فوق Room (مشفَّرة بـ SQLCipher) بدل ملف JSON واحد. القراءة/الكتابة
 * الفعلية تذهب مباشرة لـ Room من أي مكان (خدمة، Worker، الواجهة) عبر
 * AppDatabase.getInstance() - لا تعتمد على استدعاء ensureStarted() أولاً.
 * الـ StateFlow هنا مجرد "كاش" مريح تراقبه الواجهة فقط، تُغذّى من Flow حقيقي
 * لقاعدة البيانات عبر ensureStarted(). كل منطق التجميع/الحساب مفوَّض إلى
 * StatementCalculator (منطق بحت قابل للاختبار الآلي بمعزل عن هذا الكلاس).
 */
object TransferRepository {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers.asStateFlow()

    private var started = false
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun dao(context: Context): ReceiptDao =
        AppDatabase.getInstance(context).receiptDao()

    /** استدعِها مرة عند بدء الواجهة (مثلاً من ViewModel) لتفعيل التحديث التلقائي التفاعلي للقائمة. */
    @Synchronized
    fun ensureStarted(context: Context) {
        if (started) return
        started = true
        val database = dao(context.applicationContext)
        repoScope.launch {
            migrateFromOldJsonIfNeeded(context.applicationContext, database)
            database.observeAll().collect { list -> _transfers.value = list }
        }
    }

    suspend fun addTransfer(context: Context, transfer: Transfer) {
        dao(context).insert(transfer)
        WidgetUpdater.notifyDataChanged(context)
    }

    suspend fun updateTransfer(context: Context, transfer: Transfer) {
        dao(context).update(transfer)
        WidgetUpdater.notifyDataChanged(context)
    }

    suspend fun deleteTransfer(context: Context, id: String) {
        dao(context).deleteById(id)
        WidgetUpdater.notifyDataChanged(context)
    }

    /** يستبدل كل البيانات - يُستخدم عند استعادة نسخة احتياطية. */
    suspend fun replaceAll(context: Context, newList: List<Transfer>) {
        val database = dao(context)
        database.deleteAll()
        database.insertAll(newList)
        WidgetUpdater.notifyDataChanged(context)
    }

    fun totalAmount(): Double = _transfers.value.sumOf { it.amount ?: 0.0 }

    fun rawJsonForBackup(): String = json.encodeToString(_transfers.value)

    fun parseBackupJson(text: String): List<Transfer> = json.decodeFromString(text)

    fun monthlyTotals(): List<Pair<String, Double>> =
        StatementCalculator.monthlyTotals(_transfers.value)

    fun topCounterparties(limit: Int = 5): List<Pair<String, Double>> =
        StatementCalculator.topCounterparties(_transfers.value, limit)

    fun groupedByName(): List<NameGroup> =
        StatementCalculator.groupedByName(_transfers.value)

    /** ملخّص جودة البيانات (كم سجلاً يحتاج مراجعة، كم تم التحقق منه يدوياً) لعرضه بشاشة التحليلات. */
    fun qualitySummary(): QualitySummary =
        StatementCalculator.qualitySummary(_transfers.value)

    /** هجرة لمرة واحدة من الإصدار القديم (ملف JSON مشفَّر واحد) - تُتجاهل إن كانت القاعدة تحوي بيانات بالفعل. */
    private suspend fun migrateFromOldJsonIfNeeded(context: Context, database: ReceiptDao) {
        if (database.count() > 0) return
        val oldFile = File(context.filesDir, "transfers.enc")
        val decrypted = SecureStorage.readAndDecrypt(oldFile) ?: return
        val oldTransfers = runCatching { json.decodeFromString<List<Transfer>>(decrypted) }.getOrNull()
        if (!oldTransfers.isNullOrEmpty()) {
            database.insertAll(oldTransfers)
        }
        oldFile.delete()
    }
}
