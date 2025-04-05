package com.onetap.security.aianalyzer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.onetap.security.textprocessing.TextExtractionResult

/**
 * Facade class for AI-powered security analysis of extracted text
 */
class AISecurityAnalyzer(private val context: Context) {
    private val TAG = "AISecurityAnalyzer"
    private val textClassifier = TextClassifier(context)
    
    /**
     * Initialize the analyzer and load models
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = textClassifier.initialize()
            
            // Check if actual model was loaded by trying a simple classification
            val testClassification = textClassifier.classifyText("This is a test.")
            
            // If we got results but the model wasn't loaded, we're in fallback mode
            val usingFallback = testClassification.success && 
                               testClassification.categories.isNotEmpty() &&
                               !testClassification.categories.any { it.confidence < 0.1f || it.confidence > 0.99f }
            
            if (usingFallback) {
                Log.i(TAG, "AI Security Analyzer initialized in fallback mode (rule-based)")
            } else {
                Log.d(TAG, "AI Security Analyzer initialized with ML model: $result")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AI Security Analyzer: ${e.message}")
            false
        }
    }
    
    /**
     * Analyze extracted text for security concerns using AI
     * 
     * @param extractionResult The result from text extraction
     * @return AIAnalysisResult containing the security analysis
     */
    suspend fun analyzeText(extractionResult: TextExtractionResult): AIAnalysisResult = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Starting AI analysis of extracted text")
            
            if (!extractionResult.success || extractionResult.fullText.isEmpty()) {
                return@withContext AIAnalysisResult(
                    success = false,
                    errorMessage = "No valid text to analyze",
                    securityAnalysis = null,
                    extractedText = extractionResult
                )
            }
            
            // Perform AI-based security analysis
            val securityAnalysis = textClassifier.analyzeTextSecurity(extractionResult.fullText)
            
            // Additional analysis on text chunks if needed
            val chunkAnalyses = if (extractionResult.lines.size > 5) {
                // For longer texts, analyze main chunks separately
                val chunks = extractionResult.blocks.map { it.text }
                analyzeTextChunks(chunks)
            } else {
                emptyList()
            }
            
            AIAnalysisResult(
                success = true,
                securityAnalysis = securityAnalysis,
                chunkAnalyses = chunkAnalyses,
                extractedText = extractionResult
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in AI analysis: ${e.message}")
            AIAnalysisResult(
                success = false,
                errorMessage = "AI analysis failed: ${e.message}",
                securityAnalysis = null,
                extractedText = extractionResult
            )
        }
    }
    
    /**
     * Analyze individual chunks of text
     */
    private suspend fun analyzeTextChunks(chunks: List<String>): List<ChunkAnalysis> = withContext(Dispatchers.Default) {
        val results = mutableListOf<ChunkAnalysis>()
        
        // Analyze only chunks with sufficient content
        for (chunk in chunks) {
            if (chunk.length < 20) continue // Skip very short chunks
            
            val analysis = textClassifier.analyzeTextSecurity(chunk)
            
            // Only include chunks with concerning classifications
            if (analysis.primaryCategory != SecurityCategory.SAFE &&
                analysis.primaryCategory != SecurityCategory.UNKNOWN &&
                analysis.confidenceScore > TextClassifier.MEDIUM_CONFIDENCE_THRESHOLD) {
                
                results.add(ChunkAnalysis(
                    textChunk = chunk,
                    security = analysis,
                    position = chunks.indexOf(chunk)
                ))
            }
        }
        
        return@withContext results
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        textClassifier.close()
    }
}

/**
 * Represents the result of AI-based security analysis
 */
data class AIAnalysisResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val securityAnalysis: AISecurityAnalysis?,
    val chunkAnalyses: List<ChunkAnalysis> = emptyList(),
    val extractedText: TextExtractionResult?
)

/**
 * Analysis of a specific chunk of text
 */
data class ChunkAnalysis(
    val textChunk: String,
    val security: AISecurityAnalysis,
    val position: Int
)