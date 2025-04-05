package com.onetap.security.aianalyzer

import android.content.Context
import android.util.Log
import com.onetap.security.textprocessing.ProcessedTextResult
import com.onetap.security.textprocessing.SecurityRisk
import com.onetap.security.textprocessing.RiskSeverity
import com.onetap.security.textprocessing.SecurityRiskType
import com.onetap.security.textprocessing.SensitiveInformation
import com.onetap.security.textprocessing.TextExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Integration class to combine rule-based text analysis with AI-based analysis
 */
class AIIntegration(private val context: Context) {
    private val TAG = "AIIntegration"
    private val aiAnalyzer = AISecurityAnalyzer(context)
    private var isInitialized = false
    
    /**
     * Initialize the AI integration
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true
        
        try {
            val success = aiAnalyzer.initialize()
            isInitialized = success
            Log.d(TAG, "AI Integration initialized: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AI Integration: ${e.message}")
            false
        }
    }
    
    /**
     * Enhance a rule-based analysis with AI insights
     */
    suspend fun enhanceAnalysis(
        extractionResult: TextExtractionResult,
        ruleBasedResult: ProcessedTextResult
    ): EnhancedAnalysisResult = withContext(Dispatchers.Default) {
        try {
            if (!isInitialized) {
                initialize()
            }
            
            // Perform AI-based analysis
            val aiResult = aiAnalyzer.analyzeText(extractionResult)
            
            // Merge the results
            val mergedRisks = mergeSecurityRisks(ruleBasedResult.securityRisks, aiResult)
            
            // Determine the final verdict
            val securityVerdict = determineSecurityVerdict(mergedRisks, ruleBasedResult)
            
            EnhancedAnalysisResult(
                ruleBasedAnalysis = ruleBasedResult,
                aiAnalysis = aiResult,
                mergedRisks = mergedRisks,
                securityVerdict = securityVerdict,
                success = true
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enhancing analysis: ${e.message}")
            EnhancedAnalysisResult(
                ruleBasedAnalysis = ruleBasedResult,
                aiAnalysis = null,
                mergedRisks = ruleBasedResult.securityRisks,
                securityVerdict = SecurityVerdict.INDETERMINATE,
                success = false,
                errorMessage = "AI enhancement failed: ${e.message}"
            )
        }
    }
    
    /**
     * Merge rule-based security risks with AI analysis
     */
    private fun mergeSecurityRisks(
        ruleBasedRisks: List<SecurityRisk>,
        aiResult: AIAnalysisResult?
    ): List<SecurityRisk> {
        val mergedRisks = ruleBasedRisks.toMutableList()
        
        if (aiResult?.success == true && aiResult.securityAnalysis != null) {
            val aiAnalysis = aiResult.securityAnalysis
            
            // Add AI-detected risk if it's significant
            if (aiAnalysis.primaryCategory != SecurityCategory.SAFE && 
                aiAnalysis.primaryCategory != SecurityCategory.UNKNOWN &&
                aiAnalysis.confidenceScore > TextClassifier.MEDIUM_CONFIDENCE_THRESHOLD) {
                
                // Convert AI category to rule-based type
                val riskType = when (aiAnalysis.primaryCategory) {
                    SecurityCategory.PHISHING -> SecurityRiskType.SUSPICIOUS_URL
                    SecurityCategory.CREDENTIAL_STEALING -> SecurityRiskType.PASSWORD_PROMPT
                    SecurityCategory.FINANCIAL_SCAM -> SecurityRiskType.SUSPICIOUS_URL
                    SecurityCategory.MALWARE -> SecurityRiskType.SUSPICIOUS_URL
                    else -> SecurityRiskType.SUSPICIOUS_URL
                }
                
                // Convert AI severity to rule-based severity
                val severity = when (aiAnalysis.severityLevel) {
                    AISeverityLevel.CRITICAL -> RiskSeverity.CRITICAL
                    AISeverityLevel.HIGH -> RiskSeverity.HIGH
                    AISeverityLevel.MEDIUM -> RiskSeverity.MEDIUM
                    AISeverityLevel.LOW -> RiskSeverity.LOW
                }
                
                // Check if we already have a similar risk
                val existingRisk = mergedRisks.find { it.type == riskType }
                
                if (existingRisk == null) {
                    // Add new AI-detected risk
                    mergedRisks.add(SecurityRisk(
                        type = riskType,
                        description = "AI detected: ${aiAnalysis.primaryCategory.label} " +
                                    "(${(aiAnalysis.confidenceScore * 100).toInt()}% confidence)",
                        severity = severity,
                        detectedValues = aiAnalysis.detectedPatterns
                    ))
                } else {
                    // Upgrade severity if AI detection is more severe
                    if (severity.ordinal > existingRisk.severity.ordinal) {
                        mergedRisks.remove(existingRisk)
                        mergedRisks.add(SecurityRisk(
                            type = existingRisk.type,
                            description = existingRisk.description + " (AI confirmed: ${aiAnalysis.primaryCategory.label})",
                            severity = severity,
                            detectedValues = existingRisk.detectedValues + aiAnalysis.detectedPatterns
                        ))
                    }
                }
            }
        }
        
        return mergedRisks
    }
    
    /**
     * Determine the overall security verdict based on all analysis
     */
    private fun determineSecurityVerdict(
        mergedRisks: List<SecurityRisk>,
        ruleBasedResult: ProcessedTextResult
    ): SecurityVerdict {
        // Check for critical or high risks
        val hasCriticalRisk = mergedRisks.any { it.severity == RiskSeverity.CRITICAL }
        val hasHighRisk = mergedRisks.any { it.severity == RiskSeverity.HIGH }
        val hasMediumRisk = mergedRisks.any { it.severity == RiskSeverity.MEDIUM }
        
        // Check for sensitive information
        val hasSensitiveInfo = ruleBasedResult.sensitiveInformation.isNotEmpty()
        
        return when {
            hasCriticalRisk -> SecurityVerdict.DANGEROUS
            hasHighRisk -> SecurityVerdict.HIGH_RISK
            hasMediumRisk -> SecurityVerdict.SUSPICIOUS
            hasSensitiveInfo -> SecurityVerdict.CONTAINS_SENSITIVE_INFO
            mergedRisks.isNotEmpty() -> SecurityVerdict.LOW_RISK
            else -> SecurityVerdict.SAFE
        }
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        aiAnalyzer.close()
        isInitialized = false
    }
}

/**
 * Enhanced analysis result
 */
data class EnhancedAnalysisResult(
    val ruleBasedAnalysis: ProcessedTextResult,
    val aiAnalysis: AIAnalysisResult?,
    val mergedRisks: List<SecurityRisk>,
    val securityVerdict: SecurityVerdict,
    val success: Boolean,
    val errorMessage: String? = null
)

/**
 * Overall security verdict
 */
enum class SecurityVerdict {
    SAFE,               // No risks or sensitive information detected
    LOW_RISK,           // Minor risks detected, but likely not harmful
    SUSPICIOUS,         // Some suspicious elements detected, caution advised
    HIGH_RISK,          // High security risks detected, caution strongly advised
    DANGEROUS,          // Critical security risks detected, action recommended
    CONTAINS_SENSITIVE_INFO, // Contains sensitive information but no direct security risks
    INDETERMINATE       // Unable to determine the security level
}