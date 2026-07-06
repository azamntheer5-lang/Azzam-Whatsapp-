package com.example.receiptscanner.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.receiptscanner.R
import com.example.receiptscanner.ai.AiEngine
import com.example.receiptscanner.databinding.ActivitySettingsBinding
import com.example.receiptscanner.storage.ApiKeyStore

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings_title)
        binding.settingsToolbar.setNavigationOnClickListener { finish() }

        setupEngineSpinner()
        loadCurrentValues()

        binding.buttonSaveSettings.setOnClickListener { saveValues() }
    }

    private fun setupEngineSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            AiEngine.entries.map { it.displayName }
        )
        binding.spinnerEngine.adapter = adapter
        val current = ApiKeyStore.getActiveEngine(this)
        binding.spinnerEngine.setSelection(AiEngine.entries.indexOf(current))
    }

    private fun loadCurrentValues() {
        binding.editClaudeKey.setText(ApiKeyStore.getKey(this, AiEngine.CLAUDE).orEmpty())
        binding.editGeminiKey.setText(ApiKeyStore.getKey(this, AiEngine.GEMINI).orEmpty())
        binding.editGroqKey.setText(ApiKeyStore.getKey(this, AiEngine.GROQ).orEmpty())
        binding.editHuggingFaceKey.setText(ApiKeyStore.getKey(this, AiEngine.HUGGINGFACE).orEmpty())
    }

    private fun saveValues() {
        saveOrClear(AiEngine.CLAUDE, binding.editClaudeKey.text.toString())
        saveOrClear(AiEngine.GEMINI, binding.editGeminiKey.text.toString())
        saveOrClear(AiEngine.GROQ, binding.editGroqKey.text.toString())
        saveOrClear(AiEngine.HUGGINGFACE, binding.editHuggingFaceKey.text.toString())

        val selectedEngine = AiEngine.entries[binding.spinnerEngine.selectedItemPosition]
        ApiKeyStore.setActiveEngine(this, selectedEngine)

        finish()
    }

    private fun saveOrClear(engine: AiEngine, value: String) {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            ApiKeyStore.clearKey(this, engine)
        } else {
            ApiKeyStore.setKey(this, engine, trimmed)
        }
    }
}
