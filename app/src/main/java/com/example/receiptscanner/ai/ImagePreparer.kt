package com.example.receiptscanner.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.receiptscanner.ocr.PdfHelper
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Gemini وGroq وHugging Face (بعكس Claude) لا تدعم PDF مباشرة بهذا التكامل -
 * فنحوّل أول صفحة من الـ PDF إلى صورة JPEG قبل الإرسال.
 */
object ImagePreparer {
    fun jpegBytesFor(file: File, isPdf: Boolean): ByteArray {
        val bitmap: Bitmap = if (isPdf) {
            PdfHelper.renderPages(file).firstOrNull()
                ?: throw IllegalStateException("تعذّر تحويل الـ PDF إلى صورة")
        } else {
            BitmapFactory.decodeFile(file.absolutePath)
                ?: throw IllegalStateException("تعذّرت قراءة ملف الصورة")
        }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }
}
