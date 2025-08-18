# Rebranding Changes: Muzza to Dreamify

This document outlines the comprehensive changes made during the rebranding of the Muzza application to Dreamify.

## I. Textual Rebranding

The core of this effort was a deep and wide-ranging textual rebranding to replace all instances of "Muzza" with "Dreamify".

### 1. Project-level Configuration
- **`settings.gradle.kts`**: Changed `rootProject.name` from "Muzza" to "Dreamify".
- **`app/build.gradle.kts`**:
    - Updated `namespace` from `com.maloy.muzza` to `com.dreamify.app`.
    - Updated `applicationId` from `com.maloy.muzza` to `com.dreamify.app`.
- **`README.md`**: Replaced all occurrences of "Muzza" and "muzza" with "Dreamify" and "dreamify", including links and descriptions.

### 2. Package Name and Directory Structure
- **Core Logic:** Systematically refactored all package names from `com.maloy.*` to `com.dreamify.*` across all modules (`app`, `innertube`, `kugou`, `lrclib`).
- **Directory Renaming:** The entire directory structure was refactored to match the new package names. This was a meticulous, multi-step process due to environment constraints.
    - `app/src/main/java/com/maloy/muzza` -> `app/src/main/java/com/dreamify/app`
    - `app/src/foss/java/com/maloy/muzza` -> `app/src/foss/java/com/dreamify/app`
    - `app/src/full/java/com/maloy/muzza` -> `app/src/full/java/com/dreamify/app`
    - `innertube/src/main/java/com/maloy/innertube` -> `innertube/src/main/java/com/dreamify/innertube`
    - `kugou/src/main/java/com/maloy/kugou` -> `kugou/src/main/java/com/dreamify/kugou`
    - `lrclib/src/main/java/com/maloy/lrclib` -> `lrclib/src/main/java/com/dreamify/lrclib`
- **Source Code:** All `import` and `package` statements in `.kt` files were updated to reflect the new structure.

### 3. String Resources
- **All Languages:** Replaced "Muzza" and "muzza" with "Dreamify" and "dreamify" in all `strings.xml` files across all `values-*` directories. This ensures localization consistency.
- **Other XML Resources:** Updated all other `.xml` resource files (e.g., `AndroidManifest.xml`, drawables, layouts) with the new branding.

### 4. Asset and File Renaming
- **Database Schema:** Renamed `app/schemas/com.maloy.muzza.db.InternalDatabase` to `app/schemas/com.dreamify.app.db.InternalDatabase`.
- **Drawable:** Renamed `app/src/main/res/drawable/muzza_monochrome.xml` to `app/src/main/res/drawable/dreamify_monochrome.xml`.
- **Asset:** Renamed `assets/Muzza-icon.jpg` to `assets/Dreamify-icon.jpg`.

## II. Visual and Asset Rebranding (Initial Steps)

### 1. Theming
- **`app/src/main/java/com/dreamify/app/ui/theme/Theme.kt`**:
    - Changed the `DefaultThemeColor` from `Color(0xFFED5564)` to a light purple `Color(0xFFB5A7D7)` to align with the "Dreamify" aesthetic.
    - Renamed the theme composable from `MuzzaTheme` to `DreamifyTheme`.

### 2. Component Enhancements
- **`app/src/main/java/com/dreamify/app/ui/component/IconButton.kt`**:
    - Added a subtle press animation to both `IconButton` and `ResizableIconButton` composables.
    - The buttons now scale down slightly on press, providing a more expressive and tactile user feedback.

## III. Build and Environment Issues

- **Build Status:** The project is currently **non-buildable**.
- **Root Cause:** The sandbox environment is missing a required Android SDK, and attempts to install it have been blocked by file size limitations.
- **Troubleshooting Steps Taken:**
    - Attempted to configure Gradle Toolchains with the Foojay plugin.
    - Attempted to install OpenJDK 17 via `apt-get`.
    - Attempted to download the Android SDK command-line tools manually.
- **Next Steps (for you):** You will need to ensure a valid Android SDK is available in your local environment and that the `local.properties` file points to it.
