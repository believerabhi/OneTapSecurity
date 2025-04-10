package com.onetap.security.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun ResultScreen(
    securityRisks: String,
    sensitiveInfo: String,
    rawText: String
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Title
        Text(
            text = "Screen Analysis Results",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        
        // Security Risks Section
        Text(
            text = "Security Risks",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        
        Text(
            text = securityRisks,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        
        Divider()
        
        // Sensitive Information Section
        Text(
            text = "Sensitive Information",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        
        // Handle verdict coloring
        val verdictStartIndex = sensitiveInfo.indexOf("\n\nVERDICT:")
        Log.d("ResultScreen", "$sensitiveInfo $verdictStartIndex")
        if (verdictStartIndex >= 0) {
            val baseText = sensitiveInfo.substring(0, verdictStartIndex)
            val verdictText = sensitiveInfo.substring(verdictStartIndex)
            
            // Determine color based on verdict
            val verdictColor = when {
                verdictText.contains("DANGEROUS") -> Color.Red
                verdictText.contains("HIGH RISK") -> Color(0xFFF44336) // lighter red
                verdictText.contains("SUSPICIOUS") -> Color(0xFFFF9800) // orange
                verdictText.contains("LOW RISK") -> Color(0xFFFFC107) // amber
                verdictText.contains("SAFE") -> Color(0xFF4CAF50) // green
                else -> MaterialTheme.colorScheme.onBackground
            }
            
            // Create an annotated string with colored verdict
            val annotatedString = buildAnnotatedString {
                append(baseText)
                withStyle(style = SpanStyle(color = verdictColor, fontWeight = FontWeight.Bold)) {
                    append(verdictText)
                }
            }
            
            Text(
                text = annotatedString,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
        } else {
            // No verdict, display normal text
            Text(
                text = sensitiveInfo,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
        }
        
        Divider()
        
        // Extracted Text Section
        Text(
            text = "Extracted Text",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        
        Text(
            text = rawText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
    }
}