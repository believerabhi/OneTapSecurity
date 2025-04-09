package com.onetap.security.textprocessing

import android.content.Context
import android.util.Log
import com.onetap.security.aianalyzer.CustomTFLiteClassifier
import java.util.regex.Pattern

/**
 * Class responsible for processing and analyzing extracted text
 */
class TextProcessor(private val context: Context) {
    private val TAG = "TextProcessor"

    /**
     * Analyze the extracted text to identify potential security risks
     * @param extractionResult The result from text extraction
     * @return ProcessedTextResult containing analysis results
     */
    fun analyzeText(extractionResult: TextExtractionResult): ProcessedTextResult {
        if (!extractionResult.success || extractionResult.fullText.isEmpty()) {
            return ProcessedTextResult(
                originalText = extractionResult.fullText,
                securityRisks = emptyList(),
                sensitiveInformation = emptyList(),
                analysisSuccessful = false
            )
        }

        Log.d(TAG, "Analyzing extracted text - length: ${extractionResult.fullText.length} characters")
        
        // Identify potential security risks
        val securityRisks = identifySecurityRisks(extractionResult.fullText)
        
        // Find sensitive information
        val sensitiveInfo = findSensitiveInformation(extractionResult.fullText, extractionResult.lines)
        // Try to classify with TFLite model, but handle errors gracefully
        try {
            val classifier = CustomTFLiteClassifier(context)
            if (classifier.isInitialized()) {
                val prediction = classifier.classifyText(extractionResult.fullText)
                Log.d(TAG, "Custom TFLite classification result: $prediction")
                
                if (prediction != "safe") {
                    securityRisks.add(
                        SecurityRisk(
                            type = SecurityRiskType.KNOWN_PHISHING,
                            description = "TFLite model detected: $prediction",
                            severity = RiskSeverity.HIGH
                        )
                    )
                }
            } else {
                Log.w(TAG, "TFLite classifier not initialized, skipping classification")
            }
            classifier.close()
        } catch (e: Exception) {
            // Don't let TFLite errors break the entire analysis
            Log.e(TAG, "Error in TFLite classification: ${e.message}")
        }
        return ProcessedTextResult(
            originalText = extractionResult.fullText,
            securityRisks = securityRisks,
            sensitiveInformation = sensitiveInfo,
            analysisSuccessful = true
        )
    }

    /**
     * Identify potential security risks in the text
     */
    private fun identifySecurityRisks(text: String): MutableList<SecurityRisk> {
        val risks = mutableListOf<SecurityRisk>()
        
        // Check for password prompts
        if (containsPasswordPrompt(text)) {
            risks.add(SecurityRisk(
                type = SecurityRiskType.PASSWORD_PROMPT,
                description = "Password prompt detected",
                severity = RiskSeverity.HIGH
            ))
        }
        
        // Check for suspicious URLs
        val suspiciousUrls = findSuspiciousUrls(text)
        if (suspiciousUrls.isNotEmpty()) {
            risks.add(SecurityRisk(
                type = SecurityRiskType.SUSPICIOUS_URL,
                description = "Suspicious URL detected: ${suspiciousUrls.joinToString(", ")}",
                severity = RiskSeverity.MEDIUM,
                detectedValues = suspiciousUrls
            ))
        }
        
        // Check for authentication tokens
        val authTokens = findAuthTokens(text)
        if (authTokens.isNotEmpty()) {
            risks.add(SecurityRisk(
                type = SecurityRiskType.AUTH_TOKEN,
                description = "Authentication token detected",
                severity = RiskSeverity.HIGH,
                detectedValues = authTokens
            ))
        }
        
        return risks
    }

    /**
     * Find sensitive information in the text
     */
    private fun findSensitiveInformation(text: String, lines: List<String>): List<SensitiveInformation> {
        val sensitiveInfo = mutableListOf<SensitiveInformation>()
        
        // Find credit card numbers
        val creditCardNumbers = findCreditCardNumbers(text)
        if (creditCardNumbers.isNotEmpty()) {
            sensitiveInfo.add(SensitiveInformation(
                type = SensitiveInfoType.CREDIT_CARD,
                description = "Credit card number detected",
                detectedValues = creditCardNumbers.map { maskCreditCard(it) }
            ))
        }
        
        // Find email addresses
        val emails = findEmails(text)
        if (emails.isNotEmpty()) {
            sensitiveInfo.add(SensitiveInformation(
                type = SensitiveInfoType.EMAIL,
                description = "Email address detected",
                detectedValues = emails
            ))
        }
        
        // Find phone numbers
        val phoneNumbers = findPhoneNumbers(text)
        if (phoneNumbers.isNotEmpty()) {
            sensitiveInfo.add(SensitiveInformation(
                type = SensitiveInfoType.PHONE_NUMBER,
                description = "Phone number detected",
                detectedValues = phoneNumbers
            ))
        }
        
        return sensitiveInfo
    }

    /**
     * Check if text contains a password prompt
     */
    private fun containsPasswordPrompt(text: String): Boolean {
        val lowercaseText = text.lowercase()
        val passwordPatterns = listOf(
            "password",
            "enter password",
            "your password",
            "passcode",
            "passphrase",
            "pin",
            "enter pin",
            "pay now"
        )
        
        return passwordPatterns.any { pattern -> lowercaseText.contains(pattern) }
    }

    /**
     * Find suspicious URLs in text
     */
    private fun findSuspiciousUrls(text: String): List<String> {
        val urlPattern = Pattern.compile(
            "https?://(?:www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_+.~#?&/=]*)",
            Pattern.CASE_INSENSITIVE
        )
        
        val matcher = urlPattern.matcher(text)
        val urls = mutableListOf<String>()
        
        while (matcher.find()) {
            urls.add(matcher.group())
        }
        
        return urls
    }

    /**
     * Find authentication tokens
     */
    private fun findAuthTokens(text: String): List<String> {
        val tokenPatterns = listOf(
            "bearer\\s+[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+",  // JWT token
            "api[-_]?key[=:][\\s]*['\"]?([a-zA-Z0-9]{20,})['\"]?",         // API key
            "access[-_]?token[=:][\\s]*['\"]?([a-zA-Z0-9]{20,})['\"]?"     // Access token
        )
        
        val tokens = mutableListOf<String>()
        tokenPatterns.forEach { pattern ->
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(text)
            while (matcher.find()) {
                tokens.add(matcher.group())
            }
        }
        
        return tokens
    }

    /**
     * Find credit card numbers
     */
    private fun findCreditCardNumbers(text: String): List<String> {
        // Basic pattern for major credit cards
        val creditCardPattern = Pattern.compile(
            "(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|6(?:011|5[0-9]{2})[0-9]{12}|(?:2131|1800|35\\d{3})\\d{11})"
        )
        
        val matcher = creditCardPattern.matcher(text.replace(" ", "").replace("-", ""))
        val cardNumbers = mutableListOf<String>()
        
        while (matcher.find()) {
            val number = matcher.group()
            // Validate using Luhn algorithm for added certainty
            if (isValidLuhn(number)) {
                cardNumbers.add(number)
            }
        }
        
        return cardNumbers
    }

    /**
     * Validate credit card number using Luhn algorithm
     */
    private fun isValidLuhn(cardNumber: String): Boolean {
        var sum = 0
        var alternate = false
        
        for (i in cardNumber.length - 1 downTo 0) {
            var digit = cardNumber[i] - '0'
            if (alternate) {
                digit *= 2
                if (digit > 9) {
                    digit -= 9
                }
            }
            
            sum += digit
            alternate = !alternate
        }
        
        return sum % 10 == 0
    }

    /**
     * Mask credit card number for security
     */
    private fun maskCreditCard(cardNumber: String): String {
        if (cardNumber.length < 4) return cardNumber
        
        val last4 = cardNumber.takeLast(4)
        val maskedPart = "*".repeat(cardNumber.length - 4)
        
        return maskedPart + last4
    }

    /**
     * Find email addresses
     */
    private fun findEmails(text: String): List<String> {
        val emailPattern = Pattern.compile(
            "[a-zA-Z0-9+._-]+@[a-zA-Z0-9._-]+\\.[a-zA-Z0-9_-]+"
        )
        
        val matcher = emailPattern.matcher(text)
        val emails = mutableListOf<String>()
        
        while (matcher.find()) {
            emails.add(matcher.group())
        }
        
        return emails
    }

    /**
     * Find phone numbers
     */
    private fun findPhoneNumbers(text: String): List<String> {
        val phonePatterns = listOf(
            // US style: (123) 456-7890, 123-456-7890, 123.456.7890
            "\\(\\d{3}\\)[-.\\s]?\\d{3}[-.\\s]?\\d{4}",
            "\\d{3}[-.\\s]\\d{3}[-.\\s]\\d{4}",
            
            // International style: +1 123 456 7890
            "\\+\\d{1,3}[-.\\s]?\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{4}"
        )
        
        val phones = mutableListOf<String>()
        phonePatterns.forEach { pattern ->
            val regex = Pattern.compile(pattern)
            val matcher = regex.matcher(text)
            while (matcher.find()) {
                phones.add(matcher.group())
            }
        }
        
        return phones
    }
}

/**
 * Data class to hold the result of text processing
 */
data class ProcessedTextResult(
    val originalText: String,
    val securityRisks: List<SecurityRisk>,
    val sensitiveInformation: List<SensitiveInformation>,
    val analysisSuccessful: Boolean
)

/**
 * Data class for security risks found in the text
 */
data class SecurityRisk(
    val type: SecurityRiskType,
    val description: String,
    val severity: RiskSeverity,
    val detectedValues: List<String> = emptyList()
)

/**
 * Data class for sensitive information found in the text
 */
data class SensitiveInformation(
    val type: SensitiveInfoType,
    val description: String,
    val detectedValues: List<String>
)

/**
 * Types of security risks
 */
enum class SecurityRiskType {
    PASSWORD_PROMPT,
    SUSPICIOUS_URL,
    AUTH_TOKEN,
    LOGIN_PAGE,
    KNOWN_PHISHING
}

/**
 * Security risk severity levels
 */
enum class RiskSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Types of sensitive information
 */
enum class SensitiveInfoType {
    CREDIT_CARD,
    EMAIL,
    PHONE_NUMBER,
    ADDRESS,
    SSN
}