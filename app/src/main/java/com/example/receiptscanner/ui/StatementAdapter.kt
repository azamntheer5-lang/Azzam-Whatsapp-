package com.example.receiptscanner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.receiptscanner.R
import com.example.receiptscanner.databinding.ItemStatementGroupBinding
import com.example.receiptscanner.storage.NameGroup
import java.text.NumberFormat
import java.util.Locale

class StatementAdapter(
    private val onGroupTap: (NameGroup) -> Unit
) : ListAdapter<NameGroup, StatementAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemStatementGroupBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStatementGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = getItem(position)
        val formatter = NumberFormat.getCurrencyInstance(Locale("ar", "SA"))
        holder.binding.apply {
            textGroupName.text = group.name
            textGroupTotal.text = formatter.format(group.total)
            textGroupCount.text = holder.itemView.context.resources.getQuantityString(
                R.plurals.transfer_count, group.transfers.size, group.transfers.size
            )
            root.setOnClickListener { onGroupTap(group) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<NameGroup>() {
        override fun areItemsTheSame(oldItem: NameGroup, newItem: NameGroup) = oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: NameGroup, newItem: NameGroup) = oldItem == newItem
    }
}
