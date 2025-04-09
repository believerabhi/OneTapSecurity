package com.onetap.security.textprocessing

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main facade class for the text processing package
 * Provides a simple API for screenshot analysis
 */
class ScreenAnalyzer(private val context: Context) {
    private val TAG = "ScreenAnalyzer"
    private val textExtractor = TextExtractor(context)
    private val textProcessor = TextProcessor(context)

    /**
     * Analyze a bitmap directly without saving to disk
     * Extracts text and analyzes it for security risks
     *
     * @param bitmap The bitmap to analyze
     * @return ScreenAnalysisResult containing the results of the analysis
     */
    suspend fun analyzeBitmap(bitmap: Bitmap): ScreenAnalysisResult =
        withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "Starting bitmap analysis: ${bitmap.width}x${bitmap.height}")

                // Extract text from the bitmap
                val extractionResult = textExtractor.extractTextFromBitmap(bitmap)

                // If text extraction failed, return early
                if (!extractionResult.success) {
                    return@withContext ScreenAnalysisResult(
                        success = false,
                        errorMessage = "Text extraction failed: ${extractionResult.errorMessage}",
                        extractedText = extractionResult,
                        securityAnalysis = null
                    )
                }

                // Process the extracted text - with error handling
                try {
                    val processedResult = textProcessor.analyzeText(extractionResult)

                    // Return the complete analysis result
                    ScreenAnalysisResult(
                        success = true,
                        extractedText = extractionResult,
                        securityAnalysis = processedResult
                    )
                } catch (textProcessingException: Exception) {
                    Log.e(TAG, "Error in text processing: ${textProcessingException.message}")
                    textProcessingException.printStackTrace()

                    // Return partial result with the extracted text but no security analysis
                    ScreenAnalysisResult(
                        success = false,
                        errorMessage = "Error analyzing text: ${textProcessingException.message}",
                        extractedText = extractionResult,
                        securityAnalysis = ProcessedTextResult(
                            originalText = extractionResult.fullText,
                            securityRisks = emptyList(),
                            sensitiveInformation = emptyList(),
                            analysisSuccessful = false
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing bitmap: ${e.message}")
                e.printStackTrace()

                ScreenAnalysisResult(
                    success = false,
                    errorMessage = "Error analyzing bitmap: ${e.message}",
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