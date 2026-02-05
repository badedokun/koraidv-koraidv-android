# Kora IDV Android SDK

Native Android SDK for identity verification with document capture, selfie capture, and liveness detection.

## Requirements

- Android API 24+ (Android 7.0)
- Kotlin 2.0.0+
- Jetpack Compose (BOM 2024.02.00+)
- AndroidX enabled

## Installation

### JitPack (Recommended)

**Step 1:** Add JitPack repository to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**Step 2:** Add the dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.badedokun:koraidv-koraidv-android:v1.0.2")
}
```

**Step 3:** Ensure AndroidX is enabled in `gradle.properties`:

```properties
android.useAndroidX=true
android.enableJetifier=true
```

### Gradle Version Catalog (Optional)

If using version catalogs, add to `libs.versions.toml`:

```toml
[versions]
koraidv = "v1.0.2"

[libraries]
koraidv = { group = "com.github.badedokun", name = "koraidv-koraidv-android", version.ref = "koraidv" }
```

Then in `build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.koraidv)
}
```

## Quick Start

### 1. Initialize the SDK

```kotlin
import com.koraidv.sdk.KoraIDV
import com.koraidv.sdk.Configuration
import com.koraidv.sdk.Environment

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        KoraIDV.configure(
            Configuration(
                apiKey = "kora_your_api_key_here",
                tenantId = "your-tenant-uuid",
                environment = Environment.SANDBOX  // Use PRODUCTION for live
            )
        )
    }
}
```

### 2. Register Activity Result Launcher

```kotlin
import com.koraidv.sdk.KoraIDV
import com.koraidv.sdk.VerificationRequest
import com.koraidv.sdk.VerificationResult

class MainActivity : ComponentActivity() {

    private val verificationLauncher = registerForActivityResult(
        KoraIDV.VerificationContract()
    ) { result ->
        when (result) {
            is VerificationResult.Success -> {
                val verification = result.verification
                Log.d("KoraIDV", "Verification ID: ${verification.id}")
                Log.d("KoraIDV", "Status: ${verification.status}")
            }
            is VerificationResult.Failure -> {
                val error = result.exception
                Log.e("KoraIDV", "Error Code: ${error.errorCode}")
                Log.e("KoraIDV", "Error Message: ${error.message}")
            }
            is VerificationResult.Cancelled -> {
                Log.d("KoraIDV", "User cancelled verification")
            }
        }
    }

    fun startVerification() {
        verificationLauncher.launch(
            VerificationRequest(
                externalId = "user-123",
                tier = VerificationTier.STANDARD
            )
        )
    }
}
```

### 3. Resume an Existing Verification

```kotlin
verificationLauncher.launch(
    VerificationRequest(
        verificationId = "ver_existing_id"  // Resume existing verification
    )
)
```

## Configuration Options

| Option | Type | Description |
|--------|------|-------------|
| `apiKey` | String | Your Kora IDV API key (required, starts with `kora_`) |
| `tenantId` | String | Your tenant UUID (required) |
| `environment` | Environment | `PRODUCTION` or `SANDBOX` |
| `documentTypes` | List<DocumentType> | Allowed document types |
| `livenessMode` | LivenessMode | `PASSIVE` or `ACTIVE` |
| `theme` | KoraTheme | UI customization |
| `timeoutSeconds` | Int | Session timeout in seconds |

## Supported Documents

### Priority 1 (v1.0)
- US Passport
- US Driver's License
- US State ID
- International Passport
- UK Passport
- EU ID Cards (Germany, France, Spain, Italy)

### Priority 2 (v1.1)
- Ghana Card
- Nigeria NIN
- Kenya ID
- South Africa ID

## Theme Customization

```kotlin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val config = Configuration(
    apiKey = "kora_xxx",
    tenantId = "tenant-uuid",
    environment = Environment.SANDBOX,
    theme = KoraTheme(
        primaryColor = Color(0xFF2563EB),
        backgroundColor = Color.White,
        textColor = Color(0xFF1E293B),
        secondaryTextColor = Color(0xFF64748B),
        borderColor = Color(0xFFE2E8F0),
        successColor = Color(0xFF16A34A),
        errorColor = Color(0xFFDC2626),
        warningColor = Color(0xFFF59E0B),
        cornerRadius = 12.dp
    )
)

KoraIDV.configure(config)
```

## Error Handling

```kotlin
is VerificationResult.Failure -> {
    val exception = result.exception

    // Get error code
    val errorCode = exception.errorCode  // e.g., "NETWORK_ERROR"

    // Get human-readable message
    val message = exception.message  // e.g., "Network error: ..."

    when (exception) {
        is KoraException.NetworkError -> {
            // Handle network issues
        }
        is KoraException.CameraAccessDenied -> {
            // Prompt user to enable camera
        }
        is KoraException.SessionExpired -> {
            // Session timed out, restart verification
        }
        is KoraException.UserCancelled -> {
            // User cancelled the flow
        }
        else -> {
            // Show generic error
        }
    }

    // Show recovery suggestion if available
    exception.recoverySuggestion?.let { suggestion ->
        showMessage(suggestion)
    }
}
```

## Permissions

Add to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />

<uses-feature android:name="android.hardware.camera" android:required="true" />
<uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
```

The SDK automatically handles runtime camera permission requests.

## ProGuard Rules

If you're using ProGuard/R8, add these rules to `proguard-rules.pro`:

```proguard
-keep class com.koraidv.sdk.** { *; }
-keepclassmembers class com.koraidv.sdk.** { *; }
```

## Localization

The SDK supports English and French out of the box. To add additional languages, provide translations in your app's `res/values-XX/strings.xml`.

## Troubleshooting

### JitPack Build Fails

If JitPack shows "Tag or commit not found":
1. Ensure the repository is public
2. Use a tagged release (e.g., `v1.0.2`) instead of a commit hash
3. Visit https://jitpack.io/#badedokun/koraidv-koraidv-android to check build status

### Kotlin Version Mismatch

The SDK requires Kotlin 2.0.0+. If you see compose compiler errors:

```kotlin
// In build.gradle.kts (project level)
plugins {
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}
```

### AndroidX Not Enabled

Ensure `gradle.properties` contains:

```properties
android.useAndroidX=true
android.enableJetifier=true
```

### Dependency Resolution Failed

If you see "Could not find com.github.badedokun:koraidv-koraidv-android":
1. Verify JitPack repository is in `settings.gradle.kts` (not just `build.gradle.kts`)
2. Sync project with Gradle files
3. Check internet connection and try again

## Example Integration (Flutter/Native Bridge)

```kotlin
// In MainActivity.kt
import com.koraidv.sdk.KoraIDV
import com.koraidv.sdk.Configuration
import com.koraidv.sdk.Environment

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "koraidv")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "configure" -> {
                        val apiKey = call.argument<String>("apiKey")!!
                        val tenantId = call.argument<String>("tenantId")!!
                        val env = call.argument<String>("environment")!!

                        KoraIDV.configure(
                            Configuration(
                                apiKey = apiKey,
                                tenantId = tenantId,
                                environment = if (env == "sandbox") Environment.SANDBOX else Environment.PRODUCTION
                            )
                        )
                        result.success(null)
                    }
                    "startVerification" -> {
                        // Launch verification activity
                    }
                }
            }
    }
}
```

## Changelog

### v1.0.2
- Fixed deprecated `Icons.Default.KeyboardArrowLeft` (use AutoMirrored version)
- Requires Kotlin 2.0.0+
- JitPack build support

### v1.0.1
- Initial JitPack release
- Fixed kotlin-parcelize plugin configuration
- Fixed AndroidX compatibility
- Fixed Parcelable/Serializable ambiguity in error handling

### v1.0.0
- Initial release
- Document capture with ML Kit detection
- Selfie capture with face detection
- Active and passive liveness detection
- MRZ reading for passports

## License

Copyright 2025 Kora IDV. All rights reserved.
