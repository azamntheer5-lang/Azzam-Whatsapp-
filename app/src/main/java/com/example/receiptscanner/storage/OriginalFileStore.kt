package com.example.receiptscanner.storage

import android.content.Context
import java.io.File

/**
 * يحتفظ بنسخة من كل ملف إيصال تمت معالجته بنجاح، داخل تخزين التطبيق الخاص
 * (معزول عن باقي التطبيقات، لكن غير مشفَّر بشكل منفصل - راجع ملاحظة الأمان
 * بالـ README). هذا يضمن بقاء "المعاينة" متاحة حتى لو حذف المستخدم لاحقاً
 * الصورة الأصلية من واتساب.
 */
object OriginalFileStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "originals").apply { mkdirs() }

    fun save(context: Context, source: File, transferId: String, isPdf: Boolean): String? {
        return try {
            val ext = if (isPdf) "pdf" else "jpg"
            val dest = File(dir(context), "$transferId.$ext")
            source.copyTo(dest, overwrite = true)
            dest.absolutePath
        } catch (e: Exception) {
            null // فشل نسخ النسخة الاحتياطية لا يجب أن يوقف حفظ بيانات الإيصال نفسها
        }
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    fun isPdf(path: String?): Boolean = path?.endsWith(".pdf", ignoreCase = true) == true
}
