package com.onetap.security.aianalyzer

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

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

    fun classifyText(text: String): String {
        if (!initialized || interpreter == null || wordIndex == null) {
            Log.w(TAG, "Classifier not fully initialized, returning fallback result")
            // Perform simple keyword-based classification as fallback
            return fallbackClassify(text)
        }

        try {
            val input = tokenize(text)
            val output = Array(1) { FloatArray(labels.size) }
            interpreter?.run(arrayOf(input), output)
            val prediction = output[0].indices.maxByOrNull { output[0][it] } ?: -1
            val predictedLabel = labels.getOrElse(prediction) { "unknown" }
            Log.d(TAG, "Prediction: $predictedLabel (index: $prediction)")
            return predictedLabel
        } catch (e: Exception) {
            Log.e(TAG, "Error classifying text: ${e.message}")
            return fallbackClassify(text)
        }
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
            val json = JSONObject(jsonStr)

            // Check if "word_index" key exists in the JSON
            if (!json.has("word_index")) {
                Log.e(TAG, "JSON does not contain 'word_index' key")
                return createDefaultWordIndex()
            }

            val wordIndexJson = json.getJSONObject("word_index")
            val wordIndexMap = mutableMapOf<String, Int>()
            val keys = wordIndexJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                wordIndexMap[key] = wordIndexJson.getInt(key)
            }

            Log.d(TAG, "Loaded word index with ${wordIndexMap.size} entries")
            wordIndexMap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading tokenizer: ${e.message}")
            e.printStackTrace()
            createDefaultWordIndex()
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

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    fun isInitialized(): Boolean {
        return initialized
    }
}
