# Solution Notes — Bonial Coding Challenge

This document explains the architectural decisions, trade-offs, and implementation choices made while building the Rick & Morty character browser. It is intended as a reviewer guide alongside the code.

---

## What Was Built

A production-quality Android app covering all challenge requirements plus several beyond-scope additions:

**Core requirements:**
- Browse Rick & Morty characters from the public API
- Live search with debounce
- Infinite pagination
- Character detail screen

**Beyond scope:**
- Favourites with Room persistence and real-time cross-screen sync
- Share character card via Android share sheet
- Full CI/CD pipeline (GitHub Actions)
- Screenshot regression tests (Roborazzi)
- JaCoCo code coverage (80.1% lines)
- Static analysis (Detekt, zero-tolerance)
- Code style enforcement (ktlint)
- Gradle configuration cache
- Signed APK published to GitHub Releases on every `develop → main` merge

---

## Architecture Decisions

### Why Multi-Module?

Five modules (`:app`, `:domain`, `:data`, `:network`, `:core`) rather than a single module because:

- **Build speed** — Gradle only recompiles modules with changed inputs. Touching a ViewModel in `:app` does not recompile `:domain` or `:data`.
- **Enforced boundaries** — `:domain` has zero Android dependencies. Use cases and repository interfaces are pure Kotlin, making them trivially testable.
- **Scalability** — Adding a feature (e.g., episodes screen) means creating a new module that depends on `:domain` without touching existing modules.

### Why MVI over MVVM?

MVI was chosen over plain MVVM because the character list screen has non-trivial concurrent concerns: search, pagination, retry, and favourite toggles all interact with the same state. MVI's single immutable state object and explicit intent channel make these interactions predictable and debuggable.

The custom `MviViewModel<S, I, E>` base class in `:core` is intentionally thin — it provides the state/intent/effect wiring but imposes no constraint on how `handleIntent()` is implemented, leaving room for coroutine-based async work inside each ViewModel.

### Why a generation counter for retry/search?

`searchParams: MutableStateFlow<Pair<String, Int>>` carries a query string and an integer generation. The problem it solves: `StateFlow` deduplicates emissions — if the user taps "Retry" without changing the search query, emitting the same string would be a no-op. Incrementing the generation counter forces a new emission, re-triggering `flatMapLatest` and firing a fresh API call without touching the debounce logic.

The same counter controls debounce bypass: generation > 0 means this is a retry, not a new user keystroke, so debounce is set to 0ms.

### Why `flatMapLatest` for search?

`flatMapLatest` cancels the currently-running inner flow whenever a new emission arrives. This makes concurrent search requests structurally impossible — no `ConcurrentModificationException`, no race condition, no need for explicit job tracking at the search layer. The previous page 1 request is automatically cancelled before the new one starts.

### Why cancel `paginationJob` on search?

Without explicit cancellation, a pagination request for page 3 of the previous query could land on top of page 1 results from the new query, producing an inconsistent list. Tracking `paginationJob: Job?` and cancelling it before emitting a new search value closes this race window.

---

## Key Implementation Details

### Search debounce strategy

| Condition | Delay |
|---|---|
| Retry (generation > 0) | 0 ms — immediate |
| Query cleared (empty string) | 0 ms — immediate |
| New keystroke | 1000 ms — debounced |

The delay logic lives inside the `debounce { (query, gen) -> ... }` lambda, keeping it in one place and making it testable by injecting a fake `TestCoroutineDispatcher`.

### Favourite enrichment without extra API calls

`GetEnrichedCharactersUseCase` uses Kotlin's `combine` operator to merge two flows:
1. The API response flow (characters)
2. `FavouritesRepository.getFavouriteCoverUrls(): Flow<Set<String>>`

When the user toggles a favourite, Room emits a new set, `combine` re-fires, and every character card in the list immediately reflects the updated state — no network round-trip, no manual list mutation.

### Rate-limit retry

`withRetry()` in `NetworkHelper.kt`:
- Retries only on HTTP 429 (Too Many Requests)
- All other errors (4xx, 5xx, IOException) fail immediately — retrying a 404 or 500 would be pointless
- The delay provider is injected (`delayProvider: suspend (Long) -> Unit`) so tests can pass `{}` and run synchronously without `advanceTimeBy`
- Maximum 20 attempts with 500ms fixed delay between retries

### Room database design

Two tables, deliberately separated:
- `brochures` — character list cache (replaced on conflict)
- `favourite_brochures` — single-column (`coverUrl` as PK) to keep the schema minimal

Favourites use the image URL as the primary key rather than the character ID because the favourite state needs to survive API schema changes and works across any character source without coupling to the Rick & Morty API's ID scheme.

### Navigation 3

Jetpack Navigation 3 was chosen because it was the library already used in the project scaffolding. It uses `rememberNavBackStack()` with `NavDisplay` and lambda-based `entryProvider`, which integrates cleanly with Compose's recomposition model.

`rememberSaveableStateHolderNavEntryDecorator()` preserves the scroll position of the character grid when navigating back from the detail screen — a subtle UX detail that makes the app feel native.

---

## Testing Approach

### What is tested and why

**ViewModels** (`CharactersViewModelTest`, `CharacterDetailViewModelTest`): 11 + 5 tests covering state transitions, debounce timing (verified by asserting the search does NOT fire before 1000ms and DOES fire after), pagination guard conditions, and effect emissions. Turbine is used to collect Flow emissions without manual `runBlocking` / `launch` boilerplate.

**Use cases** (`GetEnrichedCharactersUseCaseTest`, `MapSuccessTest`): 9 + 3 tests. The enrichment test is particularly important — it verifies that a character is marked `isFavourite=true` only when its image URL appears in the favourites set, and that the marking survives blank-name sanitisation.

**Repository** (`CharactersRepositoryImplTest`, `FavouritesRepositoryImplTest`): Verifies that DTO-to-domain mapping is correct and that DAO operations are called with the right arguments.

**Network** (`NetworkHelperTest`): 11 tests covering the retry state machine — verifies exact call counts, delay values, and the rule that only 429 triggers a retry.

**Screenshot** (`ThemeColorsScreenshotTest`): Roborazzi baseline committed to Git. CI runs `verifyRoborazziDebug` and fails on any pixel diff, protecting against accidental theme/colour regressions.

### What is NOT tested and why

- **Compose UI** (beyond screenshots): Testing individual Compose components with `ComposeTestRule` would require either an emulator or Robolectric with a Compose renderer. The screenshot tests cover visual correctness; business logic is fully covered at the ViewModel and use-case layers.
- **Room DAOs**: DAO testing requires an in-memory Room database which needs a real Android context. Given the repository layer is mocked in ViewModel tests and the DAO operations are simple CRUD, this was deprioritised in favour of broader coverage at the use-case layer.

### Coverage

**Lines: 80.1% | Instructions: 69.7% | Methods: 70.7%**

Excludes generated/framework code: Hilt DI modules, Room-generated DAOs, Compose-generated code, `MainActivity`, navigation graph, and theme files — all code where bugs would surface as Hilt build failures or visual regressions, not logic errors.

---

## CI/CD Design

### Branch model

```
feature/*  ──PR──▶  develop  ──PR──▶  main
```

- All work happens on feature branches and merges to `develop` via PR
- `develop → main` is the release gate; merging it triggers the build job
- Both `develop` and `main` are protected: no direct pushes, no force pushes, cannot be deleted
- Feature branches are automatically deleted after their PR merges (via `delete-merged-branch.yml`)

### Why build only on `develop → main` merge?

Building on every push to `develop` would produce an APK for every work-in-progress commit. By gating the build behind a deliberate `develop → main` PR, every release is intentional and stakeholders only see completed, reviewed work.

### Signed APK in CI

The debug keystore is stored as a base64 GitHub secret (`DEBUG_KEYSTORE_BASE64`). The CI decodes it to a temporary file before `assembleDebug`. This keeps the keystore out of the repository while still producing a consistently signed APK that devices will accept as an upgrade.

### GitHub Releases for stakeholder distribution

The Build & Release job creates a GitHub Release tagged `build-{run_number}` with the APK attached as an asset. Stakeholders navigate to the **Releases** page and download directly — no GitHub Actions access required.

---

## Trade-offs & Known Limitations

| Area | Decision | Trade-off |
|---|---|---|
| **Pagination library** | Manual scroll-threshold pagination | Avoided Paging 3's complexity for a list that loads a single API with simple append semantics. Paging 3 would add value for filtered/sorted sources or very large datasets. |
| **Offline character list** | In-memory StateFlow cache only | Characters are not persisted across process death. Favourites are always persisted. Adding full list persistence would require cache invalidation logic and a TTL strategy. |
| **Token auth** | Bearer token in interceptor | The current app doesn't require auth. The token infrastructure is in place (`UserPreferencesDataStore`, `RetrofitClient` interceptor) for when it does. |
| **Error recovery** | Single retry button | There is no exponential backoff for user-initiated retries. The `withRetry` mechanism handles automated 429 retries; user-facing retry fires a single fresh request. |
| **Screenshot tests** | Single theme colour baseline | Roborazzi is wired up and the CI gate is in place. Additional baselines for full screens would be added as the UI stabilises. |

---

## Project Structure

```
BonialCodingChallenge/
├── .github/
│   └── workflows/
│       ├── ci.yml                        # Code quality, tests, coverage, screenshots, release
│       └── delete-merged-branch.yml      # Auto-delete feature branches on merge
├── config/detekt/detekt.yml              # Detekt rules (zero-tolerance)
├── gradle/libs.versions.toml             # Version catalog
├── gradle.properties                     # Configuration cache enabled
├── build.gradle.kts                      # Root: JaCoCo, Detekt, ktlint
│
├── :app                                  # Presentation layer
│   ├── presentation/home/
│   │   ├── CharactersScreen.kt           # Grid, search bar, shimmer, pagination trigger
│   │   └── CharactersViewModel.kt        # MVI: search, pagination, favourites, retry
│   ├── presentation/detail/
│   │   ├── CharacterDetailScreen.kt      # Detail, share, favourite toggle
│   │   └── CharacterDetailViewModel.kt   # MVI: detail load, share effect
│   ├── presentation/navigation/
│   │   ├── CharacterNavGraph.kt          # Navigation 3 NavDisplay setup
│   │   └── CharacterRoutes.kt            # Route key data classes
│   └── presentation/theme/               # Material 3 colour tokens, typography
│
├── :domain                               # Business logic (no Android deps)
│   ├── useCase/characters/
│   │   ├── GetEnrichedCharactersUseCase  # Combines API + favourites via Flow.combine
│   │   ├── CharacterDetailUseCase        # Single character fetch
│   │   └── GetCharacterShareTextUseCase  # Formats share text
│   ├── useCase/favourites/
│   │   ├── ToggleFavouriteUseCase        # Add or remove based on current state
│   │   └── IsFavouriteFlowUseCase        # Real-time favourite state for detail screen
│   └── model/
│       ├── network/response/Request.kt   # Sealed: Loading | Success<T> | Error
│       └── network/response/ApiError.kt  # code + message
│
├── :data                                 # Data access
│   ├── repository/
│   │   ├── CharactersRepositoryImpl      # API fetch → domain model
│   │   └── FavouritesRepositoryImpl      # Room CRUD + Flow<Set<String>>
│   ├── local/
│   │   ├── BrochuresDatabase.kt          # Room v2, MIGRATION_1_2
│   │   ├── BrochuresDao / FavouritesDao
│   │   └── BrochureEntity / FavouriteBrochureEntity
│   ├── mapper/CharacterMappers.kt        # DTO → domain, null-safe
│   └── utils/NetworkHelper.kt            # safeApiCall, withRetry, manageThrowable
│
├── :network                              # HTTP layer
│   └── RetrofitClient.kt                 # OkHttp client, logging, token interceptor
│
└── :core                                 # Shared utilities
    ├── base/MviViewModel.kt              # Generic MVI base class
    ├── preferences/SharedPrefsManager    # SharedPreferences facade
    └── ui/extensions/ModifierExt.kt      # shimmerEffect() Modifier extension
```
