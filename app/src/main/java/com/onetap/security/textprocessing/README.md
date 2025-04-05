# Text Processing Package

This package handles the extraction and analysis of text from images/screenshots for the OneTapSecurity app.

## Components

### ScreenAnalyzer

This is the main entry point for the text processing package. It provides a simple API for analyzing screenshots:

- Takes a path to a screenshot file
- Extracts text from the image
- Analyzes the text for security risks and sensitive information
- Returns a structured result

#### Usage Example

```kotlin
val screenAnalyzer = ScreenAnalyzer(context)

// In a coroutine scope
val result = screenAnalyzer.analyzeScreenshot(screenshotPath)

// Check result and handle accordingly
if (result.success) {
    val securityRisks = result.securityAnalysis?.securityRisks ?: emptyList()
    val sensitiveInfo = result.securityAnalysis?.sensitiveInformation ?: emptyList()
    // Handle the detected risks and sensitive information
}

// When done, release resources
screenAnalyzer.close()
```

### TextExtractor

The `TextExtractor` class handles the extraction of text from images:

- Uses Google ML Kit for OCR (Optical Character Recognition)
- Processes the image for optimal text recognition
- Extracts full text and text blocks with their positions
- Uses ImageProcessor for image enhancement

### TextProcessor 

The `TextProcessor` class analyzes the extracted text for security risks:

- Identifies potential phishing attempts
- Detects password prompts and login forms
- Finds suspicious URLs
- Identifies sensitive information like credit card numbers, emails, etc.
- Categorizes security risks by severity

### TextExtractionResult

Data class that contains the result of text extraction:

- The full extracted text
- Individual text lines
- Text blocks with position information
- Success/error information

### ProcessedTextResult

Data class that contains the result of text analysis:

- List of detected security risks
- List of sensitive information found
- Analysis metadata

## Dependencies

This package depends on:

- Google ML Kit Text Recognition
- Android Core libraries
- Kotlin Coroutines for asynchronous operations

## Integration with Service

To use this package in your service:

1. Create a `ScreenAnalyzer` instance
2. Use it to analyze screenshots
3. Handle the results appropriately
4. Release resources when done

## Required Permissions

This package requires the following permissions in AndroidManifest:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
```
