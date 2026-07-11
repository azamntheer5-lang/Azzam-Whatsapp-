package com.example.receiptscanner.storage

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * تتبّع بطبقتين مقصود:
 * 1) قفل مؤقت بالذاكرة فقط (يُنسى عند إغلاق التطبيق) يمنع معالجة نفس الملف
 *    مرتين بنفس اللحظة (تعارض بين الخدمة الفورية وWorker الدوري).
 * 2) قائمة دائمة (SharedPreferences) لكن تُضاف إليها الملفات فقط بعد نجاح
 *    حفظ سجل فعلي - وليس بمجرد "محاولة" المعالجة.
 *
 * لماذا هذا التغيير: لو عولج ملف قبل تفعيل أي مفتاح API صالح، كانت النسخة
 * السابقة تُدرجه بالقائمة الدائمة فوراً حتى بلا أي بيانات مستخرَجة، فيبقى
 * "مُهملاً" للأبد حتى بعد إضافة مفتاح صحيح لاحقاً - وهذا يفسّر ظهور قائمة
 * فارغة رغم وجود إيصالات فعلية. الآن: الملف يُعاد تلقائياً بالمسح القادم
 * (الدوري أو اليدوي) طالما لم يُحفَظ له سجل ناجح بعد.
 */
object ProcessedFilesTracker {
    private const val PREFS = "processed_files"
    private const val KEY_SET = "keys"
    private const val MAX_KEPT = 5000

    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /** حاول الحصول على قفل معالجة هذا الملف الآن؛ false إن كان مصدر آخر يعالجه بهذه اللحظة تحديداً. */
    fun tryAcquire(key: String): Boolean = inFlight.add(key)

    fun release(key: String) {
        inFlight.remove(key)
    }

    fun isPermanentlyProcessed(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SET, emptySet())?.contains(key) == true
    }

    @Synchronized
    fun markPermanentlyProcessed(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(KEY_SET, emptySet()) ?: emptySet()).toMutableSet()
        current.add(key)

        val trimmed = if (current.size > MAX_KEPT) {
            current.toList().takeLast(MAX_KEPT).toSet()
        } else {
            current
        }
        prefs.edit().putStringSet(KEY_SET, trimmed).apply()
    }

    /** لمسح كامل تاريخ المعالجة يدوياً (زر "إعادة فحص الكل" بالإعدادات) - يجعل كل الملفات قابلة لإعادة المحاولة. */
    @Synchronized
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
