package com.example.receiptscanner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.receiptscanner.databinding.ItemTransferBinding
import com.example.receiptscanner.model.Transfer
import java.text.NumberFormat
import java.util.Locale

class TransferAdapter(
    private val onLongPressDelete: (Transfer) -> Unit
) : ListAdapter<Transfer, TransferAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemTransferBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransferBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val formatter = NumberFormat.getCurrencyInstance(Locale("ar", "SA"))

        holder.binding.apply {
            textAmount.text = item.amount?.let { formatter.format(it) } ?: "—"
            textDate.text = item.date ?: "—"
            textRecipient.text = item.recipientName ?: item.senderName ?: "بدون اسم مستخرَج"
            textBank.text = item.bankId
            root.setOnLongClickListener {
                onLongPressDelete(item)
                true
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Transfer>() {
        override fun areItemsTheSame(oldItem: Transfer, newItem: Transfer) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Transfer, newItem: Transfer) = oldItem == newItem
    }
}
