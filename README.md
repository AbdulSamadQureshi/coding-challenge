# Bonial Coding Challenge — Brochure App

An Android application that displays nearby brochures, built with a multi-module Clean Architecture approach and modern Jetpack libraries.

## Project Structure

The project follows **Clean Architecture** principles divided into five modules:

- **`:app`** — Presentation layer: Compose UI, ViewModels, MVI state management
- **`:domain`** — Pure Kotlin business logic: Use Cases, Domain Models, Repository Interfaces
- **`:data`** — Data layer: Repository implementations, Room database, remote API service
- **`:network`** — Networking: Retrofit configuration, OkHttp interceptors (auth, logging)
- **`:core`** — Shared utilities: MVI base classes, SharedPrefsManager, UI extensions

## Architecture

### Pattern: Clean Architecture + MVI

Each screen follows a strict unidirectional data flow:

```
UI (Compose) → Intent → ViewModel → UseCase → Repository → [Network / Room]
                ↑                                                    |
                └─────────── State (StateFlow) ─────────────────────┘
```

### Data Flow

```mermaid
graph TD
    subgraph ":app (Presentation)"
        UI[Compose UI]
        VM[BrochuresViewModel]
    end

    subgraph ":domain (Business Logic)"
        UC[BrochuresUseCase]
        RI[BrochuresRepository Interface]
    end

    subgraph ":data (Data)"
        RP[BrochuresRepositoryImpl]
        API[BrochuresApiService]
        LOCAL[BrochureLocalDataSource]
        DB[(Room Database)]
    end

    subgraph ":network (Remote)"
        RF[Retrofit / OkHttp]
    end

    UI -->|Intent| VM
    VM -->|invoke| UC
    UC -->|brochures()| RI
    RI -.->|implements| RP
    RP -->|network-first| API
    RP -->|cache on success| LOCAL
    RP -->|fallback on failure| LOCAL
    LOCAL --> DB
    API --> RF
    RF --> Backend[Remote API]
    VM -->|StateFlow| UI
```

### Offline-First Strategy

`BrochuresRepositoryImpl` applies a **network-first with cache fallback** approach:

1. Emit `Request.Loading`
2. Fetch from network → on success, cache to Room, emit `Request.Success`
3. On network failure → read from Room:
   - Non-empty cache → emit `Request.Success` (stale data)
   - Empty cache → emit `Request.Error`

## Tech Stack

| Category | Library |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture, MVI |
| DI | Hilt 2.58 + Anvil 2.7.0 |
| Async | Kotlin Coroutines + Flow |
| Networking | Retrofit 3 + OkHttp 5 |
| Serialization | Gson (custom `ContentWrapperDeserializer`) |
| Image Loading | Coil 2.7 |
| Local Storage | Room 2.6 + SharedPreferences |
| Build | Gradle Version Catalog (`libs.versions.toml`) |
| Code Quality | Detekt + ktlint |
| CI | GitHub Actions |

## Testing Strategy

Tests are organised by layer, with each layer testing its own responsibilities:

### Unit Tests

| Test | What it covers |
|---|---|
| `BrochuresViewModelTest` | State transitions (Loading → Success/Error), intent handling |
| `BrochuresUseCaseTest` | Distance filtering (< 5 km), content type filtering, Loading/Error passthrough |
| `BrochuresRepositoryImplTest` | Network success + cache write, cache fallback, empty cache → error |
| `BrochureLocalDataSourceImplTest` | DAO delegation, delete-before-insert ordering |
| `NetworkHelperTest` | `safeApiCall` Flow emissions for success/failure/network errors |
| `ContentWrapperDeserializerTest` | Custom Gson deserializer for polymorphic content types |
| `SharedPrefsManagerTest` | Local preference read/write operations |

### UI / Instrumentation Tests

| Test | What it covers |
|---|---|
| `BrochureScreenTest` | Main screen renders, grid is visible on data load |
| `BrochuresGridTest` | Premium brochures span full width, error placeholder shown on image failure |

Run unit tests:
```bash
./gradlew test
```

Run instrumentation tests:
```bash
./gradlew connectedAndroidTest
```

## Code Quality

```bash
./gradlew ktlintCheck   # Kotlin style (Android Studio code style, 140-char limit)
./gradlew detekt        # Static analysis (complexity, coroutines safety, naming)
```

Configuration: `config/detekt/detekt.yml`, `.editorconfig`

## CI/CD

GitHub Actions runs on every push and pull request to `main`/`develop`:

1. **Code Quality** — ktlint + Detekt
2. **Unit Tests** — `./gradlew test`
3. **Build** — `assembleDebug` with APK upload

See `.github/workflows/ci.yml`.

## Build Variants

| Variant | Environment | Minification | Notes |
|---|---|---|---|
| `debug` | Staging | Off | Debug logging on |
| `qa` | QA | Off | App ID suffix `.qa` |
| `staging` | Staging | Off | App ID suffix `.staging` |
| `release` | Production | **On** (R8 + resource shrinking) | ProGuard rules in `proguard-rules.pro` |

Environment-specific `BASE_URL` and `ENVIRONMENT` values are loaded from `.properties` files (`release.properties`, `qa.properties`, `staging.properties`).

## 🚀 Highlights & Eye-Catching Features

- **Multi-Module Clean Architecture**: A robust decoupled structure (`:app`, `:domain`, `:data`, `:network`, `:core`) designed for scalability and team collaboration.
- **MVI with StateFlow**: Unidirectional data flow that makes state predictable and UI testing a breeze.
- **Pagination & Search**: Seamlessly handles infinite scrolling with search query debouncing for a smooth user experience.
- **Dynamic ProGuard Configuration**: Custom build variants (`release`, `qa`, `staging`) with optimized obfuscation rules to ensure production security without breaking Hilt or Kotlin Serialization.
- **Meticulous UX**: Precise scroll-state retention using `rememberSaveable` to ensure the user never loses their place when navigating back from details.

## 📊 Code Coverage (JaCoCo)

We take quality seriously. The project uses JaCoCo for comprehensive coverage reporting.

**Latest Coverage Summary:**
- **Lines**: **80.1%** (483/603)
- **Instructions**: **69.7%**
- **Methods**: **70.7%**

You can generate the full HTML report by running:
```bash
./gradlew jacocoFullReport
```

## Known Trade-offs

- **Gson over Kotlinx Serialization**: Gson was retained to preserve the existing custom `ContentWrapperDeserializer`. Kotlinx Serialization is the modern Kotlin-first alternative and would be preferred in a greenfield project.
- **SharedPreferences for token storage**: The auth token is stored in SharedPreferences via `SharedPrefsManager`. Jetpack DataStore (Preferences) would be the modern replacement; migration is straightforward given the existing abstraction layer.
- **Single-screen navigation**: The app is single-screen, so Jetpack Navigation Compose was not introduced. It would be the natural next step if a brochure detail screen were added.
