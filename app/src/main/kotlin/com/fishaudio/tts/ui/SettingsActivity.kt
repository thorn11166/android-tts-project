package com.fishaudio.tts.ui

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fishaudio.tts.api.FishAudioApiService
import com.fishaudio.tts.data.PreferencesManager
import com.fishaudio.tts.databinding.ActivitySettingsBinding
import com.fishaudio.tts.model.Prosody
import com.fishaudio.tts.model.TtsRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            binding.etApiKey.setText(prefs.apiKey.first())
            binding.tvSelectedVoice.text = prefs.selectedVoiceName.first().ifEmpty { getString(com.fishaudio.tts.R.string.no_voice_selected) }
            binding.sliderSpeed.value = prefs.speed.first()
            binding.sliderVolume.value = prefs.volume.first().toFloat()

            val latency = prefs.latency.first()
            binding.switchLatency.isChecked = latency == "balanced"

            val model = prefs.model.first()
            when (model) {
                "s1" -> binding.radioS1.isChecked = true
                "s2-pro" -> binding.radioS2Pro.isChecked = true
                else -> binding.radioS2.isChecked = true
            }
        }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener { saveSettings() }

        binding.btnSelectVoice.setOnClickListener {
            startActivityForResult(
                Intent(this, VoicePickerActivity::class.java),
                REQUEST_VOICE_PICKER
            )
        }

        binding.btnTest.setOnClickListener { testSynthesis() }
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            prefs.setApiKey(binding.etApiKey.text.toString().trim())
            prefs.setLatency(if (binding.switchLatency.isChecked) "balanced" else "normal")
            prefs.setSpeed(binding.sliderSpeed.value)
            prefs.setVolume(binding.sliderVolume.value.toInt())

            val model = when {
                binding.radioS1.isChecked -> "s1"
                binding.radioS2Pro.isChecked -> "s2-pro"
                else -> "s2"
            }
            prefs.setModel(model)

            Toast.makeText(this@SettingsActivity, com.fishaudio.tts.R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun testSynthesis() {
        lifecycleScope.launch {
            val apiKey = prefs.apiKey.first()
            val voiceId = prefs.selectedVoiceId.first()

            if (apiKey.isEmpty()) {
                Toast.makeText(this@SettingsActivity, com.fishaudio.tts.R.string.error_no_api_key, Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (voiceId.isEmpty()) {
                Toast.makeText(this@SettingsActivity, com.fishaudio.tts.R.string.error_no_voice, Toast.LENGTH_SHORT).show()
                return@launch
            }

            binding.btnTest.isEnabled = false

            val ttsRequest = TtsRequest(
                text = getString(com.fishaudio.tts.R.string.test_phrase),
                referenceId = voiceId,
                latency = prefs.latency.first(),
                prosody = Prosody(speed = prefs.speed.first(), volume = prefs.volume.first())
            )

            val result = FishAudioApiService.synthesize(apiKey, ttsRequest) { _, _, _ -> }
            binding.btnTest.isEnabled = true

            if (result.isSuccess) {
                Toast.makeText(this@SettingsActivity, com.fishaudio.tts.R.string.test_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@SettingsActivity, getString(com.fishaudio.tts.R.string.test_failed, result.exceptionOrNull()?.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VOICE_PICKER && resultCode == RESULT_OK) {
            val voiceName = data?.getStringExtra(VoicePickerActivity.EXTRA_VOICE_NAME) ?: return
            binding.tvSelectedVoice.text = voiceName
        }
    }

    companion object {
        private const val REQUEST_VOICE_PICKER = 1001
    }
}
