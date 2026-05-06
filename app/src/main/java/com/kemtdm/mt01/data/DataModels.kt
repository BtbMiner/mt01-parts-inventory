package com.kemtdm.mt01.data

// Data class to store Equipment information.
data class Equipment(
    val equipmentId: Int,
    val equipmentName: String
)

// Data class to store specific Checklist Item details for a given KTS_NO and Equipment.
data class ChecklistItem(
    val ktsNo: String,
    val establishedWeek: String? = null, // เพิ่ม field นี้เข้ามา
    val equipmentId: Int,
    val checkId: Int,
    val checkPoint: String,
    val checkPeriod: Int,
    val selectedWeek: Int,
    var selectedResult: Int = 0, // 0=Not selected, 1=Normal, 2=Slightly Abnormal, 3=Critical Abnormal
    var additionalNote: String? = null,
    var isEnabled: Boolean = true,
    var yearMonth: String = "",
    var checkedWeek: Int = 0
)

// Data class to combine an Equipment with its associated checklist items for display.
data class EquipmentWithChecklist(
    val equipment: Equipment,
    val checklistItems: MutableList<ChecklistItem> // ใช้ MutableList เพื่อให้แก้ไข selectedResult ได้
)


// Data class to hold the summary information for a saved form.
data class SavedFormHeader(
    val ktsNo: String,
    val establishedWeek: String, // เพิ่ม field นี้เข้ามา
    val yearMonthFormatted: String,
    val checkedWeek: Int,
    val checkedBy: String?,
    val checkedDate: String?,
    val latestCheckedBy: String?,
    val latestCheckedDate: String?,
    val checkedItems: Int,
    val totalItems: Int,
    val verifiedBy: String?,
    val verifiedDate: String?,
    val approvedBy: String?,
    val approvedDate: String?
)