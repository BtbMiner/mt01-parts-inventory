package com.kemtdm.mt01.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.color.MaterialColors
import com.kemtdm.mt01.R
import com.kemtdm.mt01.data.StockInfo
import com.kemtdm.mt01.data.StockRepository
import com.kemtdm.mt01.data.StockStatus
import com.kemtdm.mt01.data.TxnRecord
import com.kemtdm.mt01.data.TxnRepository
import com.kemtdm.mt01.utils.SessionManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val TAG = "tagMainActivity"

    // Views
    private lateinit var toolbar: Toolbar
    private lateinit var tvWelcome: TextView
    private lateinit var tvLowStockCount: TextView
    private lateinit var rvLowStock: RecyclerView
    private lateinit var tvEmptyLowStock: TextView
    private lateinit var rvRecentTxn: RecyclerView
    private lateinit var tvEmptyRecentTxn: TextView
    private lateinit var cardInquiry: MaterialCardView
    private lateinit var cardReceive: MaterialCardView
    private lateinit var cardIssue: MaterialCardView
    private lateinit var cardReturn: MaterialCardView

    // 1. Added OnBackPressedCallback
    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            showLogoutConfirmationDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 2. Register the callback
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        bindViews()
        setupToolbar()
        setupWindowInsets()
        setupShortcutButtons()
        loadDashboard()
    }

    private fun setupWindowInsets() {
        val appBarLayout = findViewById<View>(R.id.toolbar).parent as View
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        // รีโหลดทุกครั้งที่กลับมาจากหน้า Receive/Issue/Return
        loadDashboard()
    }

    // -------------------------------------------------------
    // Setup
    // -------------------------------------------------------

    private fun bindViews() {
        toolbar          = findViewById(R.id.toolbar)
        tvWelcome        = findViewById(R.id.tv_welcome)
        tvLowStockCount  = findViewById(R.id.tv_low_stock_count)
        rvLowStock       = findViewById(R.id.rv_low_stock)
        tvEmptyLowStock  = findViewById(R.id.tv_empty_low_stock)
        rvRecentTxn      = findViewById(R.id.rv_recent_txn)
        tvEmptyRecentTxn = findViewById(R.id.tv_empty_recent_txn)
        cardInquiry      = findViewById(R.id.card_inquiry)
        cardReceive      = findViewById(R.id.card_receive)
        cardIssue        = findViewById(R.id.card_issue)
        cardReturn       = findViewById(R.id.card_return)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        val userName = SessionManager.getUserName(this)
        tvWelcome.text = "สวัสดี, $userName"
    }

    private fun setupShortcutButtons() {
        cardInquiry.setOnClickListener {
            startActivity(Intent(this, InquiryActivity::class.java))
        }
        cardReceive.setOnClickListener {
            startActivity(Intent(this, ReceiveActivity::class.java))
        }
        cardIssue.setOnClickListener {
            startActivity(Intent(this, IssueActivity::class.java))
        }
        cardReturn.setOnClickListener {
            startActivity(Intent(this, ReturnActivity::class.java))
        }
    }

    // -------------------------------------------------------
    // Load Data
    // -------------------------------------------------------

    private fun loadDashboard() {
        lifecycleScope.launch {
            loadLowStock()
            loadRecentTxn()
        }
    }

    private suspend fun loadLowStock() {
        val list = StockRepository.getLowStockList()
        tvLowStockCount.text = list.size.toString()

        if (list.isEmpty()) {
            rvLowStock.visibility       = View.GONE
            tvEmptyLowStock.visibility  = View.VISIBLE
        } else {
            tvEmptyLowStock.visibility  = View.GONE
            rvLowStock.visibility       = View.VISIBLE
            rvLowStock.layoutManager    = LinearLayoutManager(this@MainActivity)
            rvLowStock.adapter          = LowStockAdapter(list)
        }
    }

    private suspend fun loadRecentTxn() {
        // Normal user เห็นเฉพาะของตัวเอง, Admin เห็นทั้งหมด
        val userId = if (SessionManager.isAdmin(this)) null
        else SessionManager.getUserId(this)

        val list = TxnRepository.getRecentTxn(createdBy = userId, limitDays = 1)

        if (list.isEmpty()) {
            rvRecentTxn.visibility       = View.GONE
            tvEmptyRecentTxn.visibility  = View.VISIBLE
        } else {
            tvEmptyRecentTxn.visibility  = View.GONE
            rvRecentTxn.visibility       = View.VISIBLE
            rvRecentTxn.layoutManager    = LinearLayoutManager(this@MainActivity)
            rvRecentTxn.adapter          = RecentTxnAdapter(list)
        }
    }

    // -------------------------------------------------------
    // Options Menu (Logout)
    // -------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                // 3. Reuse confirmation dialog
                showLogoutConfirmationDialog()
                true
            }
            R.id.action_history -> {
                startActivity(Intent(this, HistoryActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 4. Centralized logout confirmation logic
     */
    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("ยืนยันการออกจากระบบ")
            .setMessage("คุณต้องการออกจากระบบใช่หรือไม่?")
            .setPositiveButton("ตกลง") { _, _ ->
                SessionManager.clearSession(this)
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }
}

// -------------------------------------------------------
// LowStockAdapter
// -------------------------------------------------------

class LowStockAdapter(private val items: List<StockInfo>) :
    RecyclerView.Adapter<LowStockAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvPartName : TextView = view.findViewById(R.id.tv_part_name)
        val tvLocId    : TextView = view.findViewById(R.id.tv_loc_id)
        val tvQty      : TextView = view.findViewById(R.id.tv_qty)
        val tvStatus   : TextView = view.findViewById(R.id.tv_status)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_low_stock, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvPartName.text = item.partName
        holder.tvLocId.text    = item.locId
        holder.tvQty.text      = "${item.qtyOnHand.toInt()} / ${item.minStock.toInt()} ${item.unit}"

        val ctx = holder.itemView.context
        when (item.stockStatus) {
            StockStatus.OUT -> {
                holder.tvStatus.text = "หมด"
                holder.tvStatus.setTextColor(MaterialColors.getColor(ctx, R.attr.colorStatusOut, ctx.getColor(R.color.red_disabled)))
            }
            StockStatus.LOW -> {
                holder.tvStatus.text = "ใกล้หมด"
                holder.tvStatus.setTextColor(MaterialColors.getColor(ctx, R.attr.colorStatusLow, ctx.getColor(R.color.orange_500)))
            }
            StockStatus.NORMAL -> {
                holder.tvStatus.text = "ปกติ"
                holder.tvStatus.setTextColor(MaterialColors.getColor(ctx, R.attr.colorTxnIn, ctx.getColor(android.R.color.holo_green_dark)))
            }
        }
    }
}

// -------------------------------------------------------
// RecentTxnAdapter
// -------------------------------------------------------

class RecentTxnAdapter(private val items: List<TxnRecord>) :
    RecyclerView.Adapter<RecentTxnAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTxnType  : TextView = view.findViewById(R.id.tv_txn_type)
        val tvPartName : TextView = view.findViewById(R.id.tv_part_name)
        val tvQty      : TextView = view.findViewById(R.id.tv_qty)
        val tvDate     : TextView = view.findViewById(R.id.tv_date)
        val tvCreatedBy: TextView = view.findViewById(R.id.tv_created_by)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_txn, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx  = holder.itemView.context

        val (label, color) = when (item.txnType) {
            "IN"  -> Pair("รับเข้า", MaterialColors.getColor(ctx, R.attr.colorTxnIn, ctx.getColor(android.R.color.holo_green_dark)))
            "OUT" -> Pair("เบิก",   MaterialColors.getColor(ctx, R.attr.colorTxnOut, ctx.getColor(R.color.orange_500)))
            "RET" -> Pair("คืน",    MaterialColors.getColor(ctx, R.attr.colorTxnReturn, ctx.getColor(R.color.blue_primary)))
            else  -> Pair(item.txnType, MaterialColors.getColor(ctx, R.attr.colorTextSecondary, ctx.getColor(R.color.grey_text)))
        }

        holder.tvTxnType.text  = label
        holder.tvTxnType.setTextColor(color)
        holder.tvPartName.text = item.partName
        holder.tvQty.text      = "${item.qty.toInt()} ${item.unit}"
        holder.tvDate.text     = item.txnDate
        holder.tvCreatedBy.text = item.createdBy
    }
}
