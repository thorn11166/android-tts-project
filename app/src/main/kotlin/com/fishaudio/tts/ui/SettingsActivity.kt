package com.fishaudio.tts.ui

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fishaudio.tts.R
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
        setSupportActionBar(binding.toolbar)

        prefs = PreferencesManager(this)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            binding.etApiKey.setText(prefs.apiKey.first())
            binding.tvSelectedVoice.text =
                prefs.selectedVoiceName.first().ifEmpty { getString(R.string.no_voice_selected) }

            val speed = prefs.speed.first()
            binding.sliderSpeed.value = speed
            binding.tvSpeedValue.text = "%.1f×".format(speed)

            val volume = prefs.volume.first()
            binding.sliderVolume.value = volume.toFloat()
            binding.tvVolumeValue.text = if (volume >= 0) "+$volume dB" else "$volume dB"

            binding.switchLatency.isChecked = prefs.latency.first() == "balanced"

            when (prefs.model.first()) {
                "s1" -> binding.toggleGroupModel.check(R.id.btn_model_s1)
                "s2-pro" -> binding.toggleGroupModel.check(R.id.btn_model_s2_pro)
                else -> binding.toggleGroupModel.check(R.id.btn_model_s2)
            }
        }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnVerifyKey.setOnClickListener { verifyApiKey() }

        binding.btnSelectVoice.setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(this, VoicePickerActivity::class.java),
                REQUEST_VOICE_PICKER
            )
        }

        binding.btnTest.setOnClickListener { testSynthesis() }

        binding.sliderSpeed.addOnChangeListener { _, value, _ ->
            binding.tvSpeedValue.text = "%.1f×".format(value)
        }

        binding.sliderVolume.addOnChangeListener { _, value, _ ->
            val db = value.toInt()
            binding.tvVolumeValue.text = if (db >= 0) "+$db dB" else "$db dB"
        }
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            prefs.setApiKey(binding.etApiKey.text.toString().trim())
            prefs.setLatency(if (binding.switchLatency.isChecked) "balanced" else "normal")
            prefs.setSpeed(binding.sliderSpeed.value)
            prefs.setVolume(binding.sliderVolume.value.toInt())

            val model = when (binding.toggleGroupModel.checkedButtonId) {
                R.id.btn_model_s1 -> "s1"
                R.id.btn_model_s2_pro -> "s2-pro"
                else -> "s2"
            }
            prefs.setModel(model)

            Toast.makeText(this@SettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun verifyApiKey() {
        lifecycleScope.launch {
            val apiKey = binding.etApiKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.error_no_api_key, Toast.LENGTH_SHORT).show()
                return@launch
            }
            binding.btnVerifyKey.isEnabled = false
            val result = FishAudioApiService.listVoices(apiKey, pageSize = 1)
            binding.btnVerifyKey.isEnabled = true
            if (result.isSuccess) {
                Toast.makeText(this@SettingsActivity, R.string.key_valid, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.key_invalid, result.exceptionOrNull()?.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun testSynthesis() {
        lifecycleScope.launch {
            val apiKey = prefs.apiKey.first()
            val voiceId = prefs.selectedVoiceId.first()

            if (apiKey.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.error_no_api_key, Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (voiceId.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.error_no_voice, Toast.LENGTH_SHORT).show()
                return@launch
            }

            binding.btnTest.isEnabled = false

            val ttsRequest = TtsRequest(
                text = getString(R.string.test_phrase),
                referenceId = voiceId,
                latency = prefs.latency.first(),
                prosody = Prosody(speed = prefs.speed.first(), volume = prefs.volume.first())
            )

            val result = FishAudioApiService.synthesize(apiKey, ttsRequest) { _, _, _ -> }
            binding.btnTest.isEnabled = true

            if (result.isSuccess) {
                Toast.makeText(this@SettingsActivity, R.string.test_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.test_failed, result.exceptionOrNull()?.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
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
