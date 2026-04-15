# Bonial Coding Challenge — Rick & Morty Browser 🪐

A high-performance, offline-first Android application built with **Clean Architecture**, **MVI**, and the latest **Jetpack Compose** components. This project showcases modern Android development best practices, including a multi-module setup, robust pagination, and comprehensive test coverage.

---

## 🚀 Key Highlights & "Eye-Catching" Features

- **Multi-Module Clean Architecture**: Fully decoupled modules (`:app`, `:domain`, `:data`, `:network`, `:core`) ensuring separation of concerns and lightning-fast incremental builds.
- **MVI (Model-View-Intent) with StateFlow**: A robust unidirectional data flow implementation using a custom `MviViewModel` base class for predictable state management and easier debugging.
- **Offline-First Experience**: Local persistence using **Room** ensures that the app remains functional even without an internet connection.
- **Dynamic Build Variants**: Specialized `release` and `qa` variants with custom ProGuard/R8 rules for production security and obfuscation testing.
- **Meticulous UX & Scroll State**: Smooth pagination with precise scroll-position retention using `rememberSaveable`, ensuring the user never loses their place when navigating back from details.
- **Modern Tech Stack**: Hilt for DI, Coil 3 for image loading, Retrofit 3, and Kotlin 2.3+.

---

## 🏗 Architecture & Data Flow

### The MVI Pattern
The app follows a strict flow:
`UI (Compose) → Intent → ViewModel → UseCase → Repository → [Network / Room]`

### Module Responsibilities
| Module | Responsibility |
|---|---|
| **`:app`** | Presentation: Compose UI, ViewModels, MVI State, Theme |
| **`:domain`** | Pure Business Logic: UseCases (Enrichment, Sharing), Repository Interfaces |
| **`:data`** | Data Access: Repository Impls, Room DB, Local Data Sources |
| **`:network`** | Infrastructure: Retrofit 3, OkHttp 5, API Services, JSON DTOs |
| **`:core`** | Shared: Base MVI classes, SharedPreferences, UI Extensions |

### Mermaid Data Flow
```mermaid
graph TD
    subgraph ":app (Presentation)"
        UI[Compose UI]
        VM[CharactersViewModel]
    end

    subgraph ":domain (Business Logic)"
        UC[GetEnrichedCharactersUseCase]
        SC[GetCharacterShareTextUseCase]
        RI[CharactersRepository Interface]
    end

    subgraph ":data (Data)"
        RP[CharactersRepositoryImpl]
        DB[(Room Database)]
    end

    subgraph ":network (Remote)"
        API[CharactersApiService]
        RF[Retrofit 3 / OkHttp 5]
    end

    UI -->|Intent| VM
    VM -->|invoke| UC
    VM -->|invoke| SC
    UC -->|fetch| RI
    RI -.->|implements| RP
    RP -->|fetch/cache| API
    RP -->|persist| DB
    API --> RF
```

---

## 🛠 Tech Stack

- **UI**: Jetpack Compose (Material 3)
- **DI**: Hilt 2.59.2
- **Image Loading**: Coil 3.4.0
- **Database**: Room 2.8.4
- **Networking**: Retrofit 3.0.0 + OkHttp 5.3.2
- **Serialization**: Gson + Kotlinx Serialization (for variant-specific config)
- **Async**: Coroutines + Flow
- **Build**: Gradle Version Catalog + KSP 2.3.6

---

## 🧪 Testing & CI/CD Strategy

### ⚙️ Automated Pipeline (GitHub Actions)
I have implemented a comprehensive **CI/CD pipeline** (`.github/workflows/ci.yml`) that ensures high code standards and prevents regressions. Every push and pull request to `main` and `develop` triggers a multi-stage workflow:

| Stage | Description |
|---|---|
| **Code Quality** | Runs `ktlintCheck` (style) and `detekt` (static analysis) to ensure clean code. Reports are uploaded as artifacts. |
| **Unit Tests** | Executes all JVM unit tests (`testDebugUnitTest`) to verify business logic across all modules. |
| **Code Coverage** | Generates **JaCoCo** HTML and XML reports to monitor testing depth. |
| **Screenshot Tests** | Uses **Roborazzi** + **Robolectric** to compare UI against baselines. Fails on any pixel diff and uploads diff PNGs for review. |
| **Build Artifacts** | Automatically builds and uploads the **Debug APK** for immediate manual testing. |

### 📊 Coverage Summary (JaCoCo)
**Lines**: **80.1%** | **Instructions**: **69.7%** | **Methods**: **70.7%**

To generate a report locally:
```bash
./gradlew jacocoFullReport
```

---

## 📦 Build Variants & ProGuard

| Variant | Minified | Purpose |
|---|---|---|
| `release` | **Yes** | Production build with full R8 optimization. |
| `qa` | **Yes** | Debuggable build with obfuscation for testing. |
| `debug` | No | Standard development build. |

---

## 📝 License
See [SOLUTION.md](SOLUTION.md) for more details on the challenge requirements and trade-offs.
