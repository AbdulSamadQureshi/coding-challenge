# Coding Challenge — Rick & Morty Browser

A production-quality Android application built with **Clean Architecture**, **MVI**, and **Jetpack Compose**. The app browses Rick & Morty characters with live search, infinite pagination, offline-persisted favourites, and a full CI/CD pipeline.

---

## Features

| Feature | Detail |
|---|---|
| **Character list** | Infinite-scroll grid loaded from the Rick & Morty API |
| **Live search** | 1-second debounce, API-side filtering via `?name=` query param |
| **Pagination** | Append-on-scroll; triggers when 4 items from the end of the list |
| **Favourites** | Persisted in Room DB; synced in real time across list and detail screens |
| **Character detail** | Full info screen with species, status, origin, location |
| **Share** | Android share-sheet with formatted character card text |
| **Offline support** | Favourites survive network loss via Room; list served from in-memory StateFlow cache |
| **Error & retry** | Full-screen error + retry on initial load; sticky in-grid retry banner on pagination failure |
| **Shimmer loading** | Skeleton shimmer on initial load, pagination, and detail screen image load |

---

## Architecture

### Multi-Module Clean Architecture

The project follows a layered architecture to enforce separation of concerns and improve build times.

```
:app  ──▶  :domain  ──▶  :data  ──▶  :network
 │             │
 └──▶  :core ◀─┘
```

| Module | Responsibility |
|---|---|
| **`:app`** | Compose UI, ViewModels, MVI State/Intent/Effect, Navigation (**Navigation 3**), Theme |
| **`:domain`** | **Pure Kotlin Library** (no Android deps): Use cases, repository interfaces, domain models |
| **`:data`** | Repository implementations, Room DB, DAOs, mappers, NetworkHelper |
| **`:network`** | Retrofit 3 + OkHttp 5 client, token caching, logging interceptor |
| **`:core`** | `MviViewModel` base class, SharedPreferences, shared UI extensions (shimmer) |

### MVI Pattern

Every screen follows strict unidirectional data flow:

```
UI (Compose)  ──Intent──▶  ViewModel  ──UseCase──▶  Repository  ──▶  [API / Room]
     ▲                          │
     └────State / Effect────────┘
```

**`MviViewModel<S, I, E>`** (`:core`)
- **State** — `MutableStateFlow<S>` updated atomically via `setState { reduce() }`
- **Intent** — buffered `Channel<I>`; every user action is a named, inspectable object
- **Effect** — conflated `Channel<E>` for one-time events (navigation, share sheet, toast)

### Modern Navigation (Navigation 3)

The app uses **Jetpack Navigation 3**, the latest experimental iteration of the navigation library. It treats navigation as state, where `NavDisplay` observes a list of `NavKey`s. This integrates perfectly with the MVI pattern, as navigation effects can be treated as one-shot emissions from the ViewModel.

---

## Technical Highlights

### 1. Pure Kotlin Domain Layer
The `:domain` module is a pure Kotlin library. It has **zero Android dependencies**, ensuring that business logic is completely decoupled from the framework. This makes use cases extremely fast to unit test without needing Robolectric or an emulator.

### 2. Roborazzi Screenshot Testing
The app uses **Roborazzi** for pixel-perfect UI verification.
- **JVM-Based**: Tests run on the host machine using Robolectric, making them significantly faster than traditional instrumented tests.
- **CI Integration**: The pipeline automatically verifies every PR against committed baselines in `app/src/test/screenshots/`.

### 3. Build System & Build Variants
A robust build system is configured with multiple variants:

| Variant | Minified | Debuggable | Purpose |
|---|---|---|---|
| `debug` | No | Yes | Local development with full debug symbols |
| `qa` | **Yes (R8)** | **No** | QA testing with release-like obfuscation and performance |
| `release` | **Yes (R8)** | **No** | Production build |

The `qa` build type is specifically configured to be non-debuggable and inherits release ProGuard rules to ensure QA testing happens on code that is as close to production as possible.

### 4. Zero-Tolerance Code Quality
The project enforces strict code quality through:
- **Detekt**: Configured with `maxIssues: 0`. The build fails if a single code smell is detected.
- **ktlint**: Enforces consistent Kotlin coding styles across the entire project.

### 5. Advanced Search & Pagination
- **Generation-based Retry**: Uses a generation counter in the `searchParams` flow to trigger a retry without having to duplicate the query string or bypass the debounce window with messy logic.
- **Generation-aware Pagination**: Pagination jobs are automatically cancelled when a new search starts, preventing stale results from polluting the UI.

---

## CI/CD Pipeline

The pipeline is managed via GitHub Actions and performs the following on every PR:

1.  **Code Quality**: Runs `ktlint` and `detekt` (0 issues allowed).
2.  **Unit Tests**: Executes JUnit tests across all modules.
3.  **Code Coverage**: Generates a JaCoCo report (targeting >80% coverage).
4.  **Screenshot Verification**: Compares current UI against baselines using Roborazzi.
5.  **Automated Release**: Merges to `main` automatically build, sign, and publish a GitHub Release with the APK.

---

## Testing Strategy

| Layer | Tool | What's tested |
|---|---|---|
| **ViewModels** | JUnit 4 + Mockito + Turbine | State transitions, debounce timing, pagination guards |
| **Use Cases** | JUnit 4 + Mockito + Truth | Business logic, enrichment, error mapping |
| **Repositories** | JUnit 4 + Mockito | Data mapping, local vs remote logic |
| **Networking** | JUnit 4 + Turbine | `safeApiCall` transitions, retry logic (429 handling) |
| **UI** | **Roborazzi** | Visual regression testing of Composables |

---

## Tech Stack Summary

- **Kotlin** (2.3.20)
- **Jetpack Compose** with Material 3
- **Navigation 3** (androidx.navigation3)
- **Hilt** (Dependency Injection)
- **Retrofit 3 + OkHttp 5**
- **Room** (Offline Persistence)
- **Coil 3** (Image Loading)
- **Coroutines + Flow**
- **Roborazzi + Robolectric** (Screenshot Testing)
- **Detekt + ktlint** (Static Analysis)

---

## Getting Started

### Prerequisites
- **Android Studio** Meerkat or newer
- **JDK 17+**

### Clone & Run
```bash
git clone https://github.com/AbdulSamadQureshi/coding-challenge.git
cd coding-challenge
./gradlew assembleDebug
```

### Run All Verification
```bash
./gradlew check            # Runs ktlint, detekt, and all unit tests
./gradlew verifyRoborazzi  # Verifies UI against screenshots
```
