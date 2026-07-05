package com.niraj.voicereminder.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.niraj.voicereminder.R
import com.niraj.voicereminder.data.Reminder
import com.niraj.voicereminder.databinding.ItemReminderBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderAdapter(
    private val onItemClick: (Reminder) -> Unit,
    private val onDeleteClick: (Reminder) -> Unit,
    private val onToggleActive: (Reminder) -> Unit
) : ListAdapter<Reminder, ReminderAdapter.ViewHolder>(DiffCallback) {

    private val sdf = SimpleDateFormat("EEE, dd MMM  •  hh:mm a", Locale.getDefault())

    inner class ViewHolder(private val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(reminder: Reminder) {
            binding.tvTitle.text = reminder.title
            binding.tvDateTime.text = sdf.format(Date(reminder.triggerAtMillis))
            binding.tvCategory.text = reminder.category

            binding.tvStatus.text = when {
                !reminder.isActive        -> binding.root.context.getString(R.string.status_done)
                reminder.isSnoozed        -> binding.root.context.getString(R.string.status_snoozed)
                reminder.triggerAtMillis < System.currentTimeMillis()
                                          -> binding.root.context.getString(R.string.status_overdue)
                else                      -> binding.root.context.getString(R.string.status_pending)
            }

            // Suppress listener before setting checked state to avoid loops
            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = reminder.isActive
            binding.switchActive.setOnCheckedChangeListener { _, _ -> onToggleActive(reminder) }

            binding.root.setOnClickListener { onItemClick(reminder) }
            binding.btnDelete.setOnClickListener { onDeleteClick(reminder) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReminderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Reminder>() {
        override fun areItemsTheSame(oldItem: Reminder, newItem: Reminder) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Reminder, newItem: Reminder) =
            oldItem == newItem
    }
}
