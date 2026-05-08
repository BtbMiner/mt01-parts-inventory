package com.kemtdm.mt01.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.kemtdm.mt01.R
import com.kemtdm.mt01.data.*
import kotlinx.coroutines.launch

class InquiryActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var etSearchInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var btnSearch: com.google.android.material.button.MaterialButton
    private lateinit var btnOpenScanner: com.google.android.material.button.MaterialButton
    private lateinit var layoutResults: View
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvPartName: TextView
    private lateinit var tvPartDetails: TextView
    private lateinit var tvLocId: TextView
    private lateinit var tvCurrentQty: TextView
    private lateinit var tvMinStock: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmptyHistory: TextView

    private val scanLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        val scanned = result.contents
        if (!scanned.isNullOrBlank()) {
            loadInquiryData(scanned.trim())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inquiry)

        bindViews()
        setupToolbar()
        setupListeners()
    }

    private fun bindViews() {
        toolbar          = findViewById(R.id.toolbar)
        etSearchInput    = findViewById(R.id.et_search_input)
        btnSearch        = findViewById(R.id.btn_search)
        btnOpenScanner   = findViewById(R.id.btn_open_scanner)
        layoutResults    = findViewById(R.id.layout_inquiry_results)
        tvStatusBadge    = findViewById(R.id.tv_status_badge)
        tvPartName       = findViewById(R.id.tv_part_name)
        tvPartDetails    = findViewById(R.id.tv_part_details)
        tvLocId          = findViewById(R.id.tv_loc_id)
        tvCurrentQty     = findViewById(R.id.tv_current_qty)
        tvMinStock       = findViewById(R.id.tv_min_stock)
        rvHistory        = findViewById(R.id.rv_movement_history)
        tvEmptyHistory   = findViewById(R.id.tv_empty_history)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "ตรวจสอบข้อมูล"
    }

    private fun setupListeners() {
        btnOpenScanner.setOnClickListener {
            val options = ScanOptions().apply {
                setPrompt("สแกน QR Code เพื่อตรวจสอบ")
                setBeepEnabled(true)
                setOrientationLocked(false)
            }
            scanLauncher.launch(options)
        }

        btnSearch.setOnClickListener {
            val keyword = etSearchInput.text.toString().trim()
            if (keyword.isNotEmpty()) {
                performManualSearch(keyword)
            }
        }

        etSearchInput.setOnEditorActionListener { _, _, _ ->
            val keyword = etSearchInput.text.toString().trim()
            if (keyword.isNotEmpty()) performManualSearch(keyword)
            true
        }
    }

    private fun performManualSearch(keyword: String) {
        lifecycleScope.launch {
            when (val result = StockRepository.searchStockFlexible(keyword)) {
                is StockRepository.SearchResult.Single -> {
                    displayStockInfo(result.stock)
                }
                is StockRepository.SearchResult.Multiple -> {
                    showSelectionDialog(result.list)
                }
                is StockRepository.SearchResult.NotFound -> {
                    showError("ไม่พบข้อมูลสำหรับ: $keyword")
                    layoutResults.visibility = View.GONE
                }
                else -> {}
            }
        }
    }

    private fun showSelectionDialog(list: List<StockInfo>) {
        val names = list.map { "${it.partName} (${it.partCode})" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("เลือกรายการที่ต้องการ")
            .setItems(names) { _, which ->
                displayStockInfo(list[which])
            }
            .show()
    }

    private fun loadInquiryData(partId: String) {
        lifecycleScope.launch {
            val stock = StockRepository.getStockByPartId(partId)
            if (stock != null) {
                displayStockInfo(stock)
            } else {
                showError("ไม่พบ PART ID: $partId")
                layoutResults.visibility = View.GONE
            }
        }
    }

    private fun displayStockInfo(stock: StockInfo) {
        layoutResults.visibility = View.VISIBLE
        tvPartName.text = stock.partName
        tvPartDetails.text = "Code: ${stock.partCode} | Unit: ${stock.unit}\nBrand: ${stock.brand ?: "-"} | Model: ${stock.model ?: "-"}"
        tvLocId.text = stock.locId
        tvCurrentQty.text = "${stock.qtyOnHand.toInt()} ${stock.unit}"
        tvMinStock.text = "Min Stock: ${stock.minStock.toInt()} ${stock.unit}"

        // Status Badge
        when {
            stock.qtyOnHand <= 0 -> {
                tvStatusBadge.text = "OUT"
                tvStatusBadge.setBackgroundColor(getColor(R.color.red_disabled))
            }
            stock.qtyOnHand < stock.minStock -> {
                tvStatusBadge.text = "LOW"
                tvStatusBadge.setBackgroundColor(MaterialColors.getColor(this, R.attr.colorStatusLow, getColor(R.color.orange_600)))
            }
            else -> {
                tvStatusBadge.text = "NORMAL"
                tvStatusBadge.setBackgroundColor(MaterialColors.getColor(this, R.attr.colorTxnIn, getColor(R.color.green_700)))
            }
        }

        // Load History
        loadMovementHistory(stock.partId)
    }

    private fun loadMovementHistory(partId: String) {
        lifecycleScope.launch {
            val history = TxnRepository.getRecentTxn(partId = partId, limitDays = 30)
            if (history.isEmpty()) {
                rvHistory.visibility = View.GONE
                tvEmptyHistory.visibility = View.VISIBLE
            } else {
                tvEmptyHistory.visibility = View.GONE
                rvHistory.visibility = View.VISIBLE
                rvHistory.layoutManager = LinearLayoutManager(this@InquiryActivity)
                rvHistory.adapter = MovementAdapter(history)
            }
        }
    }

    private fun showError(msg: String) {
        MaterialAlertDialogBuilder(this)
            .setMessage(msg)
            .setPositiveButton("ตกลง", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}

class MovementAdapter(private val items: List<TxnRecord>) : RecyclerView.Adapter<MovementAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tv_txn_type)
        val tvDate: TextView = view.findViewById(R.id.tv_date)
        val tvQty: TextView = view.findViewById(R.id.tv_qty)
        val tvUser: TextView = view.findViewById(R.id.tv_created_by)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_txn, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        val (label, color) = when (item.txnType) {
            "IN"  -> Pair("รับเข้า", MaterialColors.getColor(ctx, R.attr.colorTxnIn, ctx.getColor(android.R.color.holo_green_dark)))
            "OUT" -> Pair("เบิก",   MaterialColors.getColor(ctx, R.attr.colorTxnOut, ctx.getColor(R.color.orange_500)))
            "RET" -> Pair("คืน",    MaterialColors.getColor(ctx, R.attr.colorTxnReturn, ctx.getColor(R.color.blue_primary)))
            else  -> Pair(item.txnType, MaterialColors.getColor(ctx, R.attr.colorTextSecondary, ctx.getColor(R.color.grey_text)))
        }

        holder.tvType.text = label
        holder.tvType.setTextColor(color)
        holder.tvDate.text = item.txnDate
        holder.tvQty.text = "${item.qty.toInt()} ${item.unit}"
        holder.tvUser.text = "โดย: ${item.createdBy}"

        // We reuse item_recent_txn but customize part_name to show something else if needed
        // For inquiry history, part name is already known, so we can hide it or use it for User
        holder.itemView.findViewById<TextView>(R.id.tv_part_name).visibility = View.GONE
    }
}
