package com.onetap.security.textprocessing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Main facade class for the text processing package
 * Provides a simple API for screenshot analysis
 */
class ScreenAnalyzer(private val context: Context) {
    private val TAG = "ScreenAnalyzer"
    private val textExtractor = TextExtractor(context)
    private val textProcessor = TextProcessor()

    /**
     * Analyze a screenshot from the given file path
     * Extracts text and analyzes it for security risks
     * 
     * @param screenshotPath Path to the screenshot file
     * @return ScreenAnalysisResult containing the results of the analysis
     */
    suspend fun analyzeScreenshot(screenshotPath: String): ScreenAnalysisResult = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Starting screenshot analysis: $screenshotPath")
            
            // Check if file exists
            val file = File(screenshotPath)
            if (!file.exists()) {
                Log.e(TAG, "Screenshot file does not exist: $screenshotPath")
                return@withContext ScreenAnalysisResult(
                    success = false,
                    errorMessage = "Screenshot file not found",
                    extractedText = null,
                    securityAnalysis = null
                )
            }
            
            // Extract text from the screenshot
            val extractionResult = textExtractor.extractTextFromImage(screenshotPath)
            
            // If text extraction failed, return early
            if (!extractionResult.success) {
                return@withContext ScreenAnalysisResult(
                    success = false,
                    errorMessage = "Text extraction failed: ${extractionResult.errorMessage}",
                    extractedText = extractionResult,
                    securityAnalysis = null
                )
            }
            
            // Process the extracted text
            val processedResult = textProcessor.analyzeText(extractionResult)
            
            // Return the complete analysis result
            ScreenAnalysisResult(
                success = true,
                extractedText = extractionResult,
                securityAnalysis = processedResult
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing screenshot: ${e.message}")
            e.printStackTrace()
            
            ScreenAnalysisResult(
                success = false,
                errorMessage = "Error analyzing screenshot: ${e.message}",
                extractedText = null,
                securityAnalysis = null
            )
        }
    }

    /**
     * Clean up resources when the analyzer is no longer needed
     */
    fun close() {
        textExtractor.close()
    }
}

/**
 * Data class to hold the complete result of screenshot analysis
 */
data class ScreenAnalysisResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val extractedText: TextExtractionResult?,
    val securityAnalysis: ProcessedTextResult?
)