package com.example.receiptscanner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.receiptscanner.MainActivity
import com.example.receiptscanner.R
import com.example.receiptscanner.data.AppDatabase
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

        private suspend fun renderWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            // قراءة طازجة مباشرة من Room - الودجت قد يُحدَّث والتطبيق مغلق تماماً
            val dao = AppDatabase.getInstance(context).receiptDao()
            val list = dao.getAllOnce()
            val total = list.sumOf { it.amount ?: 0.0 }
            val count = list.size
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
