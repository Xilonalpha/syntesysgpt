package com.synthesis.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synthesis.data.local.entity.SynthesizedResearchEntity
import com.synthesis.data.repository.SynthesizedResearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SynthesizedResearchViewModel @Inject constructor(
    private val repository: SynthesizedResearchRepository
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>(null)
    val bookmarkedOnly = MutableStateFlow(false)

    val allResearch: StateFlow<List<SynthesizedResearchEntity>> = repository.allResearch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredResearch: StateFlow<List<SynthesizedResearchEntity>> = combine(
        allResearch,
        searchQuery,
        selectedCategory,
        bookmarkedOnly
    ) { list, query, category, onlyBookmarked ->
        list.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.targetName.contains(query, ignoreCase = true) ||
                item.summary.contains(query, ignoreCase = true)
            val matchesCategory = category == null || item.researchCategory.equals(category, ignoreCase = true)
            val matchesBookmark = !onlyBookmarked || item.isBookmarked
            matchesQuery && matchesCategory && matchesBookmark
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(newQuery: String) {
        searchQuery.value = newQuery
    }

    fun onCategorySelected(category: String?) {
        selectedCategory.value = if (selectedCategory.value == category) null else category
    }

    fun onToggleBookmarkFilter() {
        bookmarkedOnly.value = !bookmarkedOnly.value
    }

    fun toggleBookmark(item: SynthesizedResearchEntity) {
        viewModelScope.launch {
            repository.toggleBookmark(item.id, item.isBookmarked)
        }
    }

    fun deleteResearch(id: String) {
        viewModelScope.launch {
            repository.deleteResearchById(id)
        }
    }

    fun clearAllResearch() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun saveSynthesizedResearch(
        title: String,
        targetName: String,
        researchCategory: String,
        summary: String,
        synthesizedDataJson: String,
        confidenceScore: Float,
        sourceCount: Int,
        observerNotes: String = ""
    ) {
        viewModelScope.launch {
            val entity = SynthesizedResearchEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                targetName = targetName,
                researchCategory = researchCategory,
                summary = summary,
                synthesizedDataJson = synthesizedDataJson,
                confidenceScore = confidenceScore,
                sourceCount = sourceCount,
                observerNotes = observerNotes,
                timestamp = System.currentTimeMillis(),
                isVerified = false,
                isBookmarked = false
            )
            repository.saveResearch(entity)
        }
    }

}
