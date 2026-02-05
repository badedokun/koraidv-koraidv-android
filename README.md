# Kora IDV Android SDK

Native Android SDK for identity verification with document capture, selfie capture, and liveness detection.

## Requirements

- Android API 24+ (Android 7.0)
- Kotlin 1.9+
- Jetpack Compose 1.5+

## Installation

### Gradle (Maven Central)

Add to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.koraidv:koraidv-sdk:1.0.0")
}
```

### JitPack (Alternative)

Add JitPack repository to your root `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}
```

Then add the dependency:

```kotlin
dependencies {
    implementation("com.github.koraidv:koraidv-android:1.0.0")
}
```

## Quick Start

### 1. Initialize the SDK

```kotlin
import com.koraidv.sdk.KoraIDV
import com.koraidv.sdk.Configuration

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        KoraIDV.initialize(
            Configuration(
                apiKey = "kora_your_api_key_here",
                tenantId = "your-tenant-uuid",
                environment = Environment.PRODUCTION
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
                Log.e("KoraIDV", "Error: ${result.exception.message}")
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
| `apiKey` | String | Your Kora IDV API key (required) |
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
- EU ID Cards

### Priority 2 (v1.1)
- Ghana Card
- Nigeria NIN
- Kenya ID
- South Africa ID

## Theme Customization

```kotlin
val config = Configuration(
    apiKey = "kora_xxx",
    tenantId = "tenant-uuid",
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
```

## Localization

The SDK supports English and French out of the box. To add additional languages, provide translations in your app's `res/values-XX/strings.xml`.

## Error Handling

```kotlin
is VerificationResult.Failure -> {
    val exception = result.exception

    when (exception.code) {
        KoraErrorCode.NETWORK_ERROR -> {
            // Handle network issues
        }
        KoraErrorCode.CAMERA_PERMISSION_DENIED -> {
            // Prompt user to enable camera
        }
        KoraErrorCode.SESSION_EXPIRED -> {
            // Session timed out, restart verification
        }
        else -> {
            // Show generic error
        }
    }

    // Show recovery suggestion if available
    exception.recoverySuggestion?.let { suggestion ->
        showMessage(suggestion)
    }

    // Retry if possible
    if (exception.isRetryable) {
        showRetryOption()
    }
}
```

## Permissions

The SDK automatically handles camera permissions. Add to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />

<uses-feature android:name="android.hardware.camera" android:required="true" />
<uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
```

## ProGuard Rules

If you're using ProGuard, add these rules:

```proguard
-keep class com.koraidv.sdk.** { *; }
-keepclassmembers class com.koraidv.sdk.** { *; }
```

## License

Copyright 2025 Kora IDV. All rights reserved.
