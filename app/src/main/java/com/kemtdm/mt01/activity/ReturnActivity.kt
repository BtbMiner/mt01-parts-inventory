package com.kemtdm.mt01.activity

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kemtdm.mt01.R
import com.kemtdm.mt01.data.StockInfo
import com.kemtdm.mt01.data.StockRepository
import com.kemtdm.mt01.data.TxnInput
import com.kemtdm.mt01.data.TxnRepository
import com.kemtdm.mt01.utils.DeviceUtils
import com.kemtdm.mt01.utils.SessionManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.kemtdm.mt01.utils.QuantitySelectorHandler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReturnActivity : BaseActivity() {

    private lateinit var btnOpenScanner: Button
    private lateinit var etManualInput: EditText
    private lateinit var btnSearch: Button
    private lateinit var layoutPartInfo: LinearLayout
    private lateinit var tvPartName: TextView
    private lateinit var tvPartCode: TextView
    private lateinit var tvLocId: TextView
    private lateinit var tvUnit: TextView
    private lateinit var tvCurrentQty: TextView
    private lateinit var etQty: EditText
    private lateinit var tvTxnDate: TextView
    private lateinit var etRemark: EditText
    private lateinit var btnConfirm: Button

    private lateinit var qtyHandler: QuantitySelectorHandler
    private var currentStock: StockInfo? = null
    private var selectedDate: String = todayString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_return)

        setupToolbar()
        initViews()
        setupWindowInsets()
        setupListeners()
        resetForm()
    }

    private fun setupWindowInsets() {
        val appBarLayout = findViewById<View>(R.id.toolbar).parent as View
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.menu_return)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun initViews() {
        btnOpenScanner = findViewById(R.id.btn_open_scanner)
        etManualInput  = findViewById(R.id.et_manual_input)
        btnSearch      = findViewById(R.id.btn_manual_search)
        layoutPartInfo = findViewById(R.id.layout_part_info)
        tvPartName     = findViewById(R.id.tv_part_name)
        tvPartCode     = findViewById(R.id.tv_part_code)
        tvLocId = findViewById(R.id.tv_loc_id)
        tvUnit  = findViewById(R.id.tv_unit)
        tvCurrentQty   = findViewById(R.id.tv_current_qty)
        val qtyContainer = findViewById<View>(R.id.qty_selector)
        etQty            = qtyContainer.findViewById(R.id.et_qty)
        val btnMinus     = qtyContainer.findViewById<MaterialButton>(R.id.btn_qty_minus)
        val btnPlus      = qtyContainer.findViewById<MaterialButton>(R.id.btn_qty_plus)

        qtyHandler = QuantitySelectorHandler(etQty, btnMinus, btnPlus, lifecycleScope)
        qtyHandler.setupShortcuts(
            qtyContainer.findViewById(R.id.btn_shortcut_1),
            qtyContainer.findViewById(R.id.btn_shortcut_5),
            qtyContainer.findViewById(R.id.btn_shortcut_10)
        )
        tvTxnDate      = findViewById(R.id.tv_txn_date)
        etRemark       = findViewById(R.id.et_remark)
        btnConfirm     = findViewById(R.id.btn_confirm)
    }

    private fun setupListeners() {
        btnOpenScanner.setOnClickListener { startScanning() }

        btnSearch.setOnClickListener {
            val input = etManualInput.text.toString()
            performFlexibleSearch(input)
        }

        etManualInput.setOnEditorActionListener { _, _, _ ->
            val input = etManualInput.text.toString()
            performFlexibleSearch(input)
            true
        }

        tvTxnDate.setOnClickListener { showDatePicker() }
        btnConfirm.setOnClickListener { confirmReturn() }
    }

    // -------------------------------------------------------
    // Search Logic
    // -------------------------------------------------------

    private fun performFlexibleSearch(input: String) {
        lifecycleScope.launch {
            when (val result = StockRepository.searchStockFlexible(input)) {
                is StockRepository.SearchResult.Single -> {
                    displayStockInfo(result.stock)
                }
                is StockRepository.SearchResult.Multiple -> {
                    showSelectionDialog(result.list)
                }
                is StockRepository.SearchResult.NotFound -> {
                    showError("ไม่พบข้อมูลสำหรับ: $input")
                    layoutPartInfo.visibility = View.GONE
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

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { loadStockDirect(it) }
    }

    private fun startScanning() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("สแกน QR Code ของสินค้า")
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        barcodeLauncher.launch(options)
    }

    private fun loadStockDirect(partId: String) {
        lifecycleScope.launch {
            val stock = StockRepository.getStockByPartId(partId)
            if (stock != null) {
                displayStockInfo(stock)
            } else {
                showError("ไม่พบ PART ID: $partId")
            }
        }
    }

    private fun displayStockInfo(stock: StockInfo) {
        currentStock = stock
        tvPartName.text = stock.partName
        tvPartCode.text = stock.partCode
        tvLocId.text      = stock.locId
        tvCurrentQty.text = stock.qtyOnHand.toInt().toString()
        tvUnit.text       = stock.unit
        layoutPartInfo.visibility = View.VISIBLE
        btnConfirm.isEnabled = true
        etQty.requestFocus()
    }

    private fun confirmReturn() {
        val stock = currentStock ?: run {
            showError("กรุณาเลือกสินค้า")
            return
        }

        val qty = etQty.text.toString().toDoubleOrNull() ?: 0.0
        if (qty <= 0) {
            showError("กรุณากรอกจำนวนที่ถูกต้อง")
            return
        }

        val input = TxnInput(
            txnType = "RET",
            partId = stock.partId,
            locId = stock.locId,
            qty = qty,
            txnDate = selectedDate,
            remark = etRemark.text.toString(),
            createdBy = SessionManager.getUserId(this),
            deviceInfo = DeviceUtils.getDeviceId(this)
        )

        lifecycleScope.launch {
            btnConfirm.isEnabled = false
            if (TxnRepository.saveTxn(input)) {
                showSuccess("คืนของสำเร็จ")
                resetForm()
            } else {
                showError("บันทึกไม่สำเร็จ")
                btnConfirm.isEnabled = true
            }
        }
    }

    private fun resetForm() {
        currentStock = null
        selectedDate = todayString()
        tvTxnDate.text = selectedDate
        layoutPartInfo.visibility = View.GONE
        etQty.text.clear()
        etRemark.text.clear()
        etManualInput.text.clear()
        btnConfirm.isEnabled = false
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker().build()
        picker.addOnPositiveButtonClickListener { ms ->
            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ms))
            tvTxnDate.text = selectedDate
        }
        picker.show(supportFragmentManager, "DATE_PICKER")
    }

    private fun showError(msg: String) = MaterialAlertDialogBuilder(this).setMessage(msg).setPositiveButton("ตกลง", null).show()
    private fun showSuccess(msg: String) = MaterialAlertDialogBuilder(this).setMessage(msg).setPositiveButton("ตกลง", null).show()

    private fun todayString() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}