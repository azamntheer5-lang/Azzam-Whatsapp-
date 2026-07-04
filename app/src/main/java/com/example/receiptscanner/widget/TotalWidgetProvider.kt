package com.example.receiptscanner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.receiptscanner.MainActivity
import com.example.receiptscanner.R
import com.example.receiptscanner.storage.TransferRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** ودجت بسيط بالشاشة الرئيسية يعرض الإجمالي الحالي وعدد الإيصالات، ويفتح التطبيق عند الضغط. */
class TotalWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                renderWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            CoroutineScope(Dispatchers.IO).launch {
                renderWidgets(context, manager, ids)
            }
        }

        private fun renderWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            // تحميل طازج من الملف المشفَّر مباشرة - الودجت قد يُحدَّث والتطبيق مغلق تماماً
            TransferRepository.forceReload(context)
            val total = TransferRepository.totalAmount()
            val count = TransferRepository.transfers.value.size
            val formatter = NumberFormat.getCurrencyInstance(Locale("ar", "SA"))

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_total)
                views.setTextViewText(R.id.widgetTotal, formatter.format(total))
                views.setTextViewText(
                    R.id.widgetSubtitle,
                    context.getString(R.string.widget_subtitle_format, count)
                )

                val launchIntent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

                manager.updateAppWidget(id, views)
            }
        }
    }
}
