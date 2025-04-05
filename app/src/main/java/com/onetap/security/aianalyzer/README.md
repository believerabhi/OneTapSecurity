# AI Security Text Analyzer

This package contains components for analyzing extracted text using TensorFlow Lite models to detect potential security threats.

## Overview

The AI analyzer package extends the rule-based text processing capabilities with machine learning to provide more accurate threat detection. It can identify various security threats like phishing, credential stealing, financial scams, and malware distribution.

> **Note:** The package includes a fallback mode that uses rule-based pattern matching when a TensorFlow Lite model is not available. For production use, you should train and include a proper TensorFlow Lite model in the assets directory.

## Key Components

### `TextClassifier`

The core ML component that uses TensorFlow Lite models to classify text into security categories:

- Supports both standard text classification models and BERT models
- Provides confidence scores for classification results
- Detects specific patterns in text that triggered the classification

### `AISecurityAnalyzer`

Main facade for the AI analysis features:

- Analyzes extracted text for security concerns
- Provides detailed security analysis with threat categories and confidence scores
- Can analyze both full text and individual chunks for more targeted analysis

### `AIIntegration`

Integration layer between rule-based text processing and AI analysis:

- Enhances rule-based analysis with AI insights
- Merges detected threats from both approaches
- Provides an overall security verdict

### `ModelTrainer`

Reference implementation for training and updating ML models:

- Loads training data from CSV files
- Evaluates model performance
- Facilitates model deployment and updates
- Can generate synthetic training data for testing

## ML Model Details

The package supports two types of models:

1. **Simple Text Classification Model**: 
   - Faster execution, smaller size
   - Good for basic threat detection

2. **BERT Models**:
   - More accurate but larger and slower
   - Better for nuanced threat detection

## Security Categories

The AI models are trained to detect the following security categories:

- **Phishing**: Attempts to steal credentials by impersonating legitimate services
- **Credential Stealing**: Direct attempts to capture login credentials
- **Financial Scam**: Fraudulent schemes targeting financial information or transfers
- **Malware**: Content attempting to distribute malware
- **Safe**: Non-threatening content
- **Suspicious**: Content that shows some suspicious patterns but isn't clearly malicious

## Usage

Basic usage pattern:

```kotlin
// Initialize the analyzer
val aiAnalyzer = AISecurityAnalyzer(context)
aiAnalyzer.initialize()

// Analyze extracted text
val result = aiAnalyzer.analyzeText(extractedText)

// Check for security risks
if (result.success && result.securityAnalysis != null) {
    val analysis = result.securityAnalysis
    if (analysis.severityLevel == AISeverityLevel.HIGH || 
        analysis.severityLevel == AISeverityLevel.CRITICAL) {
        // Handle high-risk content
    }
}

// Clean up when done
aiAnalyzer.close()
```

## Training and Updating Models

The package includes tools for training and updating models, but for production use, it's recommended to:

1. Train models offline using Python and TensorFlow
2. Convert them to TensorFlow Lite format
3. Deploy them as asset files in the app

A sample dataset is included for testing purposes.

## Integration with Rule-Based Analysis

For best results, combine the AI analysis with the rule-based analysis:

```kotlin
val aiIntegration = AIIntegration(context)
val enhancedResult = aiIntegration.enhanceAnalysis(
    extractionResult,
    ruleBasedResult
)

// Get the final security verdict
val verdict = enhancedResult.securityVerdict

// Take action based on the verdict
when (verdict) {
    SecurityVerdict.DANGEROUS, 
    SecurityVerdict.HIGH_RISK -> showHighRiskWarning()
    SecurityVerdict.SUSPICIOUS -> showCautionWarning()
    SecurityVerdict.CONTAINS_SENSITIVE_INFO -> showSensitiveInfoAlert()
    SecurityVerdict.SAFE -> proceedSafely()
    else -> showDefaultWarning()
}
```