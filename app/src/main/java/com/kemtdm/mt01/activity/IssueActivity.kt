package com.kemtdm.mt01.activity

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.kemtdm.mt01.R
import com.kemtdm.mt01.data.StockInfo
import com.kemtdm.mt01.data.StockRepository
import com.kemtdm.mt01.data.TxnInput
import com.kemtdm.mt01.data.TxnRepository
import com.kemtdm.mt01.sql.SqlConnectionVariable
import com.kemtdm.mt01.utils.DeviceUtils
import com.kemtdm.mt01.utils.QuantitySelectorHandler
import com.kemtdm.mt01.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import org.json.JSONArray
import org.json.JSONObject
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class IssueActivity : BaseActivity() {

    // Views — scan section
    private lateinit var btnOpenScanner: Button
    private lateinit var etManualInput: TextInputEditText
    private lateinit var btnManualSearch: Button

    // Views — part info card
    private lateinit var layoutPartInfo: LinearLayout
    private lateinit var tvPartId: TextView
    private lateinit var tvPartCode: TextView
    private lateinit var tvPartName: TextView
    private lateinit var tvLocId: TextView
    private lateinit var tvCurrentQty: TextView
    private lateinit var tvUnit: TextView

    // Views — form
    private lateinit var etQty: EditText
    private lateinit var etRemark: EditText
    private lateinit var tvTxnDate: TextView
    private lateinit var btnConfirm: Button

    private lateinit var qtyHandler: QuantitySelectorHandler

    private var currentStock: StockInfo? = null
    private var selectedDate: String = todayString()
    // private val okHttpClient = OkHttpClient() // TODO: Disabled during transition to SMTP Email Notifications.

    // ZXing scanner launcher
    private val scanLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        val scanned = result.contents
        if (!scanned.isNullOrBlank()) {
            loadStockDirect(scanned.trim())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_issue)

        setupToolbar()
        bindViews()
        setupWindowInsets()
        setupListeners()
        tvTxnDate.text = selectedDate
    }

    private fun setupWindowInsets() {
        val appBarLayout = findViewById<View>(R.id.toolbar).parent as View
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }
    }

    // -------------------------------------------------------
    // Setup
    // -------------------------------------------------------

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.menu_issue)
    }

    private fun bindViews() {
        btnOpenScanner = findViewById(R.id.btn_open_scanner)
        etManualInput  = findViewById(R.id.et_manual_input)
        btnManualSearch = findViewById(R.id.btn_manual_search)
        layoutPartInfo = findViewById(R.id.layout_part_info)
        tvPartId       = findViewById(R.id.tv_part_id)
        tvPartCode     = findViewById(R.id.tv_part_code)
        tvPartName     = findViewById(R.id.tv_part_name)
        tvLocId        = findViewById(R.id.tv_loc_id)
        tvCurrentQty   = findViewById(R.id.tv_current_qty)
        tvUnit         = findViewById(R.id.tv_unit)

        // Qty Selector components from include
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

        etRemark       = findViewById(R.id.et_remark)
        tvTxnDate      = findViewById(R.id.tv_txn_date)
        btnConfirm     = findViewById(R.id.btn_confirm)
    }

    private fun setupListeners() {
        btnOpenScanner.setOnClickListener { openScanner() }

        btnManualSearch.setOnClickListener {
            val input = etManualInput.text.toString()
            performFlexibleSearch(input)
        }

        etManualInput.setOnEditorActionListener { _, _, _ ->
            val input = etManualInput.text.toString()
            performFlexibleSearch(input)
            true
        }

        tvTxnDate.setOnClickListener { showDatePicker() }
        btnConfirm.setOnClickListener { doConfirm() }
    }

    // -------------------------------------------------------
    // Search Logic
    // -------------------------------------------------------

    private fun performFlexibleSearch(input: String) {
        lifecycleScope.launch {
            when (val result = StockRepository.searchStockFlexible(input)) {
                is StockRepository.SearchResult.Single -> {
                    showPartInfo(result.stock)
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
                showPartInfo(list[which])
            }
            .show()
    }

    private fun loadStockDirect(partId: String) {
        lifecycleScope.launch {
            val stock = StockRepository.getStockByPartId(partId)
            if (stock != null) {
                showPartInfo(stock)
            } else {
                showError("ไม่พบ PART ID: $partId")
            }
        }
    }

    // -------------------------------------------------------
    // Scanner
    // -------------------------------------------------------

    private fun openScanner() {
        val options = ScanOptions().apply {
            setPrompt("สแกน QR Code บนพาร์ท")
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        scanLauncher.launch(options)
    }

    private fun showPartInfo(stock: StockInfo) {
        currentStock = stock
        tvPartId.text     = stock.partId
        tvPartCode.text   = stock.partCode
        tvPartName.text   = stock.partName
        tvLocId.text      = stock.locId
        tvCurrentQty.text = stock.qtyOnHand.toInt().toString()
        tvUnit.text       = stock.unit

        layoutPartInfo.visibility = View.VISIBLE
        btnConfirm.isEnabled      = true
        qtyHandler.setValue(1.0)
        etQty.requestFocus()
    }

    // -------------------------------------------------------
    // Date Picker
    // -------------------------------------------------------

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("เลือกวันที่เบิก")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { ms ->
            selectedDate   = millisToDateString(ms)
            tvTxnDate.text = selectedDate
        }
        picker.show(supportFragmentManager, "date_picker")
    }

    // -------------------------------------------------------
    // Confirm
    // -------------------------------------------------------

    private fun doConfirm() {
        val stock = currentStock ?: return

        val qty = qtyHandler.getValue()
        when {
            qty <= 0 -> { showError("กรุณากรอกจำนวนที่ถูกต้อง"); return }
            qty > stock.qtyOnHand -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("ยืนยันจำนวนเกินสต็อก")
                    .setMessage("จำนวนที่เบิก ($qty) มากกว่าจำนวนคงเหลือ (${stock.qtyOnHand.toInt()})\nคุณยังต้องการดำเนินการต่อหรือไม่?")
                    .setPositiveButton("ดำเนินการต่อ") { _, _ -> saveTxn(stock, qty) }
                    .setNegativeButton("ยกเลิก", null)
                    .show()
                return
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("ยืนยันการเบิก")
            .setMessage("${stock.partName}\nจำนวน ${qty.toInt()} ${stock.unit}\nวันที่ $selectedDate")
            .setPositiveButton("ยืนยัน") { _, _ -> saveTxn(stock, qty) }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun saveTxn(stock: StockInfo, qty: Double) {
        btnConfirm.isEnabled = false

        val input = TxnInput(
            txnType    = "OUT",
            partId     = stock.partId,
            locId      = stock.locId,
            qty        = qty,
            txnDate    = selectedDate,
            remark     = etRemark.text.toString().trim().ifEmpty { null },
            createdBy  = SessionManager.getUserId(this),
            deviceInfo = DeviceUtils.getDeviceId(this)
        )

        lifecycleScope.launch {
            val remainingQty = TxnRepository.saveTxn(input)

            if (remainingQty != null) {
                sendEmailNotificationIfNeeded(stock, remainingQty)
                resetForm()
                showSuccess("บันทึกสำเร็จ")
            } else {
                showError("บันทึกไม่สำเร็จ กรุณาลองใหม่")
                btnConfirm.isEnabled = true
            }
        }
    }

    // -------------------------------------------------------
    // Notification (SMTP Email)
    // -------------------------------------------------------

    private fun sendEmailNotificationIfNeeded(stock: StockInfo, remainingQty: Double) {
        if (remainingQty >= stock.minStock) return // ยังปกติ ไม่ต้องแจ้ง

        val smtpServer = SqlConnectionVariable.smtpServer
        val smtpPort   = SqlConnectionVariable.smtpPort
        val smtpUser   = SqlConnectionVariable.smtpUser
        val smtpPass   = SqlConnectionVariable.smtpPassword
        val recipient  = SqlConnectionVariable.notificationRecipient
        val useSsl     = SqlConnectionVariable.smtpEnableSsl

        if (smtpServer.isBlank() || recipient.isBlank()) {
            Log.w("EmailNotification", "SMTP Server or Recipient not configured")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val props = Properties().apply {
                    put("mail.smtp.host", smtpServer)
                    put("mail.smtp.port", smtpPort)
                    put("mail.smtp.auth", "true")
                    if (useSsl) {
                        put("mail.smtp.socketFactory.port", smtpPort)
                        put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                        put("mail.smtp.ssl.enable", "true")
                    } else {
                        put("mail.smtp.starttls.enable", "true")
                    }
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(smtpUser, smtpPass)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(smtpUser))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
                    subject = "[TEAMS-NOTIFY] | PartStockAlert"

                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    val messageCard = JSONObject().apply {
                        put("@type", "MessageCard")
                        put("@context", "http://schema.org/extensions")
                        put("themeColor", "FF0000")
                        put("summary", "LOW STOCK ALERT: PartStockAlert from MT01-Android-App")

                        val section = JSONObject().apply {
                            put("activityTitle", "LOW STOCK ALERT: PartStockAlert")
                            put("activitySubtitle", "Time: $timestamp")
                            put("text", "Inventory levels have fallen below the defined minimum threshold during an issue transaction.")

                            val facts = JSONArray().apply {
                                put(JSONObject().apply { put("name", "Part Name"); put("value", stock.partName) })
                                put(JSONObject().apply { put("name", "Part Code"); put("value", stock.partCode) })
                                put(JSONObject().apply { put("name", "Current Quantity"); put("value", "${remainingQty.toInt()} ${stock.unit}") })
                                put(JSONObject().apply { put("name", "Minimum Stock"); put("value", "${stock.minStock.toInt()} ${stock.unit}") })
                                put(JSONObject().apply { put("value", stock.locId); put("name", "Location") })
                                put(JSONObject().apply { put("name", "Alert Source"); put("value", "MT01-Android-App") })
                            }
                            put("facts", facts)
                        }

                        put("sections", JSONArray().put(section))
                    }

                    setText(messageCard.toString())
                }

                Transport.send(message)
                Log.i("EmailNotification", "Notification email sent successfully to $recipient")

            } catch (e: MessagingException) {
                Log.e("EmailNotification", "Failed to send email: ${e.message}", e)
            } catch (e: Exception) {
                Log.e("EmailNotification", "Unexpected error sending email: ${e.message}", e)
            }
        }
    }

    private fun sendTeamsAlertIfNeeded(stock: StockInfo, remainingQty: Double) {
        /*
        // TODO: Disabled during transition to SMTP Email Notifications.
        if (remainingQty >= stock.minStock) return          // ยังปกติ ไม่ต้องแจ้ง

        val webhookUrl = SqlConnectionVariable.teamsWebhookUrl

        if (webhookUrl.isBlank()) {
            Log.w("TeamsAlert", "Webhook URL not configured")
            return
        }

        if (!webhookUrl.startsWith("http")) {
            Log.w("TeamsAlert", "Invalid webhook URL: $webhookUrl")
            return
        }

        // fire-and-forget — ไม่ block UI และไม่กระทบ flow หลัก
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val message = buildString {
                    append("⚠️ **Low Stock Alert — MT01**\n\n")
                    append("**${stock.partName}**")
                    if (stock.partCode.isNotBlank()) append(" (${stock.partCode})")
                    append("\n")
                    append("📦 คงเหลือ: **${remainingQty.toInt()} ${stock.unit}**")
                    append(" | Min: ${stock.minStock.toInt()} ${stock.unit}")
                    append("\n📍 Location: ${stock.locId}")
                }

                val json = """{"text":"$message"}"""

                val request = Request.Builder()
                    .url(webhookUrl)
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        android.util.Log.w("TeamsAlert", "Webhook response: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TeamsAlert", "Failed to send alert: ${e.message}")
                // fail silently — ไม่กระทบ user
            }
        }
        */
    }

    private fun resetForm() {
        currentStock              = null
        selectedDate              = todayString()
        layoutPartInfo.visibility = View.GONE
        etManualInput.setText("")
        qtyHandler.setValue(1.0)
        etRemark.setText("")
        tvTxnDate.text            = selectedDate
        btnConfirm.isEnabled      = false
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private fun showError(msg: String) {
        MaterialAlertDialogBuilder(this)
            .setMessage(msg)
            .setPositiveButton("ตกลง", null)
            .show()
    }

    private fun showSuccess(msg: String) {
        MaterialAlertDialogBuilder(this)
            .setMessage(msg)
            .setPositiveButton("ตกลง", null)
            .show()
    }

    private fun todayString() =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun millisToDateString(ms: Long) =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ms))

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}