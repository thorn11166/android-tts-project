package com.fishaudio.tts.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.chip.Chip
import com.fishaudio.tts.R
import com.fishaudio.tts.data.PreferencesManager
import com.fishaudio.tts.databinding.ActivityVoicePickerBinding
import com.fishaudio.tts.databinding.ItemVoiceBinding
import com.fishaudio.tts.model.VoiceModel
import com.fishaudio.tts.repository.VoiceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VoicePickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoicePickerBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var repository: VoiceRepository
    private lateinit var adapter: VoiceAdapter

    private var allVoices: List<VoiceModel> = emptyList()
    private var currentPage = 1
    private var isLoading = false
    private var activeLanguageFilter = ""
    private var currentSelectedVoiceId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoicePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        prefs = PreferencesManager(this)
        repository = VoiceRepository(this)

        lifecycleScope.launch {
            currentSelectedVoiceId = prefs.selectedVoiceId.first()
            adapter = VoiceAdapter(emptyList(), currentSelectedVoiceId) { voice -> selectVoice(voice) }
            binding.recyclerVoices.apply {
                layoutManager = LinearLayoutManager(this@VoicePickerActivity)
                adapter = this@VoicePickerActivity.adapter
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                        val lm = rv.layoutManager as LinearLayoutManager
                        if (!isLoading && lm.findLastVisibleItemPosition() >= adapter.itemCount - 5) {
                            loadNextPage()
                        }
                    }
                })
            }
            loadFirstPage()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterVoices(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnCustomId.setOnClickListener {
            val customId = binding.etCustomId.text.toString().trim()
            if (customId.isNotEmpty()) {
                selectVoice(VoiceModel(id = customId, title = "Custom ($customId)"))
            }
        }

        // Seed the "All" chip immediately so the row is visible
        addFilterChip(getString(R.string.filter_all), "", checked = true)
    }

    private fun loadFirstPage() {
        currentPage = 1
        allVoices = emptyList()
        loadVoices()
    }

    private fun loadNextPage() {
        currentPage++
        loadVoices()
    }

    private fun loadVoices() {
        lifecycleScope.launch {
            val apiKey = prefs.apiKey.first()
            if (apiKey.isEmpty()) {
                Toast.makeText(this@VoicePickerActivity, R.string.error_no_api_key, Toast.LENGTH_SHORT).show()
                return@launch
            }

            isLoading = true
            binding.progressBar.visibility = View.VISIBLE

            val result = repository.getVoices(apiKey, pageNumber = currentPage)
            binding.progressBar.visibility = View.GONE
            isLoading = false

            if (result.isSuccess) {
                val newVoices = result.getOrThrow()
                allVoices = allVoices + newVoices
                updateLanguageChips()
                filterVoices(binding.etSearch.text?.toString() ?: "")
            } else {
                Toast.makeText(
                    this@VoicePickerActivity,
                    getString(R.string.error_load_voices, result.exceptionOrNull()?.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateLanguageChips() {
        val existingLabels = (0 until binding.chipGroupLanguages.childCount)
            .map { (binding.chipGroupLanguages.getChildAt(it) as? Chip)?.text?.toString() }
            .toSet()

        val newLanguages = allVoices
            .flatMap { it.languages }
            .distinct()
            .sorted()
            .filter { it !in existingLabels }

        newLanguages.forEach { lang -> addFilterChip(lang, lang) }
    }

    private fun addFilterChip(label: String, filter: String, checked: Boolean = false) {
        val chip = Chip(this).apply {
            text = label
            isCheckable = true
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    activeLanguageFilter = filter
                    filterVoices(binding.etSearch.text?.toString() ?: "")
                }
            }
        }
        binding.chipGroupLanguages.addView(chip)
    }

    private fun filterVoices(query: String) {
        val filtered = allVoices.filter { voice ->
            val matchesQuery = query.isEmpty() ||
                voice.title.contains(query, ignoreCase = true) ||
                voice.languages.any { it.contains(query, ignoreCase = true) }
            val matchesLang = activeLanguageFilter.isEmpty() ||
                voice.languages.any { it.equals(activeLanguageFilter, ignoreCase = true) }
            matchesQuery && matchesLang
        }
        adapter.updateVoices(filtered)
    }

    private fun selectVoice(voice: VoiceModel) {
        lifecycleScope.launch {
            repository.setSelectedVoice(voice.id, voice.title)
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra(EXTRA_VOICE_ID, voice.id)
                putExtra(EXTRA_VOICE_NAME, voice.title)
            })
            finish()
        }
    }

    companion object {
        const val EXTRA_VOICE_ID = "voice_id"
        const val EXTRA_VOICE_NAME = "voice_name"
    }
}

class VoiceAdapter(
    private var voices: List<VoiceModel>,
    private var selectedVoiceId: String,
    private val onSelect: (VoiceModel) -> Unit
) : RecyclerView.Adapter<VoiceAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemVoiceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val voice = voices[position]
        holder.binding.tvVoiceName.text = voice.title
        holder.binding.tvLanguages.text = voice.languages.joinToString(", ")
        holder.binding.ivSelected.visibility =
            if (voice.id == selectedVoiceId) View.VISIBLE else View.GONE

        voice.coverImage?.let { url ->
            holder.binding.ivAvatar.load(url) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_voice_placeholder)
            }
        } ?: run {
            holder.binding.ivAvatar.setImageResource(R.drawable.ic_voice_placeholder)
        }

        holder.binding.root.setOnClickListener { onSelect(voice) }
    }

    override fun getItemCount() = voices.size

    fun updateVoices(newVoices: List<VoiceModel>) {
        voices = newVoices
        notifyDataSetChanged()
    }
}
