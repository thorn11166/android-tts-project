package com.fishaudio.tts.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoicePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        repository = VoiceRepository(this)
        adapter = VoiceAdapter(emptyList()) { voice -> selectVoice(voice) }

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

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterVoices(newText ?: "")
                return true
            }
        })

        binding.btnCustomId.setOnClickListener {
            val customId = binding.etCustomId.text.toString().trim()
            if (customId.isNotEmpty()) {
                val voice = VoiceModel(id = customId, title = "Custom ($customId)")
                selectVoice(voice)
            }
        }

        loadFirstPage()
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
                filterVoices(binding.searchView.query?.toString() ?: "")
            } else {
                Toast.makeText(
                    this@VoicePickerActivity,
                    getString(R.string.error_load_voices, result.exceptionOrNull()?.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun filterVoices(query: String) {
        val filtered = if (query.isEmpty()) {
            allVoices
        } else {
            allVoices.filter { voice ->
                voice.title.contains(query, ignoreCase = true) ||
                voice.languages.any { it.contains(query, ignoreCase = true) }
            }
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
        voice.coverImage?.let { url ->
            holder.binding.ivAvatar.load(url) {
                transformations(CircleCropTransformation())
                placeholder(com.fishaudio.tts.R.drawable.ic_voice_placeholder)
            }
        }
        holder.binding.root.setOnClickListener { onSelect(voice) }
    }

    override fun getItemCount() = voices.size

    fun updateVoices(newVoices: List<VoiceModel>) {
        voices = newVoices
        notifyDataSetChanged()
    }
}
