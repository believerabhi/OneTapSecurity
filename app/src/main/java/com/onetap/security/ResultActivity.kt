package com.onetap.security

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

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
        
        // Display results
        findViewById<TextView>(R.id.securityRisksTextView).text = securityRisks
        findViewById<TextView>(R.id.sensitiveInfoTextView).text = sensitiveInfo
        findViewById<TextView>(R.id.rawTextView).text = rawText
    }
}