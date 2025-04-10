package com.onetap.security

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.onetap.security.ui.screens.ResultScreen
import com.onetap.security.ui.theme.OneTapSecurityTheme

/**
 * Activity to display the results of screen analysis
 */
class ResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get analysis results from intent
        val securityRisks = intent.getStringExtra("security_risks") ?: "No security risks detected"
        val sensitiveInfo = intent.getStringExtra("sensitive_info") ?: "No sensitive information detected"
        val rawText = intent.getStringExtra("raw_text") ?: ""
        
        // Set Compose content
        setContent {
            OneTapSecurityTheme {
                ResultScreen(
                    securityRisks = securityRisks,
                    sensitiveInfo = sensitiveInfo,
                    rawText = rawText
                )
            }
        }
    }
}