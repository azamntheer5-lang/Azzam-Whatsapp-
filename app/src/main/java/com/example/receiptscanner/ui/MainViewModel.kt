package com.example.receiptscanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.receiptscanner.model.Transfer
import com.example.receiptscanner.storage.TransferRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val transfers: StateFlow<List<Transfer>> = TransferRepository.transfers

    init {
        viewModelScope.launch {
            TransferRepository.loadIfNeeded(getApplication())
        }
    }

    fun totalAmount(): Double = transfers.value.sumOf { it.amount ?: 0.0 }

    fun deleteTransfer(id: String) {
        viewModelScope.launch {
            TransferRepository.deleteTransfer(getApplication(), id)
        }
    }
}
