# Solution Notes — Coding Challenge

This document is a reviewer guide explaining the architectural decisions, per-screen implementation choices, trade-offs considered, and strategies for extending and scaling the solution. It is intended to be read alongside the code.

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

### Note on Data Source

This project uses the public **Rick & Morty API** as its data source rather than a marketplace API. The domain fields differ — characters have species, status, origin, and location instead of price, description, and seller info — but the architecture is identical to how a marketplace item list would be built. The repository layer, use cases, ViewModels, and UI state model are all data-agnostic; swapping the API and domain model would leave every architectural pattern intact. All three extra features from the brief (Search, Favourites, Share) are fully implemented.

---

## Architecture Decisions

### Why Layer Modules — and How They Relate to Feature Modules

The project uses **layer modules** (`:app`, `:domain`, `:data`, `:network`, `:core`). Layer modules and feature modules are not competing choices — they are different phases of the same scaleable architecture. Layer modules are the **foundation** that feature modules sit on top of.

**Why not a single module?**
A single module removes compile-time boundaries. Nothing stops a ViewModel from importing a Room DAO directly. `:domain` can accidentally grow Android dependencies. Every Kotlin file recompiles on every change regardless of what was touched.

**What layer modules deliver:**
- **Faster incremental builds** — Gradle only recompiles layers whose inputs changed. Touching a ViewModel recompiles only `:app`. Touching a use case recompiles `:domain` and `:app`, but not `:data` or `:network`.
- **Hard compile-time boundaries** — `:domain` literally cannot import Android classes. `:data` cannot reach into `:app`. These are enforced by the module graph, not by convention or code review.
- **Trivially testable domain logic** — `:domain` has zero Android dependencies, so all use cases and repository interfaces run in plain JUnit without Robolectric or an emulator.
- **The correct foundation for feature modules** — when the app grows to 3+ features with dedicated teams, feature modules slot directly on top:

```
:feature:characters ──▶  :domain  ──▶  :data  ──▶  :network
:feature:favourites ──▶  :domain               └──▶ :core
:feature:episodes   ──▶  :domain
:app  (thin orchestrator: navigation + DI graph assembly)
```

The layer modules are not replaced — they become the shared infrastructure every feature module depends on. Starting with layer modules is the correct first step toward this structure.

**Why not jump straight to feature modules now?**
Feature modules require each vertical slice to carry its own DI module, domain interfaces, and data sources. Cross-cutting use cases like `GetEnrichedCharactersUseCase` — which combines the character list with the favourites state — have no natural home in a feature module without creating a `:feature:common` that immediately becomes a second `:domain`. The layer structure handles cross-cutting concerns cleanly by design.

---

### Why MVI over MVVM?

Plain MVVM works well for simple screens with one or two independently-updating data streams. The character list screen is not simple — **search, pagination, retry, and favourite toggles all run concurrently and all touch the same state**.

With MVVM and multiple `LiveData`/`StateFlow` fields, the UI must reconcile several independently-updating streams. It becomes easy to render inconsistent combinations: `isLoading=true` with `error != null`, or `characters` showing while `isInitialLoading=true`. Every new state field multiplies the number of combinations the UI must handle defensively.

MVI enforces:
- **One immutable state object** — every possible screen combination is an explicit named state. The UI is a pure function of that state.
- **One update path** — `setState { copy(...) }` is the only way to mutate state. No race between two `_myLiveData.value = ...` assignments.
- **Explicit intent channel** — every user action is a named, inspectable object in the intent stream. Debugging is reading a log of intents, not hunting across multiple setter calls.
- **Side effects via a separate channel** — one-time events (share sheet, toast) go through `Channel<E>` and are not part of state, so they don't re-fire on recomposition.

The trade-off is boilerplate: every screen needs a State data class, an Intent sealed class, and an Effect sealed class. For a simple static screen this overhead is unwarranted. For a screen with concurrent async operations, the structure pays for itself immediately.

**Alternative considered:** Redux-style reducer with a single global store. Rejected because a global store introduces unnecessary coupling between unrelated screens and complicates ViewModel scoping with Hilt.

---

### Why a generation counter for retry/search?

`searchParams: MutableStateFlow<Pair<String, Int>>` carries a query string and an integer generation. The problem it solves: `StateFlow` deduplicates emissions — if the user taps "Retry" without changing the search query, emitting the same string would be a no-op. Incrementing the generation counter forces a new emission, re-triggering `flatMapLatest` and firing a fresh API call without touching the debounce logic.

The same counter controls debounce bypass: generation > 0 means this is a retry, not a new user keystroke, so debounce is set to 0ms.

**Alternative considered:** A separate `retryTrigger: SharedFlow<Unit>` that merges with the search flow. Rejected because merging two flows while maintaining correct debounce behaviour for one but not the other requires significantly more operator plumbing. The generation counter keeps it in one place.

---

### Why `flatMapLatest` for search?

`flatMapLatest` cancels the currently-running inner flow whenever a new emission arrives. This makes concurrent search requests structurally impossible — no `ConcurrentModificationException`, no race condition, no need for explicit job tracking at the search layer. The previous page 1 request is automatically cancelled before the new one starts.

**Alternative considered:** `switchMap` on a `LiveData` stream. Rejected because the rest of the codebase is Coroutines/Flow; mixing `LiveData` would require bridge operators and complicate testing.

---

### Why cancel `paginationJob` on search?

Pagination runs outside `flatMapLatest` (it loads subsequent pages, not page 1). Without explicit cancellation, a page 3 request from the previous query could complete after the new query's page 1 response arrives, appending stale results to the fresh list. Tracking `paginationJob: Job?` and cancelling it before emitting a new search value closes this race window completely.

---

## Character List Screen — Detailed Decisions

### State design: two error fields

```kotlin
data class CharactersState(
    val error: String? = null,           // replaces the entire screen
    val paginationError: String? = null, // appears in grid footer; list stays visible
    ...
)
```

A single `error` field cannot express two fundamentally different failure modes:
- **Initial/search failure**: The user has no content yet. Replace the grid with a full-screen error and retry button.
- **Pagination failure**: The user is reading page 1 results. Show a sticky banner in the footer; do not destroy what they are reading.

Using one field for both would require the UI to check `currentPage > 1` to decide how to render the error — that is business logic leaking into the composable.

### `isInitialLoading` vs `isLoading`

`isInitialLoading` drives the full-screen skeleton shimmer shown before any content has ever loaded. `isLoading` drives the per-request spinner state. They are separate because:
- On app start: `isInitialLoading=true`, `isLoading=true` → full-screen shimmer
- On search change: `isInitialLoading=false`, `isLoading=true` → shimmer in content area, not full-screen replacement
- On pagination: `isLoadingNextPage=true` → shimmer only in grid footer

### Search debounce strategy

| Condition | Delay |
|---|---|
| Retry (generation > 0) | 0 ms — immediate |
| Query cleared (empty string) | 0 ms — immediate |
| New keystroke | 1 000 ms — debounced |

The delay logic lives inside the `debounce { (query, gen) -> ... }` lambda, making it testable by injecting a `TestCoroutineDispatcher` and verifiable by asserting that the API is NOT called before 1000ms and IS called after.

### Favourite enrichment without extra API calls

`GetEnrichedCharactersUseCase` uses Kotlin's `combine` operator to merge two flows:
1. The API response flow (characters from the network/cache)
2. `FavouritesRepository.getFavouriteCoverUrls(): Flow<Set<String>>`

When the user toggles a favourite, Room emits a new set, `combine` re-fires, and every character card in the list immediately reflects the updated state — no network round-trip, no manual list mutation. The enrichment is purely in-memory and runs on the domain layer.

### Network Failure & Empty State Handling

| Scenario | UI behaviour |
|---|---|
| Initial load fails | Full-screen `ErrorMessage` with the HTTP-specific message and a **Retry** button |
| Pagination (page 2+) fails | Loaded list stays visible; a sticky **"Failed to load more. Tap to retry."** banner appears in the grid footer. Tapping re-sends `LoadNextPage`. |
| Search returns 404 | API 404 is treated as "no results", not an error. `EmptySearchState` shown with the query and a suggestion to try a different term. |
| No characters at all | `EmptyState` shown with retry prompt. |

The 404-as-no-results treatment is deliberate: the Rick & Morty API returns HTTP 404 (not an empty array) when a name query matches nothing. Surfacing this as an error would be confusing to the user.

---

## Character Detail Screen — Detailed Decisions

### Assisted injection for the character ID

`CharacterDetailViewModel` uses `@AssistedInject` + `@HiltViewModel(assistedFactory = ...)`. The character ID is carried in the `CharacterDetailKey` navigation route object and passed to the factory at screen creation time.

**Why not `SavedStateHandle`?**
`SavedStateHandle` requires an agreed string key constant (`"character_id"`) shared between the navigation call site and the ViewModel. That is an implicit string contract that can silently break if either side is refactored. `@AssistedInject` makes the contract explicit and type-safe: the factory takes a `CharacterDetailKey`, not a raw string.

**Why not pass the full character object?**
Passing the full `CharacterUi` through the navigation key would couple navigation to the UI model and make deep-linking impossible. Passing only the ID means the detail screen can be reached from any entry point (list tap, deep link, notification) with the same code path.

### Share via Effect channel

The share flow:
1. User taps share icon → `CharacterDetailIntent.ShareCharacter` enters the intent channel
2. ViewModel calls `GetCharacterShareTextUseCase(characterDetail)` — pure Kotlin, no Android dependency
3. Result emitted as `CharacterDetailEffect.Share(text)` on the Effect channel
4. Composable collects via `flowWithLifecycle(Lifecycle.State.STARTED)` and calls `startActivity`

The composable does exactly one Android-specific thing: call `startActivity`. All share-text formatting logic is in the use case and is fully unit-tested without a Robolectric context.

**Why `flowWithLifecycle(STARTED)` instead of `LaunchedEffect` alone?**
Without lifecycle awareness, effects collected in a `LaunchedEffect` would be processed even when the screen is in the background (e.g. during a system dialog). `flowWithLifecycle(STARTED)` suspends collection when the lifecycle drops below STARTED and resumes it when it returns — preventing the share sheet from appearing unexpectedly.

### Real-time favourite sync

After a successful character load, the ViewModel launches `observeFavourite(imageUrl)` in a separate coroutine:

```kotlin
private fun observeFavourite(imageUrl: String) {
    viewModelScope.launch {
        isFavouriteFlowUseCase(imageUrl).collectLatest { isFav ->
            setState { copy(isFavourite = isFav) }
        }
    }
}
```

`isFavouriteFlowUseCase` returns `Flow<Boolean>` backed by a Room `@Query` with `RETURNING` semantics. Every write to the `favourite_brochures` table emits a new boolean. The detail screen and list screen both read from the same underlying Room table, so they are always consistent regardless of which screen initiated the toggle.

### Image loading in the detail screen

Two shimmer layers:
1. **`CharacterDetailShimmer`** — full-layout skeleton shown while the API call is in flight (`isLoading=true`)
2. **In-content image shimmer** — shown inside `CharacterDetailContent` while Coil fetches the hero image

Coil's `AsyncImagePainter.State` callbacks update `imageLoaded` and `imageError` local state. When Coil cannot load the image (network error, 404, decode failure), `ImageErrorPlaceholder` is overlaid on the image slot — no broken icon, no empty space.

The content card (name, status, species, etc.) slides in with a `slideInVertically + fadeIn` animation triggered by `contentVisible = true` inside a `LaunchedEffect(Unit)`. This gives the screen a natural reveal even when the hero image is cached and loads instantly.

### Retry on the detail screen

`CharacterDetailIntent.Retry` triggers `loadCharacter(navKey.id)`:

```kotlin
private fun loadCharacter(id: Int) {
    loadJob?.cancel()   // cancel any in-flight request first
    loadJob = viewModelScope.launch {
        setState { copy(isLoading = true, error = null) }
        characterDetailUseCase(id).collectLatest { ... }
    }
}
```

`loadJob` is tracked so that if the screen is somehow recreated (deep-link scenario) a stale response from the original load cannot overwrite the fresh one. In the normal case `loadJob` is null when Retry fires, and the cancel is a no-op.

---

## Key Implementation Details

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

A `MIGRATION_1_2` is included to demonstrate production-grade migration discipline — even on a coding challenge, dropping and recreating the DB on schema change is not acceptable in production.

### Navigation 3

Jetpack Navigation 3 uses `rememberNavBackStack()` with `NavDisplay` and lambda-based `entryProvider`, integrating cleanly with Compose's recomposition model.

`rememberSaveableStateHolderNavEntryDecorator()` preserves the scroll position of the character grid when navigating back from the detail screen — a subtle UX detail that makes the app feel native.

---

## Testing Approach

### What is tested and why

**ViewModels** (`CharactersViewModelTest`, `CharacterDetailViewModelTest`): 11 + 5 tests covering state transitions, debounce timing (verified by asserting the search does NOT fire before 1000ms and DOES fire after), pagination guard conditions, and effect emissions. Turbine is used to collect Flow emissions without manual `runBlocking` / `launch` boilerplate.

**Use cases** (`GetEnrichedCharactersUseCaseTest`, `MapSuccessTest`): 9 + 3 tests. The enrichment test verifies that a character is marked `isFavourite=true` only when its image URL appears in the favourites set, and that the marking survives blank-name sanitisation.

**Repository — unit** (`CharactersRepositoryImplTest`, `FavouritesRepositoryImplTest`): Verifies that DTO-to-domain mapping is correct and that DAO operations are called with the right arguments. The API service is mocked, so these tests focus purely on mapping and flow logic.

**API integration** (`CharactersApiServiceIntegrationTest`, `CharactersRepositoryIntegrationTest`, `NetworkHelperIntegrationTest`): A real Retrofit + Gson stack is wired against `MockWebServer` — no mocks, no emulator, pure JVM. These tests catch what the unit tests structurally cannot:
- Wrong `@SerializedName` keys or missing `@Query`/`@Path` annotations
- Gson crashing on null optional fields (`origin`, `location`, `image`) absent from the JSON
- HTTP error codes (404, 500) flowing out as the correct `Request.Error` code and user message end-to-end
- `withRetry` actually sending the right number of HTTP requests on 429 vs. non-retryable errors

**Network — unit** (`NetworkHelperTest`): 11 tests covering the retry state machine with injected delay — verifies exact call counts and the rule that only 429 triggers a retry, without real I/O.

**Screenshot** (`CharactersScreenShotTest`, `CharacterDetailScreenShotTest`, `ThemeColorsScreenshotTest`): Roborazzi baselines committed to Git. CI runs `verifyRoborazziDebug` and fails on any pixel diff, protecting against accidental layout or theme regressions across all distinct UI states.

### What is NOT tested and why

- **Compose UI** (beyond screenshots): Testing individual Compose components with `ComposeTestRule` would require either an emulator or Robolectric with a full Compose renderer. Screenshot tests cover visual correctness; business logic is fully covered at the ViewModel and use-case layers.
- **Room DAOs**: DAO testing requires an in-memory Room database which needs a real Android context. Given the repository layer is mocked in ViewModel tests and the DAO operations are simple CRUD, this was deprioritised in favour of broader coverage at the use-case layer.

### Coverage

**Lines: 80.1% | Instructions: 69.7% | Methods: 70.7%**

Excludes generated/framework code: Hilt DI modules, Room-generated DAOs, Compose-generated code, `MainActivity`, navigation graph, and theme files — all code where bugs surface as Hilt build failures or visual regressions, not logic errors.

---

## Trade-offs & Known Limitations

| Area | Decision | Trade-off |
|---|---|---|
| **Pagination library** | Manual scroll-threshold pagination | Avoided Paging 3's complexity for a list that loads a single API with simple append semantics. Paging 3 would add value for filtered/sorted sources or very large datasets. |
| **Offline character list** | In-memory StateFlow cache only | Characters are not persisted across process death. Favourites are always persisted. Adding full list persistence would require cache invalidation logic and a TTL strategy. |
| **Token auth** | Bearer token interceptor wired, not activated | The current app doesn't require auth. The token infrastructure is in place (`UserPreferencesDataStore`, `RetrofitClient` interceptor) for when it does. |
| **Error recovery** | Single retry button | There is no exponential backoff for user-initiated retries. `withRetry` handles automated 429 retries; user-facing retry fires a single fresh request. |
| **Screenshot tests** | Single theme colour baseline | Roborazzi is wired up and the CI gate is in place. Additional baselines for full screens would be added as the UI stabilises. |
| **Favourite PK** | Image URL | Works across any character source but would need migration if the API ever changes image URL format. A composite key (source + id) would be more robust at the cost of schema complexity. |
| **Detail screen episode count** | Not shown | The API returns episode URLs, not an episode object. Resolving them would require N additional requests or a batched episodes endpoint that the public API does not provide. |

---

## How to Extend for New Requirements

### Adding a new screen

1. **`:domain`** — add a domain model, repository interface, and use cases
2. **`:data`** — add repository implementation, DTO, mapper, and optional Room table
3. **`:app`** — add State/Intent/Effect/ViewModel/Screen files
4. **`:app` navigation** — add a route key to `CharacterRoutes.kt` and an `entryProvider` block in `CharacterNavGraph.kt`

No changes to `:network`, `:core`, or existing screens are required.

### Adding filters or sorting

Add filter parameters to `CharactersParams` in `:domain`. The ViewModel emits a new `searchParams` value containing the filter. The existing `flatMapLatest` + debounce pipeline picks it up automatically.

### Adding deep links

Add `deepLinks` to the `NavDisplay` entry in `CharacterNavGraph.kt`. Navigation 3 resolves deep-link URIs to the same route key types the list screen uses, so the detail ViewModel receives an identical `CharacterDetailKey` regardless of entry point.

### Adding authentication

`RetrofitClient.kt` already has a token interceptor reading from `UserPreferencesDataStore`. Populate the token on login and the bearer header is attached to all requests automatically.

---

## How the Approach Scales in a Larger Team

### Migrating to feature modules

```
:feature:characters  ──▶  :domain  ──▶  :data  ──▶  :network
:feature:favourites  ──▶  :domain
:feature:episodes    ──▶  :domain
             ↑
          :app  (thin orchestrator: navigation + DI graph assembly)
```

The current layer modules become the shared infrastructure. Each squad owns one feature module. The rule is the same as today: feature modules depend on `:domain` and `:core` but not on each other.

### Parallel team ownership

- **`:domain`** — shared ownership; any team can add a use case or model; zero Android code means changes here never block other teams' builds
- **`:data`** — database or networking changes are isolated and do not force `:app` recompilation
- **`:app` feature modules** — each squad owns one module; no shared mutable state between modules; merge conflicts are structurally minimised

### CI/CD at scale

Current jobs run sequentially. As team size grows:
- Split `unit-tests` into per-module jobs and run them in parallel (`test-domain`, `test-data`, `test-app`)
- Cache Gradle remote build cache across runners (configuration cache is already enabled)
- Add an emulator farm job for instrumented Room DAO tests once data layer ownership grows
- Promote screenshot baselines to a separate approved-design-only branch

### Code quality enforcement

- `detekt` with `maxIssues: 0` prevents technical debt accumulation regardless of team size or deadline pressure
- `ktlint` standardises formatting so code review focuses on logic
- JaCoCo coverage gate can be tightened per module independently as teams mature

---

## CI/CD Design

### Branch model

```
feature/*  ──PR──▶  develop  ──PR──▶  main
```

- All work happens on feature branches and merges to `develop` via PR
- `main` is the release branch — merging any PR into `main` triggers the Build & Release job
- Both `develop` and `main` are protected: no direct pushes, no force pushes, cannot be deleted
- Feature branches are automatically deleted after their PR merges (via `delete-merged-branch.yml`)

### What runs when

| Event | Code Quality | Unit Tests | Coverage | Screenshot Tests | Build & Release |
|---|---|---|---|---|---|
| Feature PR opened/updated → `develop` | ✅ | ✅ | ✅ | ✅ | ❌ |
| Any PR opened/updated → `main` | ❌ | ❌ | ❌ | ❌ | ❌ |
| Any PR **merged** → `main` | ❌ | ❌ | ❌ | ❌ | ✅ |

All four check jobs are scoped to PRs targeting `develop`. Releases always build from `main` regardless of the source branch — this means a `hotfix/* → main` PR also produces a release, not just the standard `develop → main` flow.

### Why build only on `develop → main` merge?

Building on every push to `develop` would produce an APK for every work-in-progress commit. By gating the build behind a deliberate `develop → main` PR, every release is intentional and stakeholders only see completed, reviewed work.

### Signed APK in CI

The debug keystore is stored as a base64 GitHub secret (`DEBUG_KEYSTORE_BASE64`). The CI decodes it to a temporary file before `assembleDebug`. This keeps the keystore out of the repository while still producing a consistently signed APK that devices will accept as an upgrade.

### GitHub Releases for stakeholder distribution

The Build & Release job creates a GitHub Release tagged `build-{run_number}` with the APK attached as an asset. Stakeholders navigate to the **Releases** page and download directly — no GitHub Actions access required.

---

## Project Structure

```
CodingChallenge/
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
│   │   ├── CharactersScreen.kt           # Grid, search bar, shimmer, pagination trigger, empty/error states
│   │   ├── CharactersViewModel.kt        # MVI: search, pagination, favourites, retry
│   │   └── CharacterDetailViewModel.kt   # MVI: detail load, share effect, favourite sync
│   ├── presentation/detail/
│   │   └── CharacterDetailScreen.kt      # Detail layout, shimmer, share, favourite toggle, image states
│   ├── presentation/navigation/
│   │   ├── CharacterNavGraph.kt          # Navigation 3 NavDisplay setup
│   │   └── CharacterRoutes.kt            # Route key data classes
│   └── presentation/theme/               # Material 3 colour tokens, typography, status chip colours
│
├── :domain                               # Business logic (no Android deps)
│   ├── useCase/characters/
│   │   ├── GetEnrichedCharactersUseCase  # Combines API + favourites via Flow.combine
│   │   ├── CharacterDetailUseCase        # Single character fetch
│   │   └── GetCharacterShareTextUseCase  # Formats share text (pure Kotlin)
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
│   └── RetrofitClient.kt                 # OkHttp client, logging, bearer token interceptor
│
└── :core                                 # Shared utilities
    ├── base/MviViewModel.kt              # Generic MVI base class
    ├── preferences/SharedPrefsManager    # SharedPreferences facade
    └── ui/extensions/ModifierExt.kt      # shimmerEffect() Modifier extension
```
