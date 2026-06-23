package io.github.mojri.hesabyar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mojri.hesabyar.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CategoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository: HesabyarRepositoryInterface = HesabyarRepository(
        database.transactionDao(),
        database.loanDao(),
        database.installmentDao(),
        database.paymentHistoryDao(),
        database.categoryDao()
    )

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCategory(name: String, key: String, icon: String, color: Long, type: String) {
        viewModelScope.launch {
            repository.insertCategory(Category(name = name, key = key, icon = icon, color = color, type = type))
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    suspend fun getCategoryByKey(key: String): Category? {
        return repository.getCategoryByKey(key)
    }
}
