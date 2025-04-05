package com.onetap.security.aianalyzer

import android.content.Context
import android.util.Log
import org.tensorflow.lite.support.label.Category
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.text.nlclassifier.NLClassifier
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Class that handles text classification using TensorFlow Lite
 */
class TextClassifier(private val context: Context) {
    private val TAG = "TextClassifier"
    private var classifier: NLClassifier? = null
    private var isInitialized = false
    
    // Model names
    companion object {
        const val SIMPLE_MODEL_FILE = "security_text_classifier.tflite"
        
        // Threshold values for classification confidence
        const val HIGH_CONFIDENCE_THRESHOLD = 0.75f
        const val MEDIUM_CONFIDENCE_THRESHOLD = 0.5f
        const val LOW_CONFIDENCE_THRESHOLD = 0.25f
    }
    
    /**
     * Initialize the text classifier with the appropriate model
     */
    suspend fun initialize(useAdvancedModel: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                return@withContext true
            }
            
            // Check if model file exists
            val modelExists = try {
                context.assets.open(SIMPLE_MODEL_FILE).close()
                true
            } catch (e: Exception) {
                Log.w(TAG, "Model file $SIMPLE_MODEL_FILE not found, using fallback mode")
                false
            }
            
            if (modelExists) {
                try {
                    // Simple model initialization
                    classifier = NLClassifier.createFromFile(context, SIMPLE_MODEL_FILE)
                    
                    isInitialized = true
                    Log.d(TAG, "Text classifier initialized successfully with model")
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading model: ${e.message}")
                    // Fall back to rule-based mode
                    isInitialized = true 
                }
            } else {
                // Model not available, use fallback mode (rule-based only)
                Log.d(TAG, "No model available, using rule-based analysis only")
                isInitialized = true 
            }
            
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing text classifier: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }
    
    /**
     * Classify the given text using the loaded model
     */
    suspend fun classifyText(text: String): TextClassificationResult = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            val initialized = initialize()
            if (!initialized) {
                return@withContext TextClassificationResult(
                    categories = emptyList(),
                    inputText = text,
                    success = false,
                    errorMessage = "Classifier not initialized"
                )
            }
        }
        
        try {
            // Check if we have a valid classifier
            if (classifier != null) {
                // Use standard classifier
                val results = classifier!!.classify(text)
                processResults(results, text)
            } else {
                // No model available, use rule-based fallback classification
                Log.d(TAG, "Using fallback rule-based classification for: ${text.take(50)}...")
                fallbackClassify(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error classifying text: ${e.message}")
            return@withContext TextClassificationResult(
                categories = emptyList(),
                inputText = text,
                success = false,
                errorMessage = "Classification failed: ${e.message}"
            )
        }
    }
    
    /**
     * Process ML Kit results into our model
     */
    private fun processResults(results: List<Category>, text: String): TextClassificationResult {
        val categories = results.map { category ->
            ClassificationCategory(
                label = category.label,
                confidence = category.score
            )
        }.sortedByDescending { it.confidence }
        
        return TextClassificationResult(
            categories = categories,
            inputText = text,
            success = true
        )
    }
    
    /**
     * Fallback classification using rule-based patterns
     * Used when no TFLite model is available
     */
    private fun fallbackClassify(text: String): TextClassificationResult {
        val lowercaseText = text.lowercase()
        val categories = mutableListOf<ClassificationCategory>()
        
        // Check for phishing indicators
        val phishingScore = calculatePhishingScore(lowercaseText)
        if (phishingScore > 0) {
            categories.add(ClassificationCategory("phishing", phishingScore))
        }
        
        // Check for credential stealing indicators
        val credentialScore = calculateCredentialStealingScore(lowercaseText)
        if (credentialScore > 0) {
            categories.add(ClassificationCategory("credential_stealing", credentialScore))
        }
        
        // Check for financial scam indicators
        val scamScore = calculateFinancialScamScore(lowercaseText)
        if (scamScore > 0) {
            categories.add(ClassificationCategory("financial_scam", scamScore))
        }
        
        // Check for malware indicators
        val malwareScore = calculateMalwareScore(lowercaseText)
        if (malwareScore > 0) {
            categories.add(ClassificationCategory("malware", malwareScore))
        }
        
        // If no risks detected, mark as safe
        if (categories.isEmpty()) {
            categories.add(ClassificationCategory("safe", 0.9f))
        }
        
        return TextClassificationResult(
            categories = categories.sortedByDescending { it.confidence },
            inputText = text,
            success = true
        )
    }
    
    /**
     * Calculate phishing confidence score based on patterns
     */
    private fun calculatePhishingScore(text: String): Float {
        var score = 0f
        
        // Common phishing phrases
        val phishingPhrases = listOf(
            "verify your account",
            "confirm your identity",
            "unusual activity",
            "login attempt",
            "click here",
            "security alert",
            "update your information",
            "limited time",
            "urgent action required"
        )
        
        for (phrase in phishingPhrases) {
            if (text.contains(phrase)) {
                score += 0.15f
            }
        }
        
        // URLs that don't match the claimed identity
        if (text.contains("http") && 
            (text.contains("secure") || text.contains("bank") || text.contains("account"))) {
            score += 0.2f
        }
        
        return score.coerceAtMost(0.95f)
    }
    
    /**
     * Calculate credential stealing confidence score based on patterns
     */
    private fun calculateCredentialStealingScore(text: String): Float {
        var score = 0f
        
        // Common credential request phrases
        if (text.contains("password")) score += 0.3f
        if (text.contains("login")) score += 0.2f
        if (text.contains("username")) score += 0.2f
        if (text.contains("sign in")) score += 0.2f
        if (text.contains("credentials")) score += 0.3f
        if (text.contains("verify") && text.contains("account")) score += 0.25f
        if (text.contains("authentication")) score += 0.2f
        if (text.contains("security code")) score += 0.25f
        if (text.contains("pin")) score += 0.3f
        
        return score.coerceAtMost(0.95f)
    }
    
    /**
     * Calculate financial scam confidence score based on patterns
     */
    private fun calculateFinancialScamScore(text: String): Float {
        var score = 0f
        
        // Common financial scam indicators
        if (text.contains("million") || text.contains("billion")) score += 0.3f
        if (text.contains("lottery") || text.contains("prize")) score += 0.4f
        if (text.contains("inheritance")) score += 0.4f
        if (text.contains("prince") || text.contains("diplomat")) score += 0.4f
        if (text.contains("fund transfer")) score += 0.35f
        if (text.contains("investment opportunity")) score += 0.3f
        if (text.contains("guaranteed returns")) score += 0.4f
        if (text.contains("offshore")) score += 0.25f
        if (text.contains("urgent") && (text.contains("business") || text.contains("money"))) score += 0.3f
        
        return score.coerceAtMost(0.95f)
    }
    
    /**
     * Calculate malware score based on patterns
     */
    private fun calculateMalwareScore(text: String): Float {
        var score = 0f
        
        // Common malware distribution phrases
        if (text.contains("download") && text.contains("update")) score += 0.3f
        if (text.contains("flash player")) score += 0.4f
        if (text.contains("install") && text.contains("required")) score += 0.25f
        if (text.contains("virus") && text.contains("detected")) score += 0.4f
        if (text.contains("clean") && text.contains("computer")) score += 0.3f
        if (text.contains("enable macros")) score += 0.5f
        if (text.contains(".exe") || text.contains(".zip") || text.contains(".rar")) score += 0.25f
        if (text.contains("patch") || text.contains("crack")) score += 0.3f
        if (text.contains("torrent")) score += 0.2f
        
        return score.coerceAtMost(0.95f)
    }
    
    /**
     * Conducts a detailed security analysis on the text
     */
    suspend fun analyzeTextSecurity(text: String): AISecurityAnalysis = withContext(Dispatchers.Default) {
        // First classify the text
        val classification = classifyText(text)
        
        if (!classification.success || classification.categories.isEmpty()) {
            return@withContext AISecurityAnalysis(
                primaryCategory = SecurityCategory.UNKNOWN,
                confidenceScore = 0f,
                otherCategories = emptyList(),
                severityLevel = AISeverityLevel.LOW,
                detectedPatterns = emptyList(),
                explanation = "Could not analyze text: ${classification.errorMessage ?: "Unknown error"}"
            )
        }
        
        // Map the categories to our security categories
        val mappedCategories = classification.categories.map { category ->
            val securityCategory = mapToSecurityCategory(category.label)
            Pair(securityCategory, category.confidence)
        }
        
        val primaryCategory = mappedCategories.first()
        val otherCategories = mappedCategories.drop(1)
        
        // Determine severity based on category and confidence
        val severityLevel = determineSeverity(primaryCategory.first, primaryCategory.second)
        
        // Analyze patterns in the text that triggered the classification
        val detectedPatterns = detectPatterns(text, primaryCategory.first)
        
        // Generate explanation
        val explanation = generateExplanation(primaryCategory, severityLevel, detectedPatterns)
        
        AISecurityAnalysis(
            primaryCategory = primaryCategory.first,
            confidenceScore = primaryCategory.second,
            otherCategories = otherCategories,
            severityLevel = severityLevel,
            detectedPatterns = detectedPatterns,
            explanation = explanation
        )
    }
    
    /**
     * Map a model label to a SecurityCategory
     */
    private fun mapToSecurityCategory(label: String): SecurityCategory {
        return when (label.lowercase()) {
            "phishing" -> SecurityCategory.PHISHING
            "malware" -> SecurityCategory.MALWARE
            "credential_stealing" -> SecurityCategory.CREDENTIAL_STEALING
            "financial_scam" -> SecurityCategory.FINANCIAL_SCAM
            "safe" -> SecurityCategory.SAFE
            "suspicious" -> SecurityCategory.SUSPICIOUS
            else -> SecurityCategory.UNKNOWN
        }
    }
    
    /**
     * Determine the severity level based on category and confidence
     */
    private fun determineSeverity(category: SecurityCategory, confidence: Float): AISeverityLevel {
        return when (category) {
            SecurityCategory.SAFE -> AISeverityLevel.LOW
            SecurityCategory.UNKNOWN -> AISeverityLevel.LOW
            SecurityCategory.SUSPICIOUS -> {
                when {
                    confidence > HIGH_CONFIDENCE_THRESHOLD -> AISeverityLevel.MEDIUM
                    else -> AISeverityLevel.LOW
                }
            }
            SecurityCategory.PHISHING, 
            SecurityCategory.CREDENTIAL_STEALING -> {
                when {
                    confidence > HIGH_CONFIDENCE_THRESHOLD -> AISeverityLevel.CRITICAL
                    confidence > MEDIUM_CONFIDENCE_THRESHOLD -> AISeverityLevel.HIGH
                    else -> AISeverityLevel.MEDIUM
                }
            }
            SecurityCategory.MALWARE,
            SecurityCategory.FINANCIAL_SCAM -> {
                when {
                    confidence > HIGH_CONFIDENCE_THRESHOLD -> AISeverityLevel.CRITICAL
                    confidence > MEDIUM_CONFIDENCE_THRESHOLD -> AISeverityLevel.HIGH
                    else -> AISeverityLevel.MEDIUM
                }
            }
        }
    }
    
    /**
     * Detect patterns in the text based on the primary category
     */
    private fun detectPatterns(text: String, category: SecurityCategory): List<String> {
        val patterns = mutableListOf<String>()
        val lowercaseText = text.lowercase()
        
        // Simple pattern detection based on category
        when (category) {
            SecurityCategory.PHISHING -> {
                if (lowercaseText.contains("account") && 
                    (lowercaseText.contains("verify") || lowercaseText.contains("confirm"))) {
                    patterns.add("Account verification request")
                }
                if (lowercaseText.contains("login") && lowercaseText.contains("unusual")) {
                    patterns.add("Unusual login activity claim")
                }
                if (lowercaseText.contains("click") && lowercaseText.contains("link")) {
                    patterns.add("Request to click on link")
                }
            }
            SecurityCategory.CREDENTIAL_STEALING -> {
                if (lowercaseText.contains("password") || lowercaseText.contains("login")) {
                    patterns.add("Password or login request")
                }
                if (lowercaseText.contains("credit card") || 
                    lowercaseText.contains("card number") || 
                    lowercaseText.contains("cvv")) {
                    patterns.add("Credit card information request")
                }
            }
            SecurityCategory.FINANCIAL_SCAM -> {
                if (lowercaseText.contains("money") || 
                    lowercaseText.contains("payment") || 
                    lowercaseText.contains("bank")) {
                    patterns.add("Financial transaction reference")
                }
                if (lowercaseText.contains("urgent") || lowercaseText.contains("immediately")) {
                    patterns.add("Urgency indicators")
                }
            }
            else -> {
                // No specific patterns for other categories
            }
        }
        
        return patterns
    }
    
    /**
     * Generate an explanation for the security analysis
     */
    private fun generateExplanation(
        primaryCategory: Pair<SecurityCategory, Float>,
        severityLevel: AISeverityLevel,
        patterns: List<String>
    ): String {
        val (category, confidence) = primaryCategory
        val confidencePercentage = (confidence * 100).toInt()
        
        val baseExplanation = when (category) {
            SecurityCategory.SAFE -> 
                "This content appears to be safe with $confidencePercentage% confidence."
            SecurityCategory.UNKNOWN -> 
                "The nature of this content could not be determined with confidence."
            SecurityCategory.SUSPICIOUS -> 
                "This content contains some suspicious elements ($confidencePercentage% confidence)."
            SecurityCategory.PHISHING -> 
                "This content appears to be a phishing attempt ($confidencePercentage% confidence)."
            SecurityCategory.CREDENTIAL_STEALING -> 
                "This content appears to be attempting to steal credentials ($confidencePercentage% confidence)."
            SecurityCategory.FINANCIAL_SCAM -> 
                "This content appears to be a financial scam ($confidencePercentage% confidence)."
            SecurityCategory.MALWARE -> 
                "This content may be related to malware distribution ($confidencePercentage% confidence)."
        }
        
        val patternExplanation = if (patterns.isNotEmpty()) {
            "\n\nDetected patterns: ${patterns.joinToString(", ")}."
        } else {
            ""
        }
        
        val severityExplanation = when (severityLevel) {
            AISeverityLevel.CRITICAL -> 
                "\n\nThis represents a CRITICAL security risk and immediate action is recommended."
            AISeverityLevel.HIGH -> 
                "\n\nThis represents a HIGH security risk and caution is strongly advised."
            AISeverityLevel.MEDIUM -> 
                "\n\nThis represents a MEDIUM security risk and caution is advised."
            AISeverityLevel.LOW -> 
                "\n\nThis represents a LOW security risk, but always remain vigilant."
        }
        
        return baseExplanation + patternExplanation + severityExplanation
    }
    
    /**
     * Get a model file from assets
     */
    private fun getModelFile(modelName: String): File {
        try {
            val file = File(context.filesDir, modelName)
            if (file.exists() && file.length() > 0) {
                return file
            }
            
            // Copy file from assets
            context.assets.open(modelName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.flush()
                }
            }
            return file
        } catch (e: IOException) {
            Log.e(TAG, "Error loading model file $modelName: ${e.message}")
            throw RuntimeException("Error loading model file $modelName", e)
        }
    }
    
    /**
     * Close and release resources
     */
    fun close() {
        classifier?.close()
        isInitialized = false
    }
}