package com.kemtdm.mt01.activity

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReturnActivity : AppCompatActivity() {

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

    private var currentStock: StockInfo? = null
    private var selectedDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_return) // ใช้ layout เดียวกับ issue ได้เลย

        setupToolbar()
        initViews()
        setupListeners()
        resetForm()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "คืนของ (Return)"
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
        etQty          = findViewById(R.id.et_qty)
        tvTxnDate      = findViewById(R.id.tv_txn_date)
        etRemark       = findViewById(R.id.et_remark)
        btnConfirm     = findViewById(R.id.btn_confirm)
    }

    private fun setupListeners() {
        btnOpenScanner.setOnClickListener { startScanning() }
        btnSearch.setOnClickListener {
            val id = etManualInput.text.toString().trim()
            if (id.isNotEmpty()) loadPartData(id)
        }
        tvTxnDate.setOnClickListener { showDatePicker() }
        btnConfirm.setOnClickListener { confirmReturn() }
    }

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { loadPartData(it) }
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

    private fun loadPartData(partId: String) {
        lifecycleScope.launch {
            val stock = StockRepository.getStockByPartId(partId)
            if (stock != null) {
                currentStock = stock
                tvPartName.text = stock.partName
                tvPartCode.text = stock.partCode
                tvCurrentQty.text = "${stock.qtyOnHand} ${stock.unit}"
                layoutPartInfo.visibility = View.VISIBLE
                btnConfirm.isEnabled = true
                tvLocId.text      = stock.locId
                tvCurrentQty.text = stock.qtyOnHand.toInt().toString()
                tvUnit.text       = stock.unit


            } else {
                showError("ไม่พบข้อมูลสินค้าชิ้นนี้ในระบบ")
                layoutPartInfo.visibility = View.GONE
                btnConfirm.isEnabled = false
            }
        }
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
        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        tvTxnDate.text = selectedDate
        layoutPartInfo.visibility = View.GONE
        etQty.text.clear()
        etRemark.text.clear()
        etManualInput.text.clear()
        btnConfirm.isEnabled = false
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker().build()
        picker.addOnPositiveButtonClickListener {
            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
            tvTxnDate.text = selectedDate
        }
        picker.show(supportFragmentManager, "DATE_PICKER")
    }

    private fun showError(msg: String) = MaterialAlertDialogBuilder(this).setMessage(msg).setPositiveButton("ตกลง", null).show()
    private fun showSuccess(msg: String) = MaterialAlertDialogBuilder(this).setMessage(msg).setPositiveButton("ตกลง", null).show()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}