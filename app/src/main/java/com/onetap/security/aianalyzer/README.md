# AI Analyzer Package

This package provides AI-powered security analysis functionality for the OneTapSecurity app.

## Components

### AISecurityAnalyzer

The `AISecurityAnalyzer` class is the main entry point for AI-based security analysis. It provides methods to:

- Initialize AI models
- Analyze extracted text for security concerns
- Process text chunks for detailed analysis
- Clean up resources

#### Usage Example

```kotlin
// Create an instance with context
val aiAnalyzer = AISecurityAnalyzer(context)

// Initialize the analyzer
coroutineScope.launch {
    val initialized = aiAnalyzer.initialize()
    if (initialized) {
        // Ready to use
    }
}

// Analyze text (in a coroutine)
val result = aiAnalyzer.analyzeText(extractionResult)

// Check results
if (result.success && result.securityAnalysis != null) {
    val primaryCategory = result.securityAnalysis.primaryCategory
    val confidenceScore = result.securityAnalysis.confidenceScore
    val explanation = result.securityAnalysis.explanation
    // Handle the results
}

// When done, release resources
aiAnalyzer.close()
```

### TextClassifier

The `TextClassifier` class handles the actual classification of text using TensorFlow Lite:

- Loads and manages TensorFlow models
- Classifies text into security categories
- Provides fallback classification when models aren't available
- Offers detailed security analysis with explanations

### Data Models

- `TextClassificationResult`: Contains classification results with categories and confidence scores
- `AISecurityAnalysis`: Detailed analysis with primary category, confidence, and explanation
- `SecurityCategory`: Enum of possible security categories (PHISHING, MALWARE, etc.)
- `AISeverityLevel`: Severity level enum (LOW, MEDIUM, HIGH, CRITICAL)

## Integration with Other Packages

The AI Analyzer package is designed to work with the Text Processing package:

1. The Text Processing package extracts text from images
2. The AI Analyzer package provides enhanced security analysis of that text

See [Text Processing README](../textprocessing/README.md) for details on text extraction.
See [Screen Capture README](../screencapture/README.md) for details on capturing screenshots.

## Model Management

This package can work with TensorFlow Lite models for text classification:

- Simple model: Loaded from assets
- Fallback mode: Rule-based classification when models aren't available

## Required Permissions

This package requires the following permissions in AndroidManifest:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
```

## Dependencies

- TensorFlow Lite Task API
- TensorFlow Lite Support
- Kotlin Coroutines
