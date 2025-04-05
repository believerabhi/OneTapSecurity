package com.onetap.security.aianalyzer

/**
 * Data class to represent the result of text classification by the AI model
 */
data class TextClassificationResult(
    val categories: List<ClassificationCategory>,
    val inputText: String,
    val success: Boolean,
    val errorMessage: String? = null
)

/**
 * Represents a classification category with confidence score
 */
data class ClassificationCategory(
    val label: String,
    val confidence: Float
)

/**
 * Predefined classification categories for security analysis
 */
enum class SecurityCategory(val label: String) {
    PHISHING("phishing"),
    MALWARE("malware"),
    CREDENTIAL_STEALING("credential_stealing"),
    FINANCIAL_SCAM("financial_scam"),
    SAFE("safe"),
    SUSPICIOUS("suspicious"),
    UNKNOWN("unknown")
}

/**
 * Severity level for AI-detected security issues
 */
enum class AISeverityLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Detailed security analysis result from the AI model
 */
data class AISecurityAnalysis(
    val primaryCategory: SecurityCategory,
    val confidenceScore: Float,
    val otherCategories: List<Pair<SecurityCategory, Float>>,
    val severityLevel: AISeverityLevel,
    val detectedPatterns: List<String>,
    val explanation: String
)