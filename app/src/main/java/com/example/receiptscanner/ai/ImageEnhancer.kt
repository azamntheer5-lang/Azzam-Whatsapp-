package com.example.receiptscanner.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * تحسين بسيط للتباين قبل إرسال الصورة لأي محرك ذكاء اصطناعي - يفيد خصوصاً
 * صور الإيصالات الملتقطة بالكاميرا (إضاءة ضعيفة/انعكاس) وليس فقط لقطات
 * الشاشة النظيفة أصلاً. لا يغيّر الأبعاد، فقط يقوّي التباين قليلاً.
 */
object ImageEnhancer {
    fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val contrast = 1.18f
        val brightness = -12f
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }
}
