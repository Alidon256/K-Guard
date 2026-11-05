# K-Guard
### Intelligent Online Content Safety and Age-Appropriate Access

**Event:** UCC Testbed Hackathon 2026  
**Track:** AI for Intelligent Online Content Safety and Age-Appropriate Access  
**Platform:** Android  
**Language:** Kotlin  
**UI Framework:** Jetpack Compose

---

## 1. Overview

K-Guard is an Android application prototype designed to support safer online experiences for children through intelligent content moderation and age-appropriate access.

The application connects to an externally provided moderation API to analyse user-submitted text and images. It receives a moderation decision and presents an appropriate response based on the result and the selected age profile.

The initial prototype focuses on text and image moderation. The Android application is responsible for the mobile interface, application state, API integration, and presentation of moderation outcomes.

**Scope clarification:** K-Guard consumes an AI moderation service supplied by the wider team. Android development does not include training AI models or implementing the moderation backend.

## 2. Objectives

- Allow users to submit text and images for safety analysis.
- Support age-aware content suitability decisions.
- Present moderation results and explanations clearly.
- Provide appropriate in-app interventions, including warnings, blurred previews, and content restrictions.
- Maintain a modular, maintainable Android codebase.
- Provide a foundation for future content types and integrations.

## 3. Initial Prototype Scope

### Included

- Android application developed in Kotlin.
- Jetpack Compose user interface.
- Age profile or age-group selection.
- Text submission and moderation results.
- Image selection, preview, upload, and moderation results.
- Integration with the moderation API supplied by the AI/backend team.
- Loading, success, empty, and error states.
- In-app warning, blur, and restriction interfaces.

### Future extensions

- Video moderation.
- Additional platform integrations.
- Further guardian and profile-management features.
- Additional content-safety workflows.

K-Guard does not claim to automatically inspect or control content inside WhatsApp, TikTok, browsers, or every other third-party Android application. Such functionality would require separate technical investigation, permissions, and platform-specific integration.

## 4. Application Architecture

K-Guard uses a layered Android architecture. The app communicates with an external moderation API; it does not host or train the AI models.

```text
+--------------------------------------------------+
|                  K-GUARD ANDROID                 |
+--------------------------------------------------+
|                 Jetpack Compose                 |
|  Home | Age Profile | Text | Image | Results    |
+-------------------------+------------------------+
                          |
                          v
+--------------------------------------------------+
|                  VIEWMODEL LAYER                 |
|          UI state and user interactions          |
+-------------------------+------------------------+
                          |
                          v
+--------------------------------------------------+
|                 REPOSITORY LAYER                 |
|       Coordinates moderation data requests      |
+-------------------------+------------------------+
                          |
                          v
+--------------------------------------------------+
|                   NETWORK LAYER                  |
|             Retrofit + OkHttp + JSON             |
+-------------------------+------------------------+
                          |
                     HTTPS / REST
                          |
                          v
+--------------------------------------------------+
|          EXTERNAL MODERATION API                 |
|       Text analysis | Image analysis             |
|       Age-aware decision | Result response       |
+-------------------------+------------------------+
                          |
                          v
+--------------------------------------------------+
|             K-GUARD RESULT PRESENTATION          |
|          ALLOW | WARN | BLUR | BLOCK | REVIEW    |
+--------------------------------------------------+
```

### Layer responsibilities

| Layer | Responsibility |
|---|---|
| UI | Compose screens and reusable components |
| ViewModel | Screen state and user interaction logic |
| Repository | Coordinates moderation requests and results |
| Network | Sends API requests and parses responses |
| Local preferences | Stores non-sensitive app settings when needed |

## 5. Technology Stack

| Technology | Purpose |
|---|---|
| Kotlin | Main programming language |
| Jetpack Compose | Declarative Android UI |
| Material 3 | UI components and visual system |
| Navigation Compose | Navigation between screens |
| ViewModel | UI state management |
| Kotlin Coroutines | Asynchronous operations |
| Retrofit | REST API communication |
| OkHttp | HTTP client |
| Kotlinx Serialization | JSON encoding and decoding |
| Coil | Image loading and display |
| DataStore Preferences | Local settings and preferences |
| Firebase Authentication | Optional user authentication |
| Firebase Firestore | Optional profile/settings/history storage |

Firebase is optional for the first prototype. Add it only if authentication, cloud-synced profiles, or persisted moderation history are required.

## 6. Android Dependencies

The following are the core Android libraries. Replace the version placeholders with versions compatible with the project's Kotlin, Gradle, Android Gradle Plugin, and Compose configuration. Where possible, manage versions centrally in `gradle/libs.versions.toml`.

### Jetpack Compose

```kotlin
implementation(platform("androidx.compose:compose-bom:<version>"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.compose.material3:material3")
implementation("androidx.activity:activity-compose:<version>")
```

### Navigation and lifecycle

```kotlin
implementation("androidx.navigation:navigation-compose:<version>")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:<version>")
implementation("androidx.lifecycle:lifecycle-runtime-compose:<version>")
```

### Networking and serialization

```kotlin
implementation("com.squareup.retrofit2:retrofit:<version>")
implementation("com.squareup.okhttp3:okhttp:<version>")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<version>")
```

Configure a Retrofit converter compatible with the serialization library selected for the project.

### Image handling

```kotlin
implementation("io.coil-kt.coil3:coil-compose:<version>")
```

### Local preferences

```kotlin
implementation("androidx.datastore:datastore-preferences:<version>")
```

### Optional Firebase

```kotlin
implementation(platform("com.google.firebase:firebase-bom:<version>"))
implementation("com.google.firebase:firebase-auth")
implementation("com.google.firebase:firebase-firestore")
```

## 7. Suggested Project Structure

The package name below is a suggested convention. Adjust it to match the actual Android application ID.

```text
K-Guard/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/kguard/
│   │       │   ├── MainActivity.kt
│   │       │   ├── navigation/
│   │       │   │   └── AppNavigation.kt
│   │       │   ├── ui/
│   │       │   │   ├── screens/
│   │       │   │   │   ├── onboarding/
│   │       │   │   │   ├── home/
│   │       │   │   │   ├── ageprofile/
│   │       │   │   │   ├── textmoderation/
│   │       │   │   │   ├── imagemoderation/
│   │       │   │   │   └── results/
│   │       │   │   ├── components/
│   │       │   │   └── theme/
│   │       │   ├── data/
│   │       │   │   ├── remote/
│   │       │   │   │   ├── ModerationApi.kt
│   │       │   │   │   └── RetrofitClient.kt
│   │       │   │   ├── repository/
│   │       │   │   │   └── ModerationRepository.kt
│   │       │   │   └── model/
│   │       │   │       ├── TextModerationRequest.kt
│   │       │   │       ├── ImageModerationRequest.kt
│   │       │   │       └── ModerationResponse.kt
│   │       │   ├── viewmodel/
│   │       │   │   ├── HomeViewModel.kt
│   │       │   │   ├── TextModerationViewModel.kt
│   │       │   │   └── ImageModerationViewModel.kt
│   │       │   └── util/
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

## 8. Main Application Screens

1. **Onboarding:** Introduces K-Guard and explains its purpose.
2. **Age Profile:** Allows selection of the age group used for suitability decisions.
3. **Home Dashboard:** Provides access to the main safety tools.
4. **Text Checker:** Accepts text for moderation.
5. **Image Checker:** Allows image selection and preview before moderation.
6. **Moderation Results:** Shows the classification, risk score if provided, age-appropriateness result, decision, and explanation.

## 9. API Integration Contract

The Android application depends on the AI/backend team to provide the API contract before integration.

Required information:

- API base URL.
- Text moderation endpoint and HTTP method.
- Image moderation endpoint and HTTP method.
- Required request fields and content types.
- Response schema and possible decision values.
- Image size and format limits.
- Authentication requirements, if applicable.
- Error response format and expected status codes.

### Proposed endpoint mapping

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/health` | Check API availability |
| `POST` | `/moderate/text` | Submit text for moderation |
| `POST` | `/moderate/image` | Submit an image for moderation |

These paths are proposed examples, not confirmed endpoints. The Android implementation must follow the actual API contract supplied by the backend team.

### Illustrative moderation response

```json
{
  "category": "violence",
  "risk_score": 0.94,
  "age_appropriate": false,
  "decision": "BLUR",
  "reason": "Potentially violent content detected"
}
```

The actual response fields and decision values must be agreed upon with the API team. The Android app should handle missing or unexpected fields safely.

### Decision handling

| Decision | Suggested Android behaviour |
|---|---|
| `ALLOW` | Show the content as suitable under the returned policy |
| `WARN` | Display a clear safety warning |
| `BLUR` | Show a blurred preview and an appropriate explanation |
| `BLOCK` | Prevent access to the submitted content within K-Guard |
| `REVIEW` | Indicate that further review is required |

The app should not treat a risk score alone as a definitive decision. It should use the moderation service's agreed decision and policy response.

## 10. Development Workflow

1. Set up the Kotlin and Gradle project.
2. Configure Jetpack Compose and Material 3.
3. Establish the navigation structure.
4. Implement onboarding and the home screen.
5. Implement age-profile selection.
6. Build the text moderation interface.
7. Build image selection and preview.
8. Obtain the API contract and base URL from the backend team.
9. Integrate Retrofit with the moderation API.
10. Implement result mapping and UI states.
11. Add warning, blur, and restriction interfaces.
12. Test successful requests, invalid inputs, server errors, and network failures.
13. Prepare the application for the hackathon demonstration.

## 11. Privacy and Security

- Request only Android permissions required by implemented features.
- Use HTTPS for API communication.
- Avoid storing submitted text and images unless there is a clear requirement and suitable consent.
- Do not commit API secrets, tokens, or private configuration to source control.
- Avoid logging sensitive user-submitted content.
- Handle age-profile information carefully.
- Provide understandable explanations when content is restricted.
- Handle failed or unavailable moderation services safely; do not silently treat an unmoderated item as approved.

## 12. Building Without Android Studio

The project can be built using the Gradle wrapper from a terminal, provided a compatible JDK and Android SDK are installed and configured.

On Linux or macOS:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

To run unit tests:

```bash
./gradlew test
```

To install the debug build on a connected Android device with ADB configured:

```bash
./gradlew installDebug
```

The generated debug APK is normally located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 13. Prototype Deliverables

The Android workstream is expected to provide:

- A working Kotlin Android application.
- Text moderation interface connected to the supplied API.
- Image moderation interface connected to the supplied API.
- Age-profile selection and age-aware result presentation.
- Warning, blur, and restriction UI states.
- Source code and build instructions.
- Demonstration of successful moderation requests and relevant error states.

## 14. Future Development

Potential future improvements include video moderation, additional age-profile controls, guardian features, more moderation workflows, and integrations with other platforms. These should be considered only after the initial text and image prototype is working and evaluated.

---

**K-Guard**  
*Building safer digital experiences through intelligent online content safety.*

**UCC Testbed Hackathon 2026**  
**Track:** AI for Intelligent Online Content Safety and Age-Appropriate Access
