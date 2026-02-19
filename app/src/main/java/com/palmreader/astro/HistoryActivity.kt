package com.palmreader.astro

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.palmreader.astro.databinding.ActivityHistoryBinding
import kotlinx.coroutines.launch

class HistoryActivity : BaseFeatureActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.get(this)
        session = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }
        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val items = db.historyDao().getByUser(session.userId)
            runOnUiThread {
                binding.llHistory.removeAllViews()
                if (items.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    return@runOnUiThread
                }
                binding.tvEmpty.visibility = View.GONE
                items.forEach { h ->
                    val card = layoutInflater.inflate(R.layout.item_history, binding.llHistory, false)
                    card.findViewById<TextView>(R.id.tvCategory).text = "📂 ${h.category}"
                    card.findViewById<TextView>(R.id.tvQuestion).text = "Q: ${h.question}"
                    card.findViewById<TextView>(R.id.tvAnswer).text = "A: ${h.answer}"
                    card.findViewById<TextView>(R.id.tvDate).text = formatDate(h.timestamp)
                    binding.llHistory.addView(card)
                }
            }
        }
    }
}
