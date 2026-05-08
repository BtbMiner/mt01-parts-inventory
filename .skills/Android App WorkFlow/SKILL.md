# Android App WorkFlow — Inventory Management System

This document summarizes the development workflow, architectural rules, and code patterns used in the MT01 Inventory project.

## 🏗 Architectural Overview
The project follows a simplified **Clean Architecture** pattern suitable for direct database interaction:
- **Activity/UI**: Handles user interaction and view binding.
- **Repository**: Contains SQL logic and data transformation using Coroutines (`Dispatchers.IO`).
- **Data Model**: Simple Kotlin data classes.
- **Theme System**: Material 3 with semantic custom attributes.

---

## 🛠 Workflow for Adding New Modules

1.  **Define Requirements**: Identify the SQL tables involved (`MT_STOCK`, `MT_TXN`, etc.).
2.  **Registration**: Add the Activity to `AndroidManifest.xml`.
3.  **Layout (`res/layout`)**:
    - Use Material 3 components.
    - Avoid hardcoded colors; use `?attr/colorPrimary` or custom attributes from `attrs.xml`.
    - Use `NestedScrollView` for form-heavy screens.
4.  **Data Logic (`data/`)**:
    - Extend existing repositories or create new ones.
    - Use `withContext(Dispatchers.IO)` for all SQL operations.
5.  **Implementation (`activity/`)**:
    - Bind views and setup listeners.
    - Use `lifecycleScope.launch` to call repository functions.
    - Handle errors using `MaterialAlertDialogBuilder`.

---

## 🎨 UI & Theme Rules

### 1. Color Attributes
Always use semantic attributes instead of raw hex or color resources:
- `?attr/colorPageBackground`: Main screen background.
- `?attr/colorCardBackground`: Background for cards.
- `?attr/colorTxnIn`, `?attr/colorTxnOut`, `?attr/colorTxnReturn`: Contextual status colors.
- `?attr/colorTextPrimary` / `?attr/colorTextSecondary`.

### 2. Material 3 Migration
- **Buttons**: `style="@style/Widget.Material3.Button"`
- **Inputs**: `style="@style/Widget.Material3.TextInputLayout.OutlinedBox"`
- **Cards**: `style="?attr/materialCardViewElevatedStyle"`

---

## 📝 Code Examples

### Repository Pattern (SQL Server)
```kotlin
object MyRepository {
    suspend fun getData(id: String): MyModel? = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        try {
            connection = GetConnection.connection ?: return@withContext null
            val sql = "SELECT * FROM TABLE WHERE ID = ?"
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) return@withContext rs.toModel()
                }
            }
        } catch (e: SQLException) {
            Log.e("MyRepository", e.message, e)
        } finally {
            connection?.close()
        }
        return@withContext null
    }
}
```

### Theme-Aware Color in Kotlin
```kotlin
val color = MaterialColors.getColor(context, R.attr.colorTxnIn, Color.GREEN)
textView.setTextColor(color)
```

### QR Scanning
```kotlin
private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
    result.contents?.let { scannedData ->
        // Handle scanned data
    }
}

private fun startScan() {
    scanLauncher.launch(ScanOptions().apply {
        setPrompt("Scan QR Code")
        setBeepEnabled(true)
    })
}
```

---

## 🔐 Security & Roles
- Store `UserID`, `UserName`, and `IsAdmin` in `SharedPreferences` via `SessionManager`.
- Check `SessionManager.isAdmin(context)` before allowing access to Authority-only features (e.g., Receive module, detailed logs).
- Implement session timeout checks on app launch or `onResume`.
