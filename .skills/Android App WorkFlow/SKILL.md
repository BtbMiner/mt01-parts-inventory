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

---

## 🌍 Multi-Language (Localization)

The project implements a simple, persistent multi-language system using a `LanguageManager` and a `BaseActivity` pattern.

### 1. Folder Structure
Localization follows the standard Android resource qualification:
- `app/src/main/res/values/strings.xml` (Default/English)
- `app/src/main/res/values-th/strings.xml` (Thai)

### 2. Implementation Approach
- **Persistence**: Selected language code ("en", "th") is stored in `SharedPreferences`.
- **Consistency**: All Activities must inherit from `BaseActivity` to ensure the locale is applied correctly via `attachBaseContext`.
- **Dynamic Switching**: When the language is changed, the Activity must be recreated (`recreate()`) or the app restarted to apply changes globally.

### 3. LanguageManager Utility
```kotlin
object LanguageManager {
    private const val PREFS_NAME = "LanguagePrefs"
    private const val KEY_LANG = "selected_lang"

    fun saveLanguage(context: Context, lang: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, lang).apply()
    }

    fun loadLanguage(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "en") ?: "en"

    fun applyLocale(context: Context): Context {
        val locale = Locale(loadLanguage(context))
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
```

### 4. BaseActivity Integration
```kotlin
abstract class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        // Intercepts context to apply the saved locale before the activity starts
        super.attachBaseContext(LanguageManager.applyLocale(newBase))
    }
}
```

### 💡 Best Practices
- **Never Hardcode Text**: All UI text must be in `strings.xml`. Use `@string/name` in XML and `getString(R.string.name)` in Kotlin.
- **Content Descriptions**: Always provide translations for `android:contentDescription` to support accessibility.
- **String Formatting**: Use placeholders (e.g., `Welcome, %1$s`) for dynamic text.
- **Resource Completeness**: If a string exists in `values/strings.xml`, it **must** have a corresponding entry in `values-th/strings.xml` to avoid build errors or falling back to English unexpectedly.

### ⚠️ Common Pitfalls
- **Resource Linking Errors**: The build will fail if a layout references a `@string` resource that is missing from any localized `strings.xml` file.
- **Context Issues**: Always use `attachBaseContext` in a `BaseActivity`. Using `resources.updateConfiguration` is deprecated and often fails on newer Android versions.
- **Dialogs/Toasts**: Ensure you pass the Activity context (which has the applied locale) to Dialogs and Toasts, not the Application context.

---

## 🏗 Build Configuration

### APK Renaming (The Modern Way)
Directly renaming APKs in modern AGP (8.0+) requires using the `variant.outputs` API within `androidComponents.onVariants`. This ensures the **primary artifact** itself is renamed, rather than just creating a copy.

#### ⚠️ AGP Status: Still "Incubating"
As of AGP 8.x, the `outputFileName` property remains marked as `@Incubating`. While technically "unstable" by Google's definition, it is the **only official way** to rename the output file without resorting to external shell scripts or manual renaming.

```kotlin
// In app/build.gradle.kts
@file:Suppress("UnstableApiUsage") // Required for outputFileName

androidComponents {
    onVariants { variant ->
        val appName = "AD07"
        val date = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date())
        val buildTypeName = variant.buildType ?: variant.name

        // ✅ Correct way to rename the ACTUAL output artifact
        variant.outputs.forEach { output ->
            output.outputFileName.set("${appName}-${buildTypeName}-${date}.apk")
        }
        
        // Optional: Copy to a specialized folder for CI/CD
        val capitalizedVariantName = variant.name.replaceFirstChar { it.uppercase() }
        val copyTask = tasks.register<Copy>("copy${capitalizedVariantName}Apk") {
            val apkFolder = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK)
            from(apkFolder)
            include("*.apk")
            destinationDir = file("${project.layout.buildDirectory.get()}/outputs/custom-apk")
        }
        
        tasks.matching { it.name == "assemble$capitalizedVariantName" }.configureEach {
            finalizedBy(copyTask)
        }
    }
}
```

#### Root Cause: Why Copy Tasks alone fail
- A `Copy` task runs **after** the APK is generated.
- It creates a **new file** with the new name but leaves the **original artifact** (e.g., `app-debug.apk`) unchanged in its default location.
- Tools like Android Studio, Firebase App Distribution, or Play Console look at the **Artifact API metadata**, which still points to the original name if you only use a `Copy` task.

#### Comparison: Artifact Transforms vs. Copy Tasks
| Feature | `variant.outputs` (Renaming) | `Copy` Task |
| :--- | :--- | :--- |
| **Artifact Integrity** | Changes the actual name known to Gradle/AGP. | Creates a disconnected copy. |
| **Tooling Support** | Android Studio "Locate APK" uses the new name. | Studio still points to the old name. |
| **Stability** | Incubating (requires `@Suppress`). | Stable. |
| **Complexity** | Low (internal to AGP). | Medium (requires custom task & lifecycle hook). |

#### 🐘 Gradle: Safe Task Configuration
When working with the Android Gradle Plugin (AGP), referencing tasks during the configuration phase requires caution.

#### Root Cause: `UnknownTaskException`
The `androidComponents.onVariants` block executes during the configuration phase, but often **before** the Android Gradle Plugin has finished registering standard tasks like `assembleDebug` or `assembleRelease`.
- `tasks.named("name")`: Immediately searches for a task with that name. If the task hasn't been registered yet by AGP, it throws an `UnknownTaskException`.
- `tasks.matching { ... }.configureEach`: Creates a live collection that Gradle monitors. Whenever a task matching the criteria is registered (even later in the configuration phase), the configuration block is executed safely.

#### Fix Summary
1.  **Renaming**: Used `variant.outputs.forEach { it.outputFileName.set(...) }` to modify the primary artifact.
2.  **Safety**: Used `tasks.matching().configureEach` to hook the `assemble` task without causing configuration-time crashes.

**Best Practice:**
1.  Use `variant.outputs` to rename the actual APK so it integrates with Android Studio and other tools.
2.  Use `tasks.matching { ... }.configureEach` when you need to hook into AGP-generated tasks like `assemble`.
3.  Avoid `applicationVariants.all` as it is deprecated and doesn't work well with the newer Artifacts API.

---

### Stable vs Incubating APIs
| API | Status | Recommendation |
| :--- | :--- | :--- |
| `applicationVariants` | **Deprecated** | Do not use; replaced by `androidComponents`. |
| `variant.outputs` | **Incubating** | Functional and necessary for renaming; use with `@Suppress`. |
| `Artifacts` (SingleArtifact.APK) | **Stable** | Best practice for accessing build outputs for copying. |
| `base.archivesName` | **Stable** | Good for setting a global prefix, but cannot handle per-variant timestamps easily. |

**Key Best Practices:**
- **Prefer Copy Tasks**: Renaming "in-place" often breaks Gradle's build caching. Copying to a separate directory (e.g., `/outputs/renamed-apk`) is safer.
- **Lazy Configuration**: Always use `tasks.register` instead of `tasks.create`.
- **Avoid Internal Classes**: Never cast to `com.android.build.gradle.internal.*`.
