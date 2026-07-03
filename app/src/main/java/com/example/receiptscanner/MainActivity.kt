package com.example.receiptscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.receiptscanner.databinding.ActivityMainBinding
import com.example.receiptscanner.storage.ApiKeyStore
import com.example.receiptscanner.ui.MainViewModel
import com.example.receiptscanner.ui.TransferAdapter
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: TransferAdapter

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* لا حاجة لمعالجة خاصة - الإشعار يعمل بأولوية منخفضة حتى بدونها */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // امنع لقطات الشاشة والظهور في قائمة التطبيقات الأخيرة - بيانات مالية حساسة
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = TransferAdapter(onLongPressDelete = { viewModel.deleteTransfer(it.id) })
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.buttonGrantPermission.setOnClickListener { requestManageStoragePermission() }
        binding.fabScanNow.setOnClickListener { triggerManualScan() }
        binding.buttonSettings.setOnClickListener { showApiKeyDialog() }

        observeTransfers()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        val granted = Environment.isExternalStorageManager()
        binding.permissionContainer.visibility = if (granted) View.GONE else View.VISIBLE
        binding.mainContainer.visibility = if (granted) View.VISIBLE else View.GONE

        if (granted) {
            requestNotificationPermissionIfNeeded()
            startServices()
        }
    }

    private fun requestManageStoragePermission() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startServices() {
        val serviceIntent = Intent(this, ReceiptWatcherService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        PeriodicScanWorker.schedule(this)
    }

    private fun triggerManualScan() {
        val request = OneTimeWorkRequestBuilder<PeriodicScanWorker>().build()
        WorkManager.getInstance(this).enqueue(request)
    }

    /** يعرض حوار لإدخال/تحديث/حذف مفتاح Claude API (المستخدم اختيارياً لقراءة العربية). */
    private fun showApiKeyDialog() {
        val currentKey = ApiKeyStore.getKey(this)
        val input = EditText(this).apply {
            hint = "sk-ant-..."
            inputType = InputType.TYPE_CLASS_TEXT
            if (currentKey != null) setText(currentKey)
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.api_key_dialog_title)
            .setMessage(R.string.api_key_dialog_desc)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotBlank()) ApiKeyStore.setKey(this, key)
            }
            .setNeutralButton(R.string.remove_key) { _, _ ->
                ApiKeyStore.clearKey(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun observeTransfers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.transfers.collect { list ->
                    val sorted = list.sortedByDescending { it.processedAt }
                    adapter.submitList(sorted)
                    binding.textEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE

                    val formatter = NumberFormat.getCurrencyInstance(Locale("ar", "SA"))
                    binding.textTotal.text = formatter.format(viewModel.totalAmount())
                }
            }
        }
    }
}
