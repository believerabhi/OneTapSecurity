# OneTapSecurity

OneTapSecurity is an Android application that provides real-time screen monitoring and security analysis. It captures screenshots, performs text extraction, and analyzes the content for potential security risks and sensitive information.

## Project Structure

The project has been refactored with a modular architecture:

```
com.onetap.security/
├── screencapture/    # Screen capture functionality
├── textprocessing/   # Text extraction and analysis
├── aianalyzer/       # AI-based security analysis
└── (Main app classes)
```

### Key Components

#### 1. Screen Capture Package

The `screencapture` package handles all screen capture functionality:
- `ScreenCaptureManager`: Main entry point for screen capture operations
- `ImageProcessor`: Processes captured images for optimal quality

See [Screen Capture README](app/src/main/java/com/onetap/security/screencapture/README.md) for details.

#### 2. Text Processing Package

The `textprocessing` package handles text extraction and security analysis:
- `ScreenAnalyzer`: Main facade for text analysis
- `TextExtractor`: Extracts text from images using OCR
- `TextProcessor`: Analyzes text for security risks
- Support classes for results and data models

See [Text Processing README](app/src/main/java/com/onetap/security/textprocessing/README.md) for details.

#### 3. AI Analyzer Package

The `aianalyzer` package provides AI-enhanced security analysis:
- `AISecurityAnalyzer`: Main entry point for AI analysis
- `TextClassifier`: Classifies text using TensorFlow Lite models
- Support classes for results and categorization

## Integration Flow

1. `ScreenCaptureService` is the main service that orchestrates the screen analysis:
   - It uses `ScreenCaptureManager` to capture screenshots
   - Passes the screenshot to `ScreenAnalyzer` for text extraction and analysis
   - Enhances the results with `AIIntegration` when available
   - Shows notifications with the results

2. The user interacts with the service through:
   - `ProjectionPermissionActivity`: To grant screen capture permission
   - `ResultActivity`: To view detailed analysis results

## Building and Running

The project uses Gradle with Kotlin DSL for building:

```
# To build the project
./gradlew assembleDebug

# To install on a connected device
./gradlew installDebug
```

## Dependencies

The project relies on the following key libraries:
- Google ML Kit for text recognition
- TensorFlow Lite for AI-based text classification
- AndroidX and Kotlin Coroutines for the core functionality

## Required Permissions

The app requires the following permissions:
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PROJECTION` for screen capture
- `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` for saving screenshots
- `INTERNET` for model downloads and updates

## Contributing

To contribute to this project:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

Please keep the modular architecture in mind when adding features or making changes.
