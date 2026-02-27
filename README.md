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
    implementation("com.github.badedokun:koraidv-koraidv-android:v1.1.0")
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
koraidv = "v1.1.0"

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
| `baseUrl` | String? | Custom base URL override (e.g., for self-hosted deployments) |
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

## Image Persistence & Compliance (v1.1.0+)

Starting with v1.1.0, KoraIDV automatically persists all captured images (document front/back, selfie, liveness frames) to secure cloud storage as part of the verification pipeline. This is required for regulatory compliance — regulators (FinCEN, state MSB examiners) can request examination of original identity documents at any time.

### How It Works

Image persistence is **fully automatic** and requires no changes to your integration. When your end-user captures a document, takes a selfie, or completes a liveness challenge, the image is uploaded to KoraIDV's secure storage before the response is returned.

Each upload response now includes an `imagePersisted` field confirming durable storage:

```kotlin
// The SDK handles this internally — these are the server response models
// DocumentUploadResponse.imagePersisted  → Boolean?
// SelfieUploadResponse.imagePersisted    → Boolean?
// LivenessChallengeResponse.imagePersisted → Boolean?
```

- `true` — Image was successfully persisted to cloud storage
- `false` or `null` — Image was not stored (sandbox mode, or server not configured)

### Retrieving Images for Your Compliance Dashboard

To display captured images in your own admin/compliance dashboard, use the tenant-scoped image retrieval API. These endpoints are authenticated with your tenant credentials and scoped to your verifications only.

#### Step 1: List Available Images

```
GET /api/v1/verifications/{verificationId}/images
Header: X-Tenant-ID: {your-tenant-uuid}
```

Response:
```json
{
  "images": [
    { "type": "document_front", "available": true },
    { "type": "document_back", "available": true },
    { "type": "selfie", "available": true },
    { "type": "liveness_blink", "available": true }
  ]
}
```

#### Step 2: Get a Signed URL for Each Image

```
GET /api/v1/verifications/{verificationId}/images/{imageType}
Header: X-Tenant-ID: {your-tenant-uuid}
```

Response:
```json
{
  "imageType": "document_front",
  "url": "https://storage.googleapis.com/...",
  "expiresIn": 900
}
```

The signed URL is valid for **15 minutes**. Load it directly in an `ImageView`, Coil/Glide, or web view. Request a new URL after expiry.

#### Valid Image Types

| Image Type | Description |
|------------|-------------|
| `document_front` | Front of the identity document |
| `document_back` | Back of the identity document |
| `selfie` | Selfie photo |
| `liveness_blink` | Liveness challenge: blink |
| `liveness_smile` | Liveness challenge: smile |
| `liveness_turn_left` | Liveness challenge: turn left |
| `liveness_turn_right` | Liveness challenge: turn right |
| `liveness_nod_up` | Liveness challenge: nod up |
| `liveness_nod_down` | Liveness challenge: nod down |

#### Example: Display Images in a Kotlin Admin App

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.net.HttpURLConnection
import java.net.URL

data class ImageInfo(
    val type: String,
    val available: Boolean
)

data class ImageListResponse(
    val images: List<ImageInfo>
)

data class SignedImageURL(
    @SerializedName("imageType") val imageType: String,
    val url: String,
    @SerializedName("expiresIn") val expiresIn: Int
)

class ComplianceImageService(
    private val baseUrl: String,
    private val tenantId: String
) {
    private val gson = Gson()

    /** Fetch the list of available images for a verification */
    suspend fun listImages(verificationId: String): List<ImageInfo> =
        withContext(Dispatchers.IO) {
            val url = URL("$baseUrl/api/v1/verifications/$verificationId/images")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("X-Tenant-ID", tenantId)
            }
            val body = conn.inputStream.bufferedReader().readText()
            gson.fromJson(body, ImageListResponse::class.java).images
        }

    /** Get a signed URL for a specific image */
    suspend fun getImageURL(verificationId: String, imageType: String): SignedImageURL =
        withContext(Dispatchers.IO) {
            val url = URL("$baseUrl/api/v1/verifications/$verificationId/images/$imageType")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("X-Tenant-ID", tenantId)
            }
            val body = conn.inputStream.bufferedReader().readText()
            gson.fromJson(body, SignedImageURL::class.java)
        }
}

// Usage in a Composable:
// val service = ComplianceImageService(baseUrl = "https://api.koraidv.com", tenantId = "your-tenant-uuid")
// val images = service.listImages("ver_xxx")
// for (img in images.filter { it.available }) {
//     val signed = service.getImageURL("ver_xxx", img.type)
//     AsyncImage(model = signed.url, contentDescription = img.type)
// }
```

#### Example: Using Retrofit (Recommended for Production)

```kotlin
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface ComplianceImageApi {

    @GET("api/v1/verifications/{id}/images")
    suspend fun listImages(
        @Path("id") verificationId: String,
        @Header("X-Tenant-ID") tenantId: String
    ): Response<ImageListResponse>

    @GET("api/v1/verifications/{id}/images/{imageType}")
    suspend fun getImageURL(
        @Path("id") verificationId: String,
        @Path("imageType") imageType: String,
        @Header("X-Tenant-ID") tenantId: String
    ): Response<SignedImageURL>
}
```

### Important Notes

- **No backfill:** Only verifications created after v1.1.0 deployment will have stored images.
- **Sandbox mode:** `imagePersisted` will be `false` in sandbox — images are not stored for synthetic test data.
- **Tenant isolation:** You can only access images for your own verifications. Cross-tenant access is not possible.
- **Retention:** Images are retained according to your regulatory requirements (configured server-side).

## Changelog

### v1.1.0
- Added `imagePersisted` field to `DocumentUploadResponse`, `SelfieUploadResponse`, and `LivenessChallengeResponse`
- Confirms whether captured images were durably stored server-side for regulatory compliance
- Aligned version numbering with iOS SDK

### v1.0.3
- Added `baseUrl` configuration option for custom API endpoint override
- Added `kora_sandbox_` API key prefix detection for automatic sandbox environment
- Fixed API connectivity when using self-hosted or Cloud Run deployments

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
