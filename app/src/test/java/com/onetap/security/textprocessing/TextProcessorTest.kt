package com.onetap.security.textprocessing

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*

@RunWith(MockitoJUnitRunner::class)
class TextProcessorTest {

    private lateinit var textProcessor: TextProcessor
    
    @Before
    fun setup() {
        textProcessor = TextProcessor()
    }
    
    @Test
    fun `analyzeText should identify password prompts`() {
        // Create mock extraction result with password prompt
        val extractionResult = TextExtractionResult(
            fullText = "Please enter your password to continue",
            lines = listOf("Please enter your password to continue"),
            blocks = listOf(
                TextBlock(
                    text = "Please enter your password to continue",
                    confidence = 0.9f,
                    boundingBox = BoundingBox(0, 0, 100, 20)
                )
            ),
            imagePath = "test.png",
            success = true
        )
        
        // Process the text
        val result = textProcessor.analyzeText(extractionResult)
        
        // Verify password risk was detected
        assertTrue(result.analysisSuccessful)
        assertTrue(result.securityRisks.isNotEmpty())
        assertTrue(result.securityRisks.any { it.type == SecurityRiskType.PASSWORD_PROMPT })
    }
    
    @Test
    fun `analyzeText should identify credit card numbers`() {
        // Create mock extraction result with a credit card number
        val extractionResult = TextExtractionResult(
            fullText = "Your payment method: 4111 1111 1111 1111 Exp: 12/25",
            lines = listOf("Your payment method: 4111 1111 1111 1111 Exp: 12/25"),
            blocks = listOf(
                TextBlock(
                    text = "Your payment method: 4111 1111 1111 1111 Exp: 12/25",
                    confidence = 0.9f,
                    boundingBox = BoundingBox(0, 0, 300, 20)
                )
            ),
            imagePath = "test.png",
            success = true
        )
        
        // Process the text
        val result = textProcessor.analyzeText(extractionResult)
        
        // Verify credit card was detected
        assertTrue(result.analysisSuccessful)
        assertTrue(result.sensitiveInformation.isNotEmpty())
        assertTrue(result.sensitiveInformation.any { it.type == SensitiveInfoType.CREDIT_CARD })
    }
    
    @Test
    fun `analyzeText should identify suspicious URLs`() {
        // Create mock extraction result with suspicious URL
        val extractionResult = TextExtractionResult(
            fullText = "Click here to verify your account: https://bank-secure-verify.com/login",
            lines = listOf("Click here to verify your account: https://bank-secure-verify.com/login"),
            blocks = listOf(
                TextBlock(
                    text = "Click here to verify your account: https://bank-secure-verify.com/login",
                    confidence = 0.9f,
                    boundingBox = BoundingBox(0, 0, 400, 20)
                )
            ),
            imagePath = "test.png",
            success = true
        )
        
        // Process the text
        val result = textProcessor.analyzeText(extractionResult)
        
        // Verify suspicious URL was detected
        assertTrue(result.analysisSuccessful)
        assertTrue(result.securityRisks.isNotEmpty())
        assertTrue(result.securityRisks.any { it.type == SecurityRiskType.SUSPICIOUS_URL })
    }
    
    @Test
    fun `analyzeText should identify email addresses`() {
        // Create mock extraction result with email
        val extractionResult = TextExtractionResult(
            fullText = "Contact us at support@example.com",
            lines = listOf("Contact us at support@example.com"),
            blocks = listOf(
                TextBlock(
                    text = "Contact us at support@example.com",
                    confidence = 0.9f,
                    boundingBox = BoundingBox(0, 0, 200, 20)
                )
            ),
            imagePath = "test.png",
            success = true
        )
        
        // Process the text
        val result = textProcessor.analyzeText(extractionResult)
        
        // Verify email was detected
        assertTrue(result.analysisSuccessful)
        assertTrue(result.sensitiveInformation.isNotEmpty())
        assertTrue(result.sensitiveInformation.any { it.type == SensitiveInfoType.EMAIL })
    }
    
    @Test
    fun `analyzeText should identify phone numbers`() {
        // Create mock extraction result with phone number
        val extractionResult = TextExtractionResult(
            fullText = "Call us at (123) 456-7890",
            lines = listOf("Call us at (123) 456-7890"),
            blocks = listOf(
                TextBlock(
                    text = "Call us at (123) 456-7890",
                    confidence = 0.9f,
                    boundingBox = BoundingBox(0, 0, 200, 20)
                )
            ),
            imagePath = "test.png",
            success = true
        )
        
        // Process the text
        val result = textProcessor.analyzeText(extractionResult)
        
        // Verify phone number was detected
        assertTrue(result.analysisSuccessful)
        assertTrue(result.sensitiveInformation.isNotEmpty())
        assertTrue(result.sensitiveInformation.any { it.type == SensitiveInfoType.PHONE_NUMBER })
    }
    
    @Test
    fun `analyzeText should handle empty text gracefully`() {
        // Create mock extraction result with empty text
        val extractionResult = TextExtractionResult(
            fullText = "",
            lines = emptyList(),
            blocks = emptyList(),
            imagePath = "test.png",
            success = true
        )
        
        // Process the text
        val result = textProcessor.analyzeText(extractionResult)
        
        // Verify empty result
        assertFalse(result.analysisSuccessful)
        assertTrue(result.securityRisks.isEmpty())
        assertTrue(result.sensitiveInformation.isEmpty())
    }
    
    @Test
    fun `analyzeText should handle failed extraction gracefully`() {
        // Create mock failed extraction result
        val extractionResult = TextExtractionResult(
            fullText = "",
            lines = emptyList(),
            blocks = emptyList(),
            imagePath = "test.png",
            success = false,
            errorMessage = "OCR failed"
        )
        
        // Process the text
        val result = textProcessor.analyzeText(extractionResult)
        
        // Verify failed result
        assertFalse(result.analysisSuccessful)
        assertTrue(result.securityRisks.isEmpty())
        assertTrue(result.sensitiveInformation.isEmpty())
    }
}