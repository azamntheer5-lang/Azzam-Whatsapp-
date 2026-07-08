package com.example.receiptscanner.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.receiptscanner.R
import com.example.receiptscanner.databinding.ActivityAnalyticsBinding
import com.example.receiptscanner.storage.TransferRepository
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.coroutines.launch

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalyticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.analyticsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.analytics_title)
        binding.analyticsToolbar.setNavigationOnClickListener { finish() }

        TransferRepository.ensureStarted(applicationContext)

        // نراقب التدفق نفسه (وليس قراءة لمرة واحدة) لأن تحميل Room يحدث بالخلفية
        // بشكل غير متزامن - هذا يضمن رسم الرسوم البيانية فور وصول البيانات فعلياً
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TransferRepository.transfers.collect {
                    renderCharts()
                }
            }
        }
    }

    private fun renderCharts() {
        val monthly = TransferRepository.monthlyTotals()
        val top = TransferRepository.topCounterparties()

        if (monthly.isEmpty() && top.isEmpty()) {
            binding.textNoData.visibility = android.view.View.VISIBLE
            binding.qualityCard.visibility = android.view.View.GONE
            binding.barChart.visibility = android.view.View.GONE
            binding.pieChart.visibility = android.view.View.GONE
            return
        }

        renderQualitySummary()
        renderBarChart(monthly)
        renderPieChart(top)
    }

    private fun renderQualitySummary() {
        val summary = TransferRepository.qualitySummary()
        binding.textQualitySummary.text = getString(
            R.string.analytics_quality_format,
            summary.total, summary.highConfidence, summary.needsReview, summary.manuallyVerified
        )
    }

    private fun renderBarChart(monthly: List<Pair<String, Double>>) {
        val entries = monthly.mapIndexed { index, (_, total) -> BarEntry(index.toFloat(), total.toFloat()) }
        val dataSet = BarDataSet(entries, getString(R.string.analytics_monthly_title)).apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 10f
        }

        binding.barChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(monthly.map { it.first })
                granularity = 1f
                setDrawGridLines(false)
            }
            axisRight.isEnabled = false
            animateY(600)
            invalidate()
        }
    }

    private fun renderPieChart(top: List<Pair<String, Double>>) {
        val entries = top.map { (name, total) -> PieEntry(total.toFloat(), name) }
        val dataSet = PieDataSet(entries, "").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 11f
        }

        binding.pieChart.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            legend.isEnabled = true
            setUsePercentValues(true)
            setEntryLabelTextSize(10f)
            animateY(600)
            invalidate()
        }
    }
}
