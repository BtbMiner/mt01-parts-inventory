package com.kemtdm.mt01.data

// ============================================================
// MT01 Inventory Models
// เพิ่มต่อท้าย DataModels.kt (ไม่แตะ model เดิม)
// ============================================================

// --- Master Data ---

data class PartItem(
    val partId: String,
    val partCode: String,
    val partName: String,
    val brand: String?,
    val model: String?,
    val category: String,       // SPARE / EQUIP / TOOL
    val unit: String,
    val minStock: Double,
    val description: String?,
    val isActive: Boolean
)

data class Location(
    val locId: String,
    val rack: String,
    val floor: Int,
    val slot: Int,
    val block: Int,             // 0 = เต็มล็อค, 1-n = บล็อคย่อย
    val description: String?,
    val isActive: Boolean
)

data class PartSupplier(
    val id: Int,
    val partId: String,
    val supId: String,
    val partNoSup: String?,     // รหัสที่ Supplier ใช้เรียก Part นี้
    val remark: String?
)

// --- Stock ---

data class StockInfo(
    val stockId: Int,
    val partId: String,
    val locId: String,
    val qtyOnHand: Double,
    val lastUpdated: String,
    // joined fields (อ่านมาพร้อมกันใน query เดียว)
    val partCode: String,
    val partName: String,
    val brand: String?,
    val model: String?,
    val category: String,
    val unit: String,
    val minStock: Double,
    val locDescription: String?
) {
    // คำนวณสถานะ Stock
    val stockStatus: StockStatus
        get() = when {
            qtyOnHand <= 0            -> StockStatus.OUT
            qtyOnHand < minStock      -> StockStatus.LOW
            else                      -> StockStatus.NORMAL
        }
}

enum class StockStatus { NORMAL, LOW, OUT }

// --- Transaction ---

data class TxnRecord(
    val txnId: Int,
    val txnType: String,        // ✅ IN / OUT / RET / MOV
    val partId: String,
    val locId: String,

    val locFrom: String?,       // ✅ ใหม่
    val locTo: String?,         // ✅ ใหม่

    val qty: Double,
    val txnDate: String,
    val remark: String?,
    val createdBy: String,
    val createdAt: String,
    val deviceInfo: String?,

    val partName: String,
    val partCode: String,
    val unit: String
)

// ใช้ตอนสร้าง Transaction ใหม่ (ส่งเข้า Repository)
data class TxnInput(
    val txnType: String,
    val partId: String,
    val locId: String,

    val locFrom: String? = null,   // ✅ ใหม่
    val locTo: String? = null,     // ✅ ใหม่

    val qty: Double,
    val txnDate: String,
    val remark: String?,
    val createdBy: String,
    val deviceInfo: String?
)