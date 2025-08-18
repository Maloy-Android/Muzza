# AGENT.md for Dreamify Material 3 Expressive Android App

## Project Overview

Dreamify is a Material 3 YouTube Music client for Android undergoing transformation into a fully expressive, production-ready music streaming application. The project leverages Google's latest Material 3 Expressive design system to create an emotionally engaging, performant, and scalable music experience that rivals industry leaders like Spotify and YouTube Music.

**Tech Stack:**
- **Language:** Kotlin 2.1.0
- **UI Framework:** Jetpack Compose 1.8.3
- **Architecture:** MVVM with Hilt dependency injection
- **Material Design:** Material 3 Expressive (target: 1.4.0-alpha14)
- **Audio:** ExoPlayer (Media3 1.7.1)
- **Database:** Room 2.7.2
- **Networking:** Ktor 3.1.2
- **Image Loading:** Coil 2.7.0
- **Build System:** Gradle 8.11.1 with KSP

**Key Features:**
- Material 3 Expressive design system with motion physics
- Dynamic color extraction from album artwork
- Physics-based animations and micro-interactions
- AI-powered theming intelligence
- Immersive music visualization studio
- Advanced audio features with visual feedback
- Cross-device compatibility and responsive design

## Project Structure

```
dreamify/
├── app/                          # Main application module
│   ├── src/main/
│   │   ├── java/com/dreamify/app/
│   │   │   ├── ui/               # Compose UI components
│   │   │   ├── viewmodel/        # MVVM ViewModels
│   │   │   ├── data/             # Data layer (repositories, models)
│   │   │   ├── domain/           # Business logic and use cases
│   │   │   ├── utils/            # Utility classes
│   │   │   └── MainActivity.kt   # Main entry point
│   │   ├── res/                  # Android resources
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts          # App module build configuration
│   └── schemas/                  # Room database schemas
├── innertube/                    # YouTube Music API integration
├── kugou/                        # Kugou lyrics provider
├── lrclib/                       # LrcLib lyrics provider  
├── kizzy/                        # Discord Rich Presence
├── gradle/                       # Gradle wrapper and version catalog
├── build.gradle.kts              # Root build configuration
└── settings.gradle.kts           # Project settings
```

**Key Directories:**
- `app/src/main/java/com/dreamify/app/ui/` - All Compose UI components and screens
- `app/src/main/java/com/dreamify/app/viewmodel/` - ViewModels following MVVM pattern
- `app/src/main/java/com/dreamify/app/data/` - Repositories and data sources
- `app/src/main/res/` - Android resources (layouts, strings, themes)

## Build & Commands

### Essential Build Commands

```bash
# Clean and build debug version
./gradlew clean assembleDebug

# Build release version
./gradlew assembleRelease

# Install debug build on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Generate test coverage report
./gradlew jacocoTestReport

# Lint check
./gradlew lint

# Build full flavor (with Firebase)
./gradlew assembleFullDebug

# Build FOSS flavor (without Firebase)
./gradlew assembleFossDebug
```

### Development Commands

```bash
# Start Android emulator (requires AVD setup)
emulator -avd Pixel_7_API_34

# Check dependencies for updates
./gradlew dependencyUpdates

# Generate Compose compiler reports
./gradlew assembleDebug -PenableComposeCompilerReports=true

# Profile build performance
./gradlew assembleDebug --profile

# Clean build cache
./gradlew cleanBuildCache
```

### Material 3 Expressive Setup Commands

```bash
# Update to Material 3 Expressive dependencies
# Edit app/build.gradle.kts and gradle/libs.versions.toml
# Update material3 version to 1.4.0-alpha14

# Sync project after dependency changes
./gradlew --refresh-dependencies

# Verify expressive features compilation
./gradlew compileDebugKotlin
```

## Code Style and Conventions

### Kotlin Style Guidelines

```kotlin
// File naming: PascalCase for classes, camelCase for functions
class ExpressiveNowPlayingScreen
fun createDynamicColorPalette()

// Package structure
com.dreamify.app.ui.components.expressive
com.dreamify.app.data.repository.music
com.dreamify.app.domain.usecase.theme

// Compose component naming
@Composable
fun ExpressiveAlbumArtwork(
    imageUrl: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
)

// ViewModel naming convention
class NowPlayingViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val themeRepository: ThemeRepository
) : ViewModel()

// Data class naming
data class ExpressiveMediaMetadata(
    val id: String,
    val title: String,
    val dominantColors: List<Color> = emptyList()
)
```

### Formatting Rules

- **Indentation:** 4 spaces (no tabs)
- **Line length:** 120 characters maximum
- **Import organization:** Android imports first, then third-party, then project imports
- **Trailing commas:** Required in multi-line parameter lists
- **String templates:** Use `${}` for complex expressions, `$` for simple variables

### Linting Configuration

```bash
# Run ktlint check
./gradlew ktlintCheck

# Auto-format with ktlint
./gradlew ktlintFormat

# Custom lint rules in lint.xml
# Disabled: MissingTranslation, MissingQuantity, ImpliedQuantity
```

## Architecture and Design Patterns

### MVVM Architecture

```kotlin
// ViewModel pattern
class ExpressivePlayerViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val motionScheme: MotionScheme
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    fun playPause() {
        viewModelScope.launch {
            // Business logic here
        }
    }
}

// Repository pattern
interface MusicRepository {
    suspend fun getCurrentTrack(): Flow<MediaMetadata>
    suspend fun extractColorPalette(imageUrl: String): DynamicColorPalette
}

// Dependency injection with Hilt
@Module
@InstallIn(SingletonComponent::class)
object MusicModule {
    @Provides
    @Singleton
    fun provideMusicRepository(): MusicRepository = MusicRepositoryImpl()
}
```

### Material 3 Expressive Integration

```kotlin
// Motion scheme implementation
@Composable
fun DreamifyExpressiveTheme(
    motionScheme: MotionScheme = MotionScheme.expressive(),
    content: @Composable () -> Unit
) {
    MaterialExpressiveTheme(
        motionScheme = motionScheme,
        content = content
    )
}

// Physics-based animations
val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
val playButtonScale by animateFloatAsState(
    targetValue = if (isPlaying) 1.1f else 1.0f,
    animationSpec = spatialSpec
)
```

### State Management

```kotlin
// Compose state management
@Composable
fun ExpressiveNowPlayingScreen(
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(uiState.currentTrack) {
        // React to state changes
    }
}

// State classes
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentTrack: MediaMetadata? = null,
    val colorPalette: DynamicColorPalette? = null,
    val isLoading: Boolean = false
)
```

## Testing Guidelines

### Unit Testing

```kotlin
// ViewModel testing
@Test
fun `playPause should toggle playback state`() = runTest {
    val viewModel = ExpressivePlayerViewModel(mockRepository, mockMotionScheme)
    
    viewModel.playPause()
    
    assertEquals(true, viewModel.uiState.value.isPlaying)
}

// Repository testing with mocks
@Test
fun `extractColorPalette should return valid palette`() = runTest {
    val mockBitmap = createMockAlbumArtwork()
    val palette = colorExtractionEngine.extractPalette(mockBitmap)
    
    assertNotNull(palette.dominantColor)
    assertTrue(palette.contrastRatio >= 4.5f) // WCAG AA compliance
}
```

### Integration Testing

```kotlin
@HiltAndroidTest
class ExpressivePlayerIntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @Test
    fun `now playing screen should animate smoothly between tracks`() {
        composeTestRule.setContent {
            DreamifyExpressiveTheme {
                ExpressiveNowPlayingScreen()
            }
        }
        
        composeTestRule.onNodeWithTag("play_button")
            .assertExists()
            .performClick()
            
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("album_artwork")
            .assertIsDisplayed()
    }
}
```

### Performance Testing

```kotlin
// Animation performance testing
@Test
fun `expressive animations should maintain 60fps`() {
    val frameMetrics = measureFrameMetrics {
        runExpressiveAnimations()
    }
    
    val frameDropPercentage = (frameMetrics.droppedFrameCount.toFloat() / 
                              frameMetrics.totalFrameCount) * 100
    assertThat(frameDropPercentage).isLessThan(5.0f)
}
```

### Test Configuration

```kotlin
// Test dependencies in app/build.gradle.kts
testImplementation("junit:junit:4.13.2")
testImplementation("org.mockito:mockito-core:4.6.1")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
androidTestImplementation("androidx.compose.ui:ui-test-junit4:$compose_version")
androidTestImplementation("com.google.dagger:hilt-android-testing:2.44")
```

## Security Considerations

### Data Protection

```kotlin
// Encrypted SharedPreferences
val encryptedPrefs = EncryptedSharedPreferences.create(
    "secure_prefs",
    masterKeyAlias,
    context,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

// Secure network configuration
// network_security_config.xml
<network-security-config>
    <domain-config>
        <domain includeSubdomains="true">music.youtube.com</domain>
        <pin-set>
            <pin digest="SHA-256">certificate_hash_here</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

### Permission Management

```kotlin
// Minimal permission requests
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

// Runtime permission handling
@Composable
fun RequestAudioPermission(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onPermissionGranted() else onPermissionDenied()
    }
}
```

## Git Workflow

### Branch Strategy

```bash
# Main branches
main                    # Production-ready code
develop                 # Integration branch for features

# Feature branches
feature/expressive-now-playing
feature/dynamic-theming
feature/particle-system
feature/ai-theming

# Release branches
release/v0.8.0

# Hotfix branches
hotfix/critical-crash-fix
```

### Commit Guidelines

```bash
# Commit message format
<type>(<scope>): <description>

# Examples
feat(ui): implement expressive now playing interface
fix(theme): resolve color extraction crash on API 24
docs(readme): update build instructions
test(player): add unit tests for playback controls
perf(animation): optimize particle system rendering
```

### Pre-commit Hooks

```bash
# Install pre-commit hooks
./gradlew installGitHooks

# Hooks run automatically:
# - ktlint formatting check
# - Unit test execution
# - Lint analysis
# - Build verification
```

## AI Integration

### Web Search Integration and Research Requirements

**MANDATORY: AI agents MUST use web search tools for up-to-date information before implementing any features. This is critical to reduce hallucinations and ensure accuracy.**

**Required Web Searches Before Implementation:**

1. **Material 3 Expressive Updates:**
   ```
   Search: "Material 3 Expressive Android latest updates 2024 2025"
   Search: "androidx.compose.material3 1.4.0 alpha beta latest version"
   Search: "Material Design 3 Expressive motion physics implementation"
   Search: "Jetpack Compose Material 3 Expressive examples GitHub"
   ```

2. **Android Development Best Practices:**
   ```
   Search: "Android Jetpack Compose best practices 2024 2025"
   Search: "Kotlin coroutines latest patterns Android development"
   Search: "Android performance optimization techniques 2024"
   Search: "Hilt dependency injection latest patterns Android"
   ```

3. **Audio and Media Integration:**
   ```
   Search: "ExoPlayer Media3 latest features Android music app"
   Search: "Android audio visualization real-time implementation"
   Search: "Music app UI design inspiration 2024 Material Design"
   Search: "Android audio effects equalizer implementation"
   ```

4. **Accessibility and Performance:**
   ```
   Search: "Android accessibility guidelines 2024 WCAG compliance"
   Search: "Jetpack Compose performance optimization latest"
   Search: "Android animation performance best practices"
   Search: "Material Design accessibility color contrast"
   ```

5. **Inspiration and Reference Research:**
   ```
   Search: "Spotify Android app UI design analysis 2024"
   Search: "YouTube Music app interface design patterns"
   Search: "Music streaming app UX trends 2024 2025"
   Search: "Material Design 3 music app examples GitHub"
   Search: "Android music player open source projects 2024"
   ```

### Autonomous Development Protocol

**AI agents MUST follow this protocol for ALL development tasks:**

1. **Research Phase (MANDATORY):**
   - Perform comprehensive web searches using the queries above
   - Research latest documentation and examples
   - Find inspiration from successful implementations
   - Verify current best practices and patterns
   - Check for any breaking changes or deprecations

2. **Implementation Phase:**
   - Apply researched knowledge to implementation
   - Use latest APIs and patterns discovered
   - Incorporate best practices from research
   - Implement with inspiration from successful examples

3. **Validation Phase:**
   - Test implementation thoroughly
   - Verify against latest standards
   - Ensure accessibility compliance
   - Validate performance requirements

**CRITICAL: AI agents should NOT ask for approval or improvements unless:**
- A critical error prevents compilation
- Security vulnerabilities are detected
- Breaking changes require architectural decisions
- Task completion is blocked by external dependencies

### Autonomous Coding Guidelines

**Permissions for AI Agents:**
- ✅ Modify UI components and screens
- ✅ Update ViewModels and business logic
- ✅ Add new Compose components
- ✅ Update dependencies and build configuration
- ✅ Create and modify test files
- ✅ Update documentation and comments
- ✅ Perform web searches for latest information
- ✅ Research and implement best practices
- ✅ Find and apply design inspiration
- ✅ Iterate and improve implementations autonomously

**Restrictions:**
- ❌ Modify security-related configurations without review
- ❌ Change API keys or sensitive credentials
- ❌ Alter ProGuard rules without testing
- ❌ Modify database schemas without migration scripts
- ❌ Ask for approval on standard implementation tasks
- ❌ Prompt for improvements unless critically blocked

### Research-Driven Development Workflow

**Step 1: Mandatory Research (Before Any Code)**
```bash
# AI must perform these searches for EVERY implementation task
1. Search for latest Material 3 Expressive documentation
2. Find current best practices and examples
3. Research inspiration from successful apps
4. Verify latest API changes and deprecations
5. Check performance and accessibility guidelines
```

**Step 2: Implementation with Research Integration**
```kotlin
// AI should follow these patterns for Material 3 Expressive
// Based on latest research and best practices
@Composable
fun AIGeneratedComponent(
    // Always include Modifier parameter (latest Compose guidelines)
    modifier: Modifier = Modifier,
    // Use Material 3 Expressive motion scheme (researched implementation)
    motionScheme: MotionScheme = MaterialTheme.motionScheme,
    // Include accessibility parameters (WCAG 2.1 AA compliance)
    contentDescription: String? = null
) {
    // Implement physics-based animations (latest Material 3 Expressive patterns)
    val animatedValue by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = motionScheme.defaultSpatialSpec()
    )
    
    // Always include semantic properties for accessibility (researched best practices)
    Box(
        modifier = modifier.semantics {
            if (contentDescription != null) {
                this.contentDescription = contentDescription
            }
        }
    )
}
```

**Step 3: Continuous Improvement Loop**
```kotlin
// AI should continuously research and apply improvements
// WITHOUT asking for permission unless critically blocked

// Example: If research shows better animation patterns
val improvedAnimation by animateFloatAsState(
    targetValue = targetValue,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy, // Latest research findings
        stiffness = Spring.StiffnessMedium
    )
)
```

### Research-Informed Code Generation Standards

**MANDATORY: Perform web search before implementing each standard**

1. **Always use Material 3 Expressive components** (Search: "Material 3 Expressive components latest Android")
2. **Include proper error handling** (Search: "Android error handling best practices 2024")
3. **Add comprehensive accessibility support** (Search: "Android accessibility implementation 2024 WCAG")
4. **Implement proper state management** (Search: "Jetpack Compose state management patterns 2024")
5. **Follow existing naming conventions** (Search: "Kotlin Android naming conventions latest")
6. **Include unit tests** (Search: "Android unit testing best practices Compose 2024")
7. **Add proper documentation** (Search: "Android code documentation standards KDoc")
8. **Research design inspiration** (Search: "Music app UI design trends 2024 Material Design")
9. **Verify performance patterns** (Search: "Android Compose performance optimization 2024")
10. **Check latest dependency versions** (Search: "Android Jetpack Compose latest versions 2024")

### Autonomous Development Commands

```bash
# AI should use these commands during autonomous development
# Research phase
websearch "Material 3 Expressive Android latest documentation"
websearch "Jetpack Compose best practices 2024"
websearch "Android music app design inspiration"

# Implementation phase
./gradlew compileDebugKotlin  # Verify AI-generated code compiles
./gradlew testDebugUnitTest   # Run tests after AI modifications
./gradlew lint                # Check code quality
./gradlew ktlintFormat        # Auto-format generated code

# Validation phase
./gradlew assembleDebug       # Build and verify functionality
./gradlew connectedAndroidTest # Run integration tests
```

### Inspiration Research Protocol

**AI must research these sources for design inspiration:**

1. **Industry Leaders:**
   ```
   Search: "Spotify Android app UI analysis 2024"
   Search: "YouTube Music interface design patterns"
   Search: "Apple Music Android app design elements"
   Search: "Tidal music app Material Design implementation"
   ```

2. **Design Communities:**
   ```
   Search: "Dribbble music app UI design 2024"
   Search: "Behance Material Design music player concepts"
   Search: "Material Design showcase music applications"
   ```

3. **Open Source References:**
   ```
   Search: "GitHub Android music player Material 3 examples"
   Search: "Open source music app Jetpack Compose implementations"
   Search: "Material Design 3 music app repositories"
   ```

4. **Technical Implementation:**
   ```
   Search: "Android music visualization implementation examples"
   Search: "Jetpack Compose animation examples music apps"
   Search: "Material 3 Expressive motion physics examples"
   ```

## Environment Setup

### Prerequisites

```bash
# Required software
- Android Studio Hedgehog | 2023.1.1 or later
- JDK 17
- Android SDK API 34+
- Git

# Recommended tools
- Android Emulator with API 34
- Physical device for testing (Android 7.0+)
- Firebase CLI (for full builds)
```

### Initial Setup

```bash
# Clone repository
git clone https://github.com/Maloy-Android/Dreamify.git
cd Dreamify

# Setup local properties (create local.properties)
sdk.dir=/path/to/Android/Sdk

# Sync dependencies
./gradlew --refresh-dependencies

# Build debug version
./gradlew assembleDebug

# Run on device/emulator
./gradlew installDebug
```

### Development Environment

```bash
# Enable developer options on device
# Settings > About phone > Tap build number 7 times

# Enable USB debugging
# Settings > Developer options > USB debugging

# Verify device connection
adb devices

# View logs during development
adb logcat | grep Dreamify
```

## Additional Considerations

### Performance Optimization

- **Animation Performance:** Use `Animatable` for complex animations, `animateFloatAsState` for simple ones
- **Memory Management:** Implement proper image caching and disposal with Coil
- **Battery Optimization:** Reduce animation complexity when battery is low
- **Frame Rate:** Target 60fps with graceful degradation to 30fps if needed

### Accessibility Compliance

- **WCAG 2.1 AA:** Ensure minimum contrast ratios of 4.5:1
- **Touch Targets:** Minimum 48dp touch targets for all interactive elements
- **Screen Readers:** Comprehensive content descriptions and semantic properties
- **Motion Sensitivity:** Respect user preferences for reduced motion

### Material 3 Expressive Features

- **Motion Physics:** Implement spring-based animations for natural interactions
- **Dynamic Colors:** Extract colors from album artwork while maintaining accessibility
- **Shape System:** Use the expanded 35-shape library for visual interest
- **Typography:** Apply expressive typography scale with proper hierarchy
- **Adaptive Components:** Ensure components adapt to different screen sizes and orientations

## Autonomous Development Expectations

### Work Until Completion Protocol

**AI agents are expected to work autonomously until task completion. DO NOT prompt for improvements or approval unless:**

1. **Critical Blockers Only:**
   - Compilation errors that cannot be resolved
   - Security vulnerabilities requiring architectural decisions
   - Missing external dependencies or API keys
   - Breaking changes requiring major refactoring decisions

2. **Self-Improvement Mandate:**
   - Continuously research and apply latest best practices
   - Iterate on implementations based on research findings
   - Optimize performance and accessibility automatically
   - Refactor code to match latest patterns without asking

3. **Quality Assurance Autonomy:**
   - Test implementations thoroughly before considering complete
   - Fix bugs and issues discovered during testing
   - Optimize performance based on profiling results
   - Ensure accessibility compliance through automated checks

### Research-First Development Cycle

```
1. RESEARCH → Web search for latest information and inspiration
2. PLAN → Analyze requirements against research findings  
3. IMPLEMENT → Code using researched best practices
4. TEST → Validate functionality and performance
5. OPTIMIZE → Improve based on test results
6. ITERATE → Repeat cycle until requirements fully met
```

### Success Criteria for Task Completion

**A task is only complete when ALL of these are satisfied:**
- ✅ All functional requirements implemented and tested
- ✅ Latest best practices researched and applied
- ✅ Performance meets or exceeds specifications
- ✅ Accessibility compliance verified (WCAG 2.1 AA)
- ✅ Code follows researched patterns and conventions
- ✅ Unit and integration tests pass
- ✅ Design inspiration incorporated appropriately
- ✅ Documentation updated with implementation details

### Continuous Research Integration

**AI agents must maintain awareness of:**
- Latest Material 3 Expressive updates and features
- Current Android development best practices
- Performance optimization techniques
- Accessibility guidelines and requirements
- Design trends and user experience patterns
- Security considerations and implementations

**Research should be performed:**
- Before starting any new feature implementation
- When encountering implementation challenges
- When optimizing existing code
- When adding new dependencies or libraries
- Periodically during long development sessions

---

**Note:** This AGENT.md is based on the Material 3 Expressive transformation specification (@requirements.md, @design.md, @tasks.md). All autonomous development should align with the outlined requirements and maintain the high standards of performance, accessibility, and user experience defined in the project specifications.

**CRITICAL:** AI agents must use web search tools extensively to stay current with latest developments, reduce hallucinations, and ensure implementations reflect current best practices and design trends. Work autonomously until task completion without seeking approval for standard development activities.