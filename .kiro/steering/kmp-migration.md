# Muzza → KMP Migration Context

## Project Overview
Android-only YouTube Music client being migrated to Kotlin Multiplatform (KMP) with iOS support using Compose Multiplatform for shared UI.

## Current Structure (Android-only)
```
Muzza/
├── app/                    # Android app (308 .kt files)
│   └── src/main/java/com/maloy/muzza/
│       ├── constants/      # App constants, preferences keys
│       ├── db/             # Room database, entities, DAOs
│       ├── di/             # Hilt dependency injection
│       ├── extensions/     # Kotlin extension functions
│       ├── listentogether/ # Discord-like listen-together feature (Protobuf + WebSocket)
│       ├── lyrics/         # Lyrics parsing/display
│       ├── models/         # Domain models
│       ├── playback/       # Media3/ExoPlayer playback service
│       ├── ui/             # ~108 Compose UI files
│       ├── utils/          # Utilities (DataStore, Sync, Cache)
│       └── viewmodels/     # 29 Hilt ViewModels
├── innertube/              # YouTube API client (kotlin("jvm"), Ktor + innertubex)
├── kugou/                  # Chinese lyrics provider (kotlin("jvm"), Ktor)
├── lrclib/                 # LRC lyrics provider (kotlin("jvm"), Ktor)
├── kizzy/                  # Discord RPC integration (kotlin("jvm"), Ktor)
└── simpmusic/              # (empty/minimal module)
```

## Target KMP Structure
```
Muzza/
├── shared/                         # KMP shared module
│   └── src/
│       ├── commonMain/             # Pure Kotlin, all platforms
│       │   └── kotlin/com/maloy/muzza/
│       │       ├── data/
│       │       │   ├── db/         # SQLDelight schemas + generated queries
│       │       │   ├── network/    # Ktor multiplatform HTTP clients
│       │       │   └── repository/ # Repository interfaces
│       │       ├── domain/
│       │       │   ├── model/      # Pure data models (no Room/Android)
│       │       │   └── usecase/    # Business logic use cases
│       │       ├── presentation/
│       │       │   └── viewmodel/  # KMP ViewModels (lifecycle-viewmodel-compose KMP)
│       │       └── util/           # Platform-agnostic utilities
│       ├── androidMain/            # Android-specific actual implementations
│       │   └── kotlin/com/maloy/muzza/
│       │       ├── db/             # Android SQLDelight driver
│       │       ├── playback/       # Media3/ExoPlayer (Android-only)
│       │       └── util/           # Android Context utilities
│       └── iosMain/                # iOS-specific actual implementations
│           └── kotlin/com/maloy/muzza/
│               ├── db/             # iOS SQLDelight driver (NativeSqliteDriver)
│               ├── playback/       # AVFoundation/AVPlayer
│               └── util/           # iOS NSUserDefaults / platform utils
│
├── innertube/                      # → Migrate to KMP multiplatform module
├── kugou/                          # → Migrate to KMP multiplatform module
├── lrclib/                         # → Migrate to KMP multiplatform module
├── kizzy/                          # → Migrate to KMP multiplatform module (Android-only feature, can stay)
│
├── androidApp/                     # Android entry point (thin shell)
│   └── src/main/java/com/maloy/muzza/
│       ├── MuzzaApp.kt             # Application class (Koin init)
│       ├── MainActivity.kt         # Single Activity
│       ├── MusicWidget.kt          # Android App Widget
│       ├── di/                     # Koin Android modules
│       └── playback/               # MusicService, ExoDownloadService, etc.
│
└── iosApp/                         # iOS entry point (Xcode project)
    ├── MuzzaApp.swift              # @main SwiftUI entry (hosts ComposeUIViewController)
    └── MuzzaApp.xcodeproj/
```

## Technology Migration Map

| Android (current) | KMP replacement | Notes |
|---|---|---|
| Hilt | **Koin 4.x** | Full KMP support, no annotation processing |
| Room | **SQLDelight 2.x** | Generates type-safe Kotlin from SQL, iOS via NativeSqliteDriver |
| DataStore Preferences | **multiplatform-settings** (russhwolf) | Simple KV storage, iOS uses NSUserDefaults |
| Jetpack ViewModel | **lifecycle-viewmodel KMP** (2.9+) | Official KMP ViewModel |
| Jetpack Navigation | **Navigation Compose KMP** (2.9+) | Now officially multiplatform |
| Media3/ExoPlayer | **expect/actual** — Media3 on Android, AVPlayer on iOS | Playback is platform-specific |
| Coil | **Coil 3.x** (KMP) | Full KMP image loading |
| Jetpack Compose | **Compose Multiplatform** (JetBrains) | Shared UI for Android + iOS |
| OkHttp (Ktor engine) | **CIO** (commonMain) + **Darwin** (iosMain) | Ktor multiplatform engines |
| brotli (Android) | CIO handles it or platform-specific | Brotli decompression expect/actual |
| Protobuf (listentogether) | **Wire** by Square (KMP) or kotlinx.serialization | Wire has KMP codegen |
| Firebase | Keep in androidApp only (foss flavor stays) | No KMP Firebase SDK |
| MLKit translate | Keep in androidApp full flavor | No iOS equivalent |
| opencc4j | Java-only → Android-only | Chinese conversion stays in androidMain |
| Apache Commons Lang3 | Java-only → extract needed utils | Copy the 2-3 functions used |

## Key Library Versions (Target)
```toml
[versions]
kotlin = "2.1.0"
kmp-compose = "1.8.0"            # Compose Multiplatform
koin = "4.0.0"
sqldelight = "2.0.2"
ktor = "3.1.2"                   # Already in project, already KMP-ready
multiplatform-settings = "1.2.0"
coil = "3.1.0"
lifecycle = "2.9.0"              # Official KMP ViewModel
navigation-compose = "2.9.0"    # Official KMP Navigation
```

## Migration Phases

### Phase 1 — Project Restructuring (CURRENT)
**Status: TODO**
- [ ] Create new Gradle module structure: `shared`, `androidApp`, `iosApp`
- [ ] Update `settings.gradle.kts` to include new modules
- [ ] Update root `build.gradle.kts` to add KMP + Compose Multiplatform plugins
- [ ] Update `libs.versions.toml` with KMP libraries
- [ ] Convert `innertube` from `kotlin("jvm")` to `kotlin("multiplatform")`
- [ ] Convert `kugou` from `kotlin("jvm")` to `kotlin("multiplatform")`
- [ ] Convert `lrclib` from `kotlin("jvm")` to `kotlin("multiplatform")`
- [ ] Move existing `app/` sources into `androidApp/`

### Phase 2 — Shared Domain & Network Layer
**Status: TODO**
- [ ] Migrate `innertube/` to KMP: replace `ktor-client-okhttp` with CIO (commonMain) + Darwin (iosMain)
- [ ] Migrate `kugou/` to KMP
- [ ] Migrate `lrclib/` to KMP
- [ ] Remove `brotli` dependency from innertube (not KMP) → use Ktor's built-in content encoding
- [ ] Create `shared/commonMain` domain models (replace Room annotations with plain data classes)
- [ ] Create SQLDelight schema files from existing Room entities

### Phase 3 — Database Migration (Room → SQLDelight)
**Status: TODO**
- [ ] Create `.sq` files for all Room entities: Song, Album, Artist, Playlist, Lyrics, etc.
- [ ] Implement `DatabaseDriverFactory` as expect/actual
- [ ] Migrate all DAO queries to SQLDelight generated code
- [ ] Keep Room in `androidApp` temporarily for data migration path

### Phase 4 — DI Migration (Hilt → Koin)
**Status: TODO**
- [ ] Remove Hilt from all modules
- [ ] Create Koin modules in `shared/commonMain`
- [ ] Create platform-specific Koin modules in `androidMain` and `iosMain`
- [ ] Update all ViewModels: remove `@HiltViewModel`, `@Inject` → Koin `by viewModel()`

### Phase 5 — ViewModel Migration
**Status: TODO**
- [ ] Move all 29 ViewModels to `shared/commonMain/presentation/viewmodel/`
- [ ] Replace `@ApplicationContext` Context references with expect/actual platform context
- [ ] Replace `Dispatchers.IO` patterns with KMP coroutine dispatchers
- [ ] DataStore → multiplatform-settings

### Phase 6 — UI Migration (Compose Multiplatform)
**Status: TODO**
- [ ] Move all UI files to `shared/commonMain/presentation/ui/`
- [ ] Replace Android-specific Compose imports with CMP equivalents
- [ ] Replace `coil` 2.x with `coil` 3.x KMP
- [ ] Handle platform-specific UI: media permissions, file picker, notifications
- [ ] `MainActivity` → thin shell calling `setContent { MuzzaApp() }`
- [ ] Create `iosApp/` Xcode project with `ComposeUIViewController`

### Phase 7 — Playback Layer
**Status: TODO**
- [ ] Define `expect class MusicPlayer` interface in `commonMain`
- [ ] `androidMain` actual: wraps Media3/ExoPlayer + `MusicService`
- [ ] `iosMain` actual: wraps AVPlayer (basic playback, no background service)
- [ ] `PlayerConnection` → KMP-compatible state holder

### Phase 8 — iOS App Target
**Status: TODO**
- [ ] Generate Xcode project via `./gradlew :iosApp:createXcodeProj` or manual
- [ ] Configure CocoaPods / SPM if needed
- [ ] Wire up SwiftUI entry point to Compose
- [ ] iOS-specific: permissions (microphone N/A, media library), background audio entitlement
- [ ] TestFlight build pipeline

## Android-Only Features (Stay in androidApp)
- Firebase Analytics / Crashlytics / Remote Config / Perf
- Google MLKit language detection + translation
- opencc4j (Chinese OpenCC conversion)
- Android App Widget (`MusicWidget.kt`)
- `ExoDownloadService` (foreground download service)
- `MediaLibrarySessionCallback` (Android Auto / WearOS)
- Discord RPC (`kizzy`) — Discord app is Android-only anyway

## Files That Migrate 1:1 to commonMain (minimal changes)
- All `db/entities/` → remove Room annotations, keep data class fields
- `constants/` → pure Kotlin constants
- `models/` → pure data models
- `extensions/CoroutineExt.kt`, `ListExt.kt`, `StringExt.kt` → pure Kotlin
- `innertube/` all files → Ktor already KMP, minor engine changes
- `kugou/`, `lrclib/` → same as innertube

## Files That Need Significant Rewrite
- `di/AppModule.kt` → Koin modules
- `db/DatabaseDao.kt`, `db/InternalDatabase.kt` → SQLDelight
- `playback/MusicService.kt` → platform-specific
- `playback/PlayerConnection.kt` → KMP state holder + platform actual
- `utils/DataStoreUtils.kt` → multiplatform-settings
- All ViewModels → remove Hilt, replace Context usage
- `ui/Theme.kt` → Compose Multiplatform theming

## Current Phase
**PHASE 6 — UI Migration (IN PROGRESS)**

### Completed ✅
- `libs.versions.toml` — добавлены Koin, SQLDelight, Coil3, multiplatform-settings, Compose MP, kotlinx-datetime
- `settings.gradle.kts` — подключены `shared`, `androidApp`, legacy `app`
- `build.gradle.kts` (root) — KMP/CMP плагины
- `shared/build.gradle.kts` — KMP модуль commonMain/androidMain/iosMain
- `androidApp/build.gradle.kts` — Android оболочка
- Все директории созданы
- `innertube/build.gradle.kts` — конвертирован в KMP multiplatform
- `kugou/build.gradle.kts` — конвертирован в KMP multiplatform
- `lrclib/build.gradle.kts` — конвертирован в KMP multiplatform
- `kizzy/build.gradle.kts` — конвертирован в KMP multiplatform
- `innertube/src/commonMain` — все 91 файл скопированы
- `innertube` — `sha1()` → expect/actual (JVM: MessageDigest, iOS: CommonCrypto)
- `innertube` — `HttpClient` → expect/actual `createInnerTubeHttpClient()` (JVM: OkHttp, iOS: Darwin)
- `innertube` — `InnerTube.kt` очищен от `java.net.Proxy` и `OkHttp`
- `kugou/src/commonMain` — файлы скопированы, `KuGou.kt` очищен от `opencc4j`/`java.lang`
- `kugou` — `toTraditionalChinese()` → expect/actual
- `lrclib/src/commonMain` — файлы скопированы
- `kizzy/src/commonMain` — файлы скопированы
- `app/src/` → `androidApp/src/` — 525 файлов скопированы
- `shared/domain/model/` — 11 чистых KMP domain моделей (без Room)
- SQLDelight `.sq` схемы — 11 таблиц: Song, Album, Artist, Playlist, Event, Lyrics, Format, SongAlbumMap, SongArtistMap, PlaylistSongMap, SearchHistory
- `DatabaseDriverFactory` — expect/actual (Android: AndroidSqliteDriver, iOS: NativeSqliteDriver)
- `MusicRepository` интерфейс + `MusicRepositoryImpl` через SQLDelight
- `Mappers.kt` — SQLDelight ↔ domain модели
- `AppSettings.kt` — KMP настройки через multiplatform-settings
- `PlatformDispatcher.kt` — expect/actual IO/Main
- `RandomStringUtils.kt` — замена Apache Commons
- Koin модули: `SharedModule`, `AndroidModule`, `IosModule`, `KoinIos`
- 15 ViewModels в `shared/commonMain`: Home, Album, Artist, Explore, LibraryAlbums/Artists/Playlists, LocalPlaylist, OnlineSearch/Playlist, LocalSearch, History, Lyrics, Player, Settings
- `MuzzaApp.kt` + `MainActivity.kt` — Android entry point с Koin
- `MuzzaApp.swift` + `ContentView.swift` + `MainViewController.kt` — iOS entry point
- `Theme.kt` — KMP тема с expect/actual (Android: dynamicColor, iOS: material-kolor)
- `MuzzaNavHost.kt` — полная KMP навигация (40+ роутов)
- `NavIcon`/`NavLabel` — expect/actual для иконок нижнего бара
- 117 UI файлов скопированы в `shared/commonMain`
- `PreferenceKeys.kt` — platform-agnostic константы и enums
- `extensions/`, `constants/`, `models/` скопированы в `shared/commonMain`

### Next Actions
- [ ] Убрать Android-специфичные импорты из скопированных UI файлов (`@StringRes`, `@DrawableRes`, `LocalContext`)
- [ ] Мигрировать `extensions/` — убрать `java.*` импорты
- [ ] Настроить `iosApp/iosApp.xcodeproj`
- [ ] Проверить сборку `./gradlew :shared:compileKotlinAndroid`
