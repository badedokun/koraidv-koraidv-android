# KoraIDV SDK Consumer ProGuard Rules
# These rules are automatically included when a consumer app uses the SDK with R8/ProGuard.

# === Gson serialization ===
# Keep all API request/response models that use @SerializedName annotations.
# Without these rules, R8 strips/renames fields and Gson deserialization silently fails.

-keep class com.koraidv.sdk.api.CreateVerificationRequest { *; }
-keep class com.koraidv.sdk.api.UploadDocumentRequest { *; }
-keep class com.koraidv.sdk.api.UploadDocumentBackRequest { *; }
-keep class com.koraidv.sdk.api.UploadSelfieRequest { *; }
-keep class com.koraidv.sdk.api.SubmitLivenessChallengeRequest { *; }

-keep class com.koraidv.sdk.api.VerificationResponse { *; }
-keep class com.koraidv.sdk.api.VerificationScoresResponse { *; }
-keep class com.koraidv.sdk.api.DocumentVerificationResponse { *; }
-keep class com.koraidv.sdk.api.FaceVerificationResponse { *; }
-keep class com.koraidv.sdk.api.LivenessVerificationResponse { *; }
-keep class com.koraidv.sdk.api.ChallengeResultResponse { *; }
-keep class com.koraidv.sdk.api.RiskSignalResponse { *; }
-keep class com.koraidv.sdk.api.DocumentUploadResponse { *; }
-keep class com.koraidv.sdk.api.OcrResultResponse { *; }
-keep class com.koraidv.sdk.api.SelfieUploadResponse { *; }
-keep class com.koraidv.sdk.api.FaceMatchResponse { *; }
-keep class com.koraidv.sdk.api.LivenessSessionResponse { *; }
-keep class com.koraidv.sdk.api.LivenessChallengeDto { *; }
-keep class com.koraidv.sdk.api.LivenessChallengeResponse { *; }
-keep class com.koraidv.sdk.api.DocumentTypesResponse { *; }
-keep class com.koraidv.sdk.api.DocumentTypeInfoResponse { *; }
-keep class com.koraidv.sdk.api.CountryResponse { *; }

# === Parcelable models (passed via Intent extras) ===
-keep class com.koraidv.sdk.Verification { *; }
-keep class com.koraidv.sdk.VerificationRequest { *; }
-keep class com.koraidv.sdk.DocumentVerification { *; }
-keep class com.koraidv.sdk.FaceVerification { *; }
-keep class com.koraidv.sdk.LivenessVerification { *; }
-keep class com.koraidv.sdk.ChallengeResult { *; }
-keep class com.koraidv.sdk.RiskSignal { *; }
-keep class com.koraidv.sdk.VerificationScores { *; }
-keep class com.koraidv.sdk.FieldError { *; }

# === Sealed exception hierarchy (Parcelable, passed via Intent) ===
-keep class com.koraidv.sdk.KoraException { *; }
-keep class com.koraidv.sdk.KoraException$* { *; }

# === Enums (used in Parcelable and API serialization) ===
-keepclassmembers enum com.koraidv.sdk.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# === Public API entry points ===
-keep class com.koraidv.sdk.KoraIDV { *; }
-keep class com.koraidv.sdk.KoraIDV$VerificationContract { *; }
-keep class com.koraidv.sdk.KoraIDV$ResumeVerificationContract { *; }
-keep class com.koraidv.sdk.Configuration { *; }
-keep class com.koraidv.sdk.KoraTheme { *; }
-keep class com.koraidv.sdk.VerificationResult { *; }
-keep class com.koraidv.sdk.VerificationResult$* { *; }
-keep class com.koraidv.sdk.VerificationTier { *; }
-keep class com.koraidv.sdk.DocumentType { *; }
-keep class com.koraidv.sdk.Environment { *; }
-keep class com.koraidv.sdk.LivenessMode { *; }
-keep class com.koraidv.sdk.VerificationStatus { *; }

# === Retrofit ===
# Keep Retrofit interface methods (annotations used at runtime via reflection)
-keep,allowobfuscation interface com.koraidv.sdk.api.ApiService
-keepclassmembers interface com.koraidv.sdk.api.ApiService { *; }

# === Gson generic type tokens ===
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
