package com.onetap.security.aianalyzer

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Data class to hold the prediction result with confidence score
 * @param label The predicted label/class
 * @param confidence The confidence score (0.0-1.0)
 * @param isFallback Whether this prediction was made by the fallback method
 */
data class PredictionResult(
    val label: String,
    val confidence: Float,
    val isFallback: Boolean
)

class CustomTFLiteClassifier(context: Context) {
    private val TAG = "CustomTFLiteClassifier"
    private val maxLen = 50
    private val labels =
        listOf("credential_stealing", "financial_scam", "malware", "phishing", "safe")
    private var wordIndex: Map<String, Int>? = null
    private var interpreter: Interpreter? = null
    private var initialized = false

    init {
        try {
            // Attempt to load the tokenizer
            wordIndex = loadTokenizerSafely(context)

            // Try to load the model file
            val modelBuffer = loadModelFileSafely(context, "phishing_classifier.tflite")
            if (modelBuffer != null) {
                interpreter = Interpreter(modelBuffer)
                initialized = true
                Log.d(TAG, "CustomTFLiteClassifier initialized successfully")
            } else {
                Log.e(TAG, "Failed to load model file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing CustomTFLiteClassifier: ${e.message}")
            e.printStackTrace()
            // Don't throw exception, just mark as not initialized
            initialized = false
        }
    }

    /**
     * Classifies text and returns a PredictionResult containing the predicted label and confidence
     * @param text The text to classify
     * @return PredictionResult with label and confidence
     */
    fun classifyText(text: String): PredictionResult {
        if (!initialized || interpreter == null || wordIndex == null) {
            Log.w(TAG, "Classifier not fully initialized, returning fallback result")
            // Perform confidence-based fallback classification
            return fallbackClassifyWithConfidence(text)
        }

        try {
            val input = tokenize(text)
            val output = Array(1) { FloatArray(labels.size) }
            interpreter?.run(arrayOf(input), output)
            // Find the index with maximum value
            val prediction = output[0].indices.maxByOrNull { output[0][it] } ?: -1
            
            // Get the predicted label
            val predictedLabel = labels.getOrElse(prediction) { "unknown" }
            
            // Get the confidence score for the prediction (the value at the max index)
            val confidence = if (prediction >= 0) output[0][prediction] else 0.0f
            
            // Apply softmax to get a probability between 0 and 1
            val probabilities = softmax(output[0])
            val normalizedConfidence = probabilities[prediction]
            
            Log.d(TAG, "Prediction: $predictedLabel (index: $prediction, confidence: $normalizedConfidence)")
            
            return PredictionResult(predictedLabel, normalizedConfidence, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error classifying text: ${e.message}")
            return fallbackClassifyWithConfidence(text)
        }
    }
    
    /**
     * Apply softmax function to convert raw model outputs to probabilities
     * @param output The raw output array from the model
     * @return FloatArray of probabilities that sum to 1.0
     */
    private fun softmax(output: FloatArray): FloatArray {
        val result = FloatArray(output.size)
        var sum = 0.0f
        
        // Apply exp to each value and sum
        for (i in output.indices) {
            result[i] = Math.exp(output[i].toDouble()).toFloat()
            sum += result[i]
        }
        
        // Normalize to get probabilities
        if (sum > 0) {
            for (i in result.indices) {
                result[i] /= sum
            }
        }
        
        return result
    }

    private fun tokenize(text: String): FloatArray {
        if (wordIndex == null) {
            Log.e(TAG, "Word index is null, cannot tokenize")
            return FloatArray(maxLen)
        }

        val tokens = text.lowercase().split(" ")
        val result = FloatArray(maxLen)
        for (i in tokens.indices) {
            if (i >= maxLen) break
            result[i] = wordIndex?.get(tokens[i])?.toFloat() ?: 0f
        }
        return result
    }

    private fun loadTokenizerSafely(context: Context): Map<String, Int>? {
        return try {
            val tokenizer = context.assets.list("")?.find { it == "tokenizer.json" }

            if (tokenizer == null) {
                Log.e(TAG, "tokenizer.json not found in assets")
                return createDefaultWordIndex()
            }

            val jsonStr =
                context.assets.open("tokenizer.json").bufferedReader().use { it.readText() }
            try {
                val json = JSONObject(jsonStr)

                // Try to find word_index directly in the main object
                if (json.has("word_index")) {
                    Log.d(TAG, "Found word_index in main JSON object")
                    val wordIndexJson = json.getJSONObject("word_index")
                    return extractWordIndexMap(wordIndexJson)
                }

                // Try to find word_index in the config object
                if (json.has("config")) {
                    val config = json.getJSONObject("config")
                    if (config.has("word_index")) {
                        Log.d(TAG, "Found word_index in config object")
                        val wordIndexJson = config.getJSONObject("word_index")
                        return extractWordIndexMap(wordIndexJson)
                    }
                }

                // Couldn't find word_index in expected locations
                Log.e(TAG, "Could not find word_index in JSON structure")
                return createDefaultWordIndex()

            } catch (e: Exception) {
                Log.e(TAG, "Error parsing JSON: ${e.message}")
                return createDefaultWordIndex()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading tokenizer: ${e.message}")
            e.printStackTrace()
            createDefaultWordIndex()
        }
    }
    
    // Helper function to extract the word index map from a JSONObject
    private fun extractWordIndexMap(wordIndexJson: JSONObject): Map<String, Int> {
        val wordIndexMap = mutableMapOf<String, Int>()
        try {
            val keys = wordIndexJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                wordIndexMap[key] = wordIndexJson.getInt(key)
            }
            Log.d(TAG, "Loaded word index with ${wordIndexMap.size} entries")
            return wordIndexMap
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting word index map: ${e.message}")
            return createDefaultWordIndex()
        }
    }

    private fun createDefaultWordIndex(): Map<String, Int> {
        Log.d(TAG, "Creating default word index with common security terms")
        // Common words related to security, phishing, etc.
        return mapOf(
            "password" to 1,
            "login" to 2,
            "account" to 3,
            "verify" to 4,
            "secure" to 5,
            "bank" to 6,
            "credit" to 7,
            "card" to 8,
            "security" to 9,
            "alert" to 10,
            "suspicious" to 11,
            "activity" to 12,
            "confirm" to 13,
            "identity" to 14,
            "click" to 15,
            "link" to 16,
            "unauthorized" to 17,
            "access" to 18,
            "update" to 19,
            "information" to 20,
            "payment" to 21,
            "ssn" to 22,
            "social" to 23,
            "security" to 24,
            "number" to 25,
            "google" to 26,
            "apple" to 27,
            "microsoft" to 28,
            "facebook" to 29,
            "amazon" to 30,
            "paypal" to 31,
            "download" to 32,
            "malware" to 33,
            "virus" to 34,
            "trojan" to 35,
            "warning" to 36,
            "urgent" to 37,
            "immediate" to 38,
            "action" to 39,
            "required" to 40
        )
    }

    private fun loadModelFileSafely(context: Context, modelName: String): MappedByteBuffer? {
        return try {
            val hasModel = context.assets.list("")?.contains(modelName) ?: false

            if (!hasModel) {
                Log.e(TAG, "$modelName not found in assets")
                return null
            }

            val fileDescriptor = context.assets.openFd(modelName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model file: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Fallback classification using keyword matching
     * @param text The text to classify
     * @return The predicted label
     */
    private fun fallbackClassify(text: String): String {
        val lowercaseText = text.lowercase()

        // Check for credential stealing patterns
        if ((lowercaseText.contains("password") || lowercaseText.contains("login")) &&
            (lowercaseText.contains("enter") || lowercaseText.contains("verify") ||
                    lowercaseText.contains("confirm"))
        ) {
            return "credential_stealing"
        }

        // Check for phishing patterns
        if ((lowercaseText.contains("account") && lowercaseText.contains("verify")) ||
            (lowercaseText.contains("suspicious") && lowercaseText.contains("activity")) ||
            (lowercaseText.contains("click") && lowercaseText.contains("link"))
        ) {
            return "phishing"
        }

        // Check for financial scam patterns
        if (lowercaseText.contains("bank") ||
            lowercaseText.contains("credit card") ||
            lowercaseText.contains("payment") ||
            lowercaseText.contains("money")
        ) {
            return "financial_scam"
        }

        // Check for malware patterns
        if (lowercaseText.contains("download") ||
            lowercaseText.contains("install") ||
            lowercaseText.contains("update") &&
            lowercaseText.contains("now")
        ) {
            return "malware"
        }

        // Default to safe if no patterns match
        return "safe"
    }
    
    /**
     * Fallback classification with confidence estimation
     * @param text The text to classify
     * @return PredictionResult with label and estimated confidence
     */
    private fun fallbackClassifyWithConfidence(text: String): PredictionResult {
        val lowercaseText = text.lowercase()
        var confidence = 0.7f // Default medium confidence for fallback
        var matchCount = 0
        var totalPatterns = 0
        
        // Count matches for credential stealing
        totalPatterns += 3  // We check for 3 patterns
        if (lowercaseText.contains("password")) matchCount++
        if (lowercaseText.contains("login")) matchCount++
        if (lowercaseText.contains("enter") || lowercaseText.contains("verify") || 
            lowercaseText.contains("confirm")) matchCount++
            
        if (matchCount == 3) {
            confidence = 0.85f  // Higher confidence with more matches
            return PredictionResult("credential_stealing", confidence, true)
        }
        
        // Reset and check for phishing
        matchCount = 0
        totalPatterns = 3
        if (lowercaseText.contains("account")) matchCount++
        if (lowercaseText.contains("verify")) matchCount++
        if (lowercaseText.contains("suspicious")) matchCount++
        if (lowercaseText.contains("activity")) matchCount++
        if (lowercaseText.contains("click")) matchCount++
        if (lowercaseText.contains("link")) matchCount++
        
        if (matchCount >= 4) {
            confidence = 0.82f
            return PredictionResult("phishing", confidence, true)
        }
        
        // Reset and check for financial scam
        matchCount = 0
        if (lowercaseText.contains("bank")) matchCount++
        if (lowercaseText.contains("credit card")) matchCount++
        if (lowercaseText.contains("payment")) matchCount++
        if (lowercaseText.contains("money")) matchCount++
        
        if (matchCount >= 2) {
            confidence = 0.78f
            return PredictionResult("financial_scam", confidence, true)
        }
        
        // Reset and check for malware
        matchCount = 0
        if (lowercaseText.contains("download")) matchCount++
        if (lowercaseText.contains("install")) matchCount++
        if (lowercaseText.contains("update")) matchCount++
        if (lowercaseText.contains("now")) matchCount++
        
        if (matchCount >= 2) {
            confidence = 0.75f
            return PredictionResult("malware", confidence, true)
        }
        
        // Default to safe with high confidence if no patterns match
        return PredictionResult("safe", 0.9f, true)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    fun isInitialized(): Boolean {
        return initialized
    }
}
