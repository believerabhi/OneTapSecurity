package com.onetap.security

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Activity to display the results of screen analysis
 */
class ResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        
        // Get analysis results from intent
        val securityRisks = intent.getStringExtra("security_risks") ?: "No security risks detected"
        val sensitiveInfo = intent.getStringExtra("sensitive_info") ?: "No sensitive information detected"
        val rawText = intent.getStringExtra("raw_text") ?: ""
        
        // Check if this contains a security verdict
        val verdictStartIndex = sensitiveInfo.indexOf("\n\nVERDICT:")
        if (verdictStartIndex >= 0) {
            // Split the text and add color to the verdict
            val baseText = sensitiveInfo.substring(0, verdictStartIndex)
            val verdictText = sensitiveInfo.substring(verdictStartIndex)
            
            // Create spannable string for colored text
            val spannable = SpannableString(baseText + verdictText)
            
            // Determine color based on verdict content
            val color = when {
                verdictText.contains("DANGEROUS") -> 
                    ContextCompat.getColor(this, android.R.color.holo_red_dark)
                verdictText.contains("HIGH RISK") -> 
                    ContextCompat.getColor(this, android.R.color.holo_red_light)
                verdictText.contains("SUSPICIOUS") -> 
                    ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                verdictText.contains("LOW RISK") -> 
                    ContextCompat.getColor(this, android.R.color.holo_orange_light)
                verdictText.contains("SAFE") -> 
                    ContextCompat.getColor(this, android.R.color.holo_green_dark)
                else -> 
                    ContextCompat.getColor(this, android.R.color.black)
            }
            
            // Apply color to verdict part
            spannable.setSpan(
                ForegroundColorSpan(color),
                baseText.length,
                baseText.length + verdictText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Set colored text
            findViewById<TextView>(R.id.sensitiveInfoTextView).text = spannable
        } else {
            // No verdict, display as normal
            findViewById<TextView>(R.id.sensitiveInfoTextView).text = sensitiveInfo
        }
        
        // Display other results
        findViewById<TextView>(R.id.securityRisksTextView).text = securityRisks
        findViewById<TextView>(R.id.rawTextView).text = rawText
        
        // Add a title that indicates if AI was used
        title = if (sensitiveInfo.contains("AI")) {
            "Analysis Results (AI Enhanced)"
        } else {
            "Analysis Results"
        }
    }
}