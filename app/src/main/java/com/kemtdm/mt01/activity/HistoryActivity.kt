package com.kemtdm.mt01.activity

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.util.Pair
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.kemtdm.mt01.R
import com.kemtdm.mt01.data.TxnRepository
import com.kemtdm.mt01.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var tvDateRange: TextView
    private lateinit var btnFilterDate: View
    private lateinit var chipGroupType: ChipGroup
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmptyHistory: TextView
    private lateinit var progressBar: ProgressBar

    private var startDate: String? = todayString()
    private var endDate: String? = todayString()
    private var selectedType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        bindViews()
        setupToolbar()
        setupListeners()
        
        updateDateDisplay()
        loadHistory()
    }

    private fun bindViews() {
        toolbar          = findViewById(R.id.toolbar)
        tvDateRange      = findViewById(R.id.tv_date_range)
        btnFilterDate    = findViewById(R.id.btn_filter_date)
        chipGroupType    = findViewById(R.id.chip_group_type)
        rvHistory        = findViewById(R.id.rv_history)
        tvEmptyHistory   = findViewById(R.id.tv_empty_history)
        progressBar      = findViewById(R.id.progress_bar)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "ประวัติรายการ"
    }

    private fun setupListeners() {
        btnFilterDate.setOnClickListener { showDateRangePicker() }

        chipGroupType.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedType = when {
                checkedIds.contains(R.id.chip_in)  -> "IN"
                checkedIds.contains(R.id.chip_out) -> "OUT"
                checkedIds.contains(R.id.chip_ret) -> "RET"
                else -> null
            }
            loadHistory()
        }
    }

    private fun showDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("เลือกช่วงวันที่")
            .setSelection(Pair(MaterialDatePicker.todayInUtcMilliseconds(), MaterialDatePicker.todayInUtcMilliseconds()))
            .build()

        picker.addOnPositiveButtonClickListener { range ->
            startDate = millisToDateString(range.first)
            endDate   = millisToDateString(range.second)
            updateDateDisplay()
            loadHistory()
        }
        picker.show(supportFragmentManager, "date_range_picker")
    }

    private fun updateDateDisplay() {
        tvDateRange.text = if (startDate == endDate) {
            "วันที่: $startDate"
        } else {
            "ระหว่าง: $startDate ถึง $endDate"
        }
    }

    private fun loadHistory() {
        progressBar.visibility = View.VISIBLE
        rvHistory.visibility = View.GONE
        tvEmptyHistory.visibility = View.GONE

        val userIdFilter = if (SessionManager.isAdmin(this)) null else SessionManager.getUserId(this)

        lifecycleScope.launch {
            val list = TxnRepository.getFilteredTxn(
                startDate = startDate,
                endDate   = endDate,
                txnType   = selectedType,
                createdBy = userIdFilter
            )

            progressBar.visibility = View.GONE
            if (list.isEmpty()) {
                tvEmptyHistory.visibility = View.VISIBLE
            } else {
                rvHistory.visibility = View.VISIBLE
                rvHistory.layoutManager = LinearLayoutManager(this@HistoryActivity)
                rvHistory.adapter = RecentTxnAdapter(list) // Reuse existing adapter
            }
        }
    }

    private fun todayString() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun millisToDateString(ms: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ms))

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
