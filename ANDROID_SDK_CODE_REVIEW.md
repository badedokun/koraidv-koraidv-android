# KoraIDV Android SDK — Comprehensive Code Review

**Date:** 2026-02-19
**Reviewer:** Automated Deep Review
**SDK Version:** 1.0.0 (source) / 1.0.4 (deployed AAR)
**Consumer App:** Stratum Remit (orokii-platform)
**Scope:** Full codebase review — 24 Kotlin source files across 6 modules

---

## Executive Summary

The KoraIDV Android SDK is a well-architected identity verification library built on modern Android primitives (CameraX, Jetpack Compose, Coroutines, Activity Result API). The public API is clean and the developer experience is strong. However, the review identified **critical correctness bugs**, **security gaps**, **memory issues**, and a **complete absence of tests** that must be addressed before competing with Jumio, Persona, ID.me, and Sardine at a commercial level.

The Stratum Remit integration (orokii-platform) uses a pre-built AAR with newer transitive dependencies than the SDK was built against, and ships no consumer ProGuard rules — both are reliability risks for the production launch.

---

## 1. Architecture & Design — Grade: A-

### Strengths
- Clean layered architecture: `api/` → `capture/` → `liveness/` → `processing/` → `ui/`
- Excellent public API: `configure()` → `registerForActivityResult()` → `launcher.launch()`
- Well-designed sealed class hierarchies: `VerificationState`, `VerificationResult`, `KoraException`, `LivenessState`
- Proper `internal` visibility — only public types exposed
- Good separation of concerns across `SessionManager`, `VerificationViewModel`, capture classes

### Issues
- **`KoraIDV` singleton has no thread safety** — race conditions on `configuration`/`apiClient`
- **`VerificationViewModel` lacks factory injection** — `SessionManager` created internally, hard to test
- **`CameraManager` is not lifecycle-aware** — callers must remember `release()`, leak-prone

---

## 2. Security — Grade: B+

### Strengths
- API key in header, not query parameter
- `VerificationActivity` is `exported="false"`
- Debug logging gated behind config flag

### Issues
- **No `Bearer` prefix on Authorization header** — violates RFC 6750, risks proxy logging
- **Full body logging in debug mode** — logs base64 selfie/document images to logcat (PII leak)
- **No certificate pinning** — for an IDV product handling biometric data, this is a gap
- **No consumer ProGuard rules shipped** — Gson models will break under R8 minification
- **Base64 images in memory** — 33% overhead, OOM risk on low-end devices

---

## 3. Correctness & Bugs — Grade: B

### Critical Bugs
1. **MRZ format detection overlap** (MrzReader.kt:101-106): TD1 `88..92` overlaps TD3 `86..90`. Passports (TD3) at length 88-90 will be misclassified as ID cards (TD1).
2. **MRZ `O→0` blanket replacement** (MrzReader.kt:85): Converts ALL letter O's to zeros globally, corrupting country codes ("GBR" → "GB0") and names ("OLIVER" → "0LIVER").

### Other Bugs
3. **Deprecated `getParcelableExtra`/`getSerializableExtra`** (KoraIDV.kt:137,142) — deprecated since API 33
4. **`SimpleDateFormat` not thread-safe** (SessionManager.kt:24-26) — called from `Dispatchers.IO` coroutines
5. **`setTargetResolution` deprecated in CameraX 1.3** (CameraManager.kt:73,82,87)
6. **422 error discards validation details** (SessionManager.kt:403) — creates `ValidationError(emptyList())`
7. **Silent ML Kit failures** (DocumentScanner.kt:157-158, FaceScanner.kt:149-151) — no logging
8. **Bitmap leaks in `capturePhoto`** (CameraManager.kt:162-175) — decoded/rotated bitmaps never recycled
9. **`VerificationStatus.fromValue` defaults to PENDING** (Verification.kt:58) — masks unknown statuses
10. **`DocumentVerificationResponse` field naming inconsistency** (ApiService.kt:122-133) — snake_case properties

---

## 4. Performance — Grade: B+

### Strengths
- `STRATEGY_KEEP_ONLY_LATEST` backpressure prevents frame queue buildup
- `isAnalyzing` guard prevents concurrent ML Kit processing
- Camera pre-warming during consent screen
- Background document/selfie uploads during subsequent steps

### Issues
- **Quality validation on full-resolution bitmaps** (QualityValidator.kt:253-299): ~24MB allocations per check for 1920x1080 images
- **Triple `getPixels` calls** for blur/brightness/glare — 3x memory for same data
- **`Thread.sleep` in retry interceptor** (ApiClient.kt:66) — blocks OkHttp dispatcher thread

---

## 5. Error Handling — Grade: A-

### Strengths
- Comprehensive sealed exception hierarchy with error codes and recovery suggestions
- Proper HTTP error mapping (401, 403, 404, 422, 429, 5xx)
- `kotlin.Result` pattern used consistently

### Issues
- 422 errors lose field-level details
- Silent ML Kit failures provide no user feedback
- Corrupt JPEG data from `capturePhoto` passed through silently

---

## 6. Testing — Grade: F

**Zero test files exist.** Test dependencies declared in `build.gradle.kts` but unused. For a commercial IDV SDK competing with Jumio/Persona, this is the single biggest gap.

---

## 7. API Design & Developer Experience — Grade: A

### Strengths
- 3-line integration
- KDoc with code examples
- Auto-environment detection from API key prefix
- Customizable theme, French localization
- `ResumeVerificationContract` for session recovery

### Issues
- No `@JvmStatic` on `KoraIDV.configure()` — awkward Java interop
- `VerificationRequest` uses `Serializable` instead of `Parcelable`
- SDK version hardcoded in two places

---

## 8. Stratum Remit (orokii-platform) Integration Findings

| Issue | Severity |
|-------|----------|
| SDK uses `compileSdk=34`, consumer app uses `compileSdk=36` | Medium |
| SDK built with CameraX 1.3.1, consumer has CameraX 1.4.1 | Medium |
| SDK built with Retrofit 2.9.0, consumer has Retrofit 2.11.0 | Low |
| No consumer ProGuard rules in AAR | **High** |
| AAR version (1.0.4) doesn't match source (1.0.0) | Low |
| Authorization header lacks Bearer prefix — may fail with certain API gateways | Medium |

---

## Priority Fix List

### Must Fix (Before Launch)
1. Add ProGuard/R8 consumer rules for Gson-serialized API models
2. Fix MRZ format detection overlap (TD1 vs TD3)
3. Fix MRZ `O→0` blanket replacement
4. Add thread safety to `KoraIDV` singleton
5. Fix deprecated `getParcelableExtra`/`getSerializableExtra`
6. Replace `SimpleDateFormat` with thread-safe alternative
7. Parse 422 error response body
8. Add `Bearer` prefix to Authorization header
9. Add certificate pinning for production
10. Redact image payloads from debug logging

### Should Fix
11. Downsample bitmaps before quality validation
12. Recycle bitmaps in `CameraManager.capturePhoto`
13. Make `CameraManager` lifecycle-aware
14. Fix silent ML Kit failures
15. Migrate `setTargetResolution` to `ResolutionSelector`
16. Add `@JvmStatic` for Java interop
17. Make `VerificationRequest` Parcelable
18. Bump `compileSdk` to 35+
19. Update dependency versions to match consumer app

### Write Tests For
20. `MrzReader` — all 3 formats + edge cases
21. `ChallengeDetector` — state machine transitions
22. `QualityValidator` — threshold behavior
23. `SessionManager` — HTTP/exception mapping
24. `VerificationViewModel` — state machine transitions
