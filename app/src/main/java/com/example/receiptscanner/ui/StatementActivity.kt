package com.example.receiptscanner.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.receiptscanner.databinding.ActivityStatementBinding
import com.example.receiptscanner.analytics.NameGroup
import com.example.receiptscanner.storage.TransferRepository
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** كشف حساب: يجمّع كل التحويلات حسب اسم الجهة (مرسِلاً أو مستلِماً) مع الإجمالي. */
class StatementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatementBinding
    private lateinit var adapter: StatementAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.statementToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.statementToolbar.setNavigationOnClickListener { finish() }

        adapter = StatementAdapter(onGroupTap = { showGroupDetails(it) })
        binding.recyclerStatement.layoutManager = LinearLayoutManager(this)
        binding.recyclerStatement.adapter = adapter

        TransferRepository.ensureStarted(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TransferRepository.transfers.collect {
                    val groups = TransferRepository.groupedByName()
                    adapter.submitList(groups)
                    binding.textStatementEmpty.visibility =
                        if (groups.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showGroupDetails(group: NameGroup) {
        val formatter = NumberFormat.getCurrencyInstance(Locale("ar", "SA"))
        val details = group.transfers.joinToString("\n\n") { t ->
            "${t.date ?: "—"}  •  ${t.amount?.let { formatter.format(it) } ?: "—"}  •  ${t.bankId}"
        }
        AlertDialog.Builder(this)
            .setTitle("${group.name} — ${formatter.format(group.total)}")
            .setMessage(details)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
