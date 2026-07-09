package com.example.receiptscanner.ui

import android.content.Intent
import android.net.Uri
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
        setupKeyLinks()

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
        binding.editClaudeKeys.setText(joinedKeys(AiEngine.CLAUDE))
        binding.editGeminiKeys.setText(joinedKeys(AiEngine.GEMINI))
        binding.editGroqKeys.setText(joinedKeys(AiEngine.GROQ))
        binding.editHuggingFaceKeys.setText(joinedKeys(AiEngine.HUGGINGFACE))
    }

    private fun joinedKeys(engine: AiEngine): String =
        ApiKeyStore.getKeys(this, engine).joinToString("\n") { it.key }

    private fun setupKeyLinks() {
        binding.linkClaudeKey.setOnClickListener { openUrl("https://console.anthropic.com/settings/keys") }
        binding.linkGeminiKey.setOnClickListener { openUrl("https://aistudio.google.com/apikey") }
        binding.linkGroqKey.setOnClickListener { openUrl("https://console.groq.com/keys") }
        binding.linkHuggingFaceKey.setOnClickListener { openUrl("https://huggingface.co/settings/tokens") }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // لا يوجد متصفح متاح - نادر جداً، نتجاهل بهدوء
        }
    }

    private fun saveValues() {
        ApiKeyStore.replaceKeys(this, AiEngine.CLAUDE, splitLines(binding.editClaudeKeys.text.toString()))
        ApiKeyStore.replaceKeys(this, AiEngine.GEMINI, splitLines(binding.editGeminiKeys.text.toString()))
        ApiKeyStore.replaceKeys(this, AiEngine.GROQ, splitLines(binding.editGroqKeys.text.toString()))
        ApiKeyStore.replaceKeys(this, AiEngine.HUGGINGFACE, splitLines(binding.editHuggingFaceKeys.text.toString()))

        val selectedEngine = AiEngine.entries[binding.spinnerEngine.selectedItemPosition]
        ApiKeyStore.setActiveEngine(this, selectedEngine)

        finish()
    }

    private fun splitLines(text: String): List<String> =
        text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
}
