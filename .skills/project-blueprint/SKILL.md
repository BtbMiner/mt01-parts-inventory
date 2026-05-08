---
skill: Project Blueprint
description: Standard architectural blueprint and coding standards for the MT01 Inventory Management System.
category: Android Development
tags: [Material3, Kotlin, SQLServer, jTDS, XML]
---

# Project Blueprint: MT01 Inventory Management

This blueprint defines the core standards, architectural patterns, and UI systems used in this project to ensure consistency and maintainability.

## 🎨 UI & Theme Design

### Color System & Theming
- **Material 3 (M3)**: The project is fully migrated to Material 3 using `Theme.Material3.DayNight`.
- **Semantic Attributes**: Avoid hardcoded colors. Use theme attributes (`?attr/`) to support Light/Dark mode automatically.
  - **Custom Attributes**: Defined in `attrs.xml` for domain-specific colors:
    - `colorTxnIn`: Success/Receive transactions.
    - `colorTxnOut`: Warning/Issue transactions.
    - `colorTxnReturn`: Information/Return transactions.
    - `colorPageBackground` & `colorCardBackground`: Layout layering.
- **Surface Layering**: Use `MaterialCardView` with `?attr/materialCardViewElevatedStyle` for content containers.

### Layout Management
- **XML Layouts**: The project primary uses XML layouts with `CoordinatorLayout` and `NestedScrollView` for flexible, scrollable forms.
- **Component Consistency**:
  - **Buttons**: `Widget.Material3.Button` for primary actions, `TonalButton` for secondary.
  - **Inputs**: `Widget.Material3.TextInputLayout.OutlinedBox` for all form fields.
  - **Toolbar**: Standardized height (`?attr/actionBarSize`) and background matching the transaction context.

## 🏗 Architecture & Database

### Architecture Patterns
- **Repository Pattern**: Centralized data access logic in `object` repositories (e.g., `StockRepository`, `TxnRepository`).
- **Activity-Logic Separation**: Activities handle UI state and user input, while business logic and data processing reside in Repositories.
- **Concurrency**: All data operations use Kotlin Coroutines with `Dispatchers.IO`.

### Database Connection
- **Direct SQL Server Connection**: Uses `net.sourceforge.jtds:jtds` driver to connect directly to Microsoft SQL Server.
- **Transaction Safety**: 
  - Manual transaction management using `connection.autoCommit = false`.
  - Row-level locking with `WITH (UPDLOCK)` to prevent race conditions during stock updates.
  - Snapshot-based `MT_AUDIT_LOG` for tracking changes.

## 🛠 Error Handling & Validation

### Error Trapping
- **SQL Level**: Extensive `try-catch` blocks in repositories capturing `SQLException` with detailed logging (SQLState, ErrorCode).
- **UI Level**: Use `MaterialAlertDialogBuilder` for user-facing error messages and confirmation dialogs.
- **Validation**: 
  - Input validation (e.g., non-zero quantity, stock availability) performed before repository calls.
  - Soft-validation via `isEnabled` states on buttons.

### Testing
- **Manual Verification**: Features are verified against live SQL Server data.
- **Logging**: Heavy use of `Log.d` and `Log.e` with specific tags (e.g., `tagMainActivity`) for runtime debugging.

## 📌 Project Constraints

### Specific Rules
- **Preservation**: Existing working modules (`Receive`, `Issue`, `Return`) must maintain their functional logic.
- **Role-Based Access**: Logic must differentiate between **Authority** (Admin) and **Normal User** using `SessionManager.isAdmin()`.
- **Dependency Management**: Centralized via `gradle/libs.versions.toml`.

### Required Libraries
- **ZXing Android Embedded**: For QR/Barcode scanning functionality.
- **jTDS**: For legacy SQL Server compatibility.
- **Material Components**: For M3 UI elements.
- **Coroutines**: For asynchronous operations.
