---
project: MT01 Inventory System
platform: Android (Kotlin + XML)
architecture: Repository-based (Activity-centric)
database: Microsoft SQL Server (Direct JDBC via jTDS)
ui: Material 3 (XML + DayNight Support)
---

# 🎨 UI & Theme Design

### Color System
- **Material 3 Usage**: Migrated to `Theme.Material3.DayNight`.
- **Theme Attributes**: Use `?attr/` for all color references to ensure automatic Light/Dark theme support.
- **Custom Attributes**: Defined in `attrs.xml` for contextual clarity (e.g., `colorTxnIn`, `colorTxnOut`).

### Layout Management
- **XML-Based**: Traditional XML layouts using `CoordinatorLayout` and `NestedScrollView`.
- **UX Consistency**: 
    - Consistent padding (16dp) and component styling across all modules.
    - **Standardized Search**: Manual search inputs now consistently support flexible matching (ID, Code, Name) and zero-padding.
    - **Action Feedback**: Uniform use of `MaterialAlertDialogBuilder` for success/error/selection dialogs.

# 🏗 Architecture & Database

### Architecture Pattern
- **Repository-based**: Business logic and data access are centralized in singleton `object` repositories.
- **Unified Search Pattern**: 
    - The `StockRepository.searchStockFlexible()` method consolidates multi-step search logic (Direct ID match -> Keyword fallback).
    - Activities consume a sealed `SearchResult` class to handle single, multiple, or zero matches consistently.
- **Data Normalization**: Centralized `normalizeId()` logic ensures numeric inputs (e.g., "1") match database formats (e.g., "0001") across all modules.

### Database & Tables
- **Connection**: Direct JDBC connection using the `jTDS` driver.
- **Atomic Operations**: Manual transaction control (`autoCommit = false`) with mandatory `commit()` or `rollback()`.
- **Concurrency**: Asynchronous data operations powered by Kotlin Coroutines (`lifecycleScope` and `Dispatchers.IO`).

# 🛠 Error Handling & Validation

### SQL & Transaction Control
- **Try/Catch**: Global SQL error trapping with detailed logging of `SQLState` and `ErrorCode`.
- **Locking**: Row-level locking using `WITH (UPDLOCK)` to prevent race conditions.

### Validation Logic
- **Input Sanitization**: Uniform use of `.trim()` and `normalizeId()` before database queries to handle leading/trailing spaces and formatting mismatches.
- **Safe Handling**: Standardized check for empty inputs and "not found" states with appropriate user feedback.

# 📌 Project Constraints
- **Preserve Logic**: Do not break existing transaction logic (Receive/Issue/Return).
- **Code Reuse**: Centralize logic in Repositories; Activities must not contain raw SQL or complex data transformation logic.
- **Role-based Access**: Differentiate functionality between **Authority** (Admin) and **Normal User** (Staff).
- **Theme Support**: All new UI must support both Light and Dark modes via semantic theme attributes.
