package com.onetap.security.aianalyzer

/**
 * This class contains sample training data for the security text classifier.
 * In a production environment, you would use much larger datasets stored
 * in files, but this provides examples for testing and development.
 */
object SecurityDatasets {

    /**
     * Sample phishing examples
     */
    val phishingExamples = listOf(
        "Your account has been locked due to suspicious activity. Click here to verify your identity.",
        "We've detected unusual login attempts on your account. Verify your password now.",
        "Your payment was declined. Update your payment information to continue your subscription.",
        "Security alert: Your account has been compromised. Reset your password immediately.",
        "Your package delivery has failed. Click here to reschedule.",
        "Your Apple ID was used to sign in on a new device. If this wasn't you, verify your account now.",
        "Your Netflix subscription will be cancelled. Update your billing information now.",
        "Your bank account has been temporarily suspended. Verify your identity to restore access.",
        "You have won a free gift card! Click here to claim your prize.",
        "Your tax refund is ready. Click here to claim it now.",
        "Important notice from PayPal: Your account requires verification.",
        "Your order has been shipped. Track your package here.",
        "Your account password will expire today. Click here to update it.",
        "Your Instagram followers are talking about you. See what they're saying.",
        "We are updating our security systems. Confirm your account details here."
    )

    /**
     * Sample credential stealing examples
     */
    val credentialStealingExamples = listOf(
        "Please enter your username and password to continue.",
        "Your session has expired. Please sign in again.",
        "Confirm your identity by entering your social security number.",
        "Please provide your credit card information to verify your account.",
        "Enter your PIN to access your account details.",
        "To secure your account, please enter your password again.",
        "We need to verify your identity. Please enter your date of birth and mother's maiden name.",
        "For security reasons, confirm your CVV number and expiration date.",
        "Your account needs verification. Enter your account number and routing number.",
        "Please enter your PayPal email and password to claim your refund.",
        "To unlock full access, enter your subscription details below.",
        "Your password must be reset. Enter your current password and new password.",
        "Complete your profile by adding your payment details.",
        "Sign in to Google to access this content.",
        "Please enter your Apple ID and password to continue with download."
    )

    /**
     * Sample financial scam examples
     */
    val financialScamExamples = listOf(
        "I am a banker with access to millions of dollars. I need your help to transfer the funds.",
        "Congratulations! You've been selected to receive $5,000,000 from our international lottery.",
        "Urgent investment opportunity: Guaranteed 50% returns in just one week.",
        "Your inheritance of $3.5 million is waiting. Contact us immediately to claim it.",
        "Help a prince transfer money and receive 20% commission on $10,000,000.",
        "We found unclaimed property in your name. Pay a small fee to release the funds.",
        "Make $1000 daily working from home. No experience required.",
        "Your cryptocurrency investment can triple in value in just 24 hours.",
        "Charity needs urgent donations for disaster victims. Wire money to this account.",
        "Government grant of $8,500 has been approved. Pay processing fee to receive funds.",
        "Your tax refund of $4,890 is pending. Provide bank details for direct deposit.",
        "Real estate investment opportunity: 300% returns guaranteed in 6 months.",
        "Binary options trading system: Make $500 per day automatically.",
        "Debt consolidation offer: Pay off all your debts for just $299.",
        "Claim your unclaimed insurance policy worth $750,000. Contact us immediately."
    )

    /**
     * Sample malware distribution examples
     */
    val malwareExamples = listOf(
        "Download this file to clean your computer from viruses.",
        "Your computer is infected with malware. Install this security tool immediately.",
        "Your Adobe Flash Player is outdated. Update now by clicking this link.",
        "Your browser needs an urgent security update. Download now.",
        "Important system update required. Run this file to update Windows.",
        "Your device has 5 viruses! Download this cleaner app now.",
        "Free antivirus scan detected problems. Install this software to fix them.",
        "Your WhatsApp has a new voice message. Download to listen.",
        "Important document attached. Enable macros to view content.",
        "Your system is vulnerable. Download this patch immediately.",
        "Your Chrome browser needs updating. Click here to install latest version.",
        "Attention: Your device has been compromised. Install security app now.",
        "Your Java runtime needs updating. Download and install new version.",
        "Important invoice attached. Open attachment to view details.",
        "Security alert: Your device is at risk. Install protection software now."
    )

    /**
     * Sample safe examples
     */
    val safeExamples = listOf(
        "Thank you for your order. Your receipt is attached.",
        "Your flight is confirmed for tomorrow at 3:30 PM. Check in opens 24 hours before departure.",
        "Meeting scheduled for Friday at 2 PM in Conference Room B.",
        "Happy birthday! Wishing you a wonderful day filled with joy.",
        "The restaurant reservation is confirmed for 4 people at 7:30 PM.",
        "Your subscription has been renewed. Thank you for your continued support.",
        "The weather forecast for tomorrow is sunny with a high of 75°F.",
        "Your package has been delivered and left at the front door.",
        "Reminder: Doctor's appointment tomorrow at 10:15 AM.",
        "Congratulations on your work anniversary! Thank you for your contributions.",
        "Your photo albums have been successfully backed up to the cloud.",
        "The library books you borrowed are due next Monday.",
        "Traffic alert: Accident on Highway 101 causing 15-minute delays.",
        "Your bill payment was successfully processed. Thank you.",
        "New article published: 10 tips for better sleep habits."
    )

    /**
     * Generate a balanced training dataset with all categories
     */
    fun generateBalancedDataset(samplesPerCategory: Int = 10): List<ModelTrainer.TrainingExample> {
        val allExamples = mutableListOf<ModelTrainer.TrainingExample>()
        
        // Add a balanced sample of each category
        phishingExamples.take(samplesPerCategory).forEach { 
            allExamples.add(ModelTrainer.TrainingExample(it, "phishing")) 
        }
        
        credentialStealingExamples.take(samplesPerCategory).forEach { 
            allExamples.add(ModelTrainer.TrainingExample(it, "credential_stealing")) 
        }
        
        financialScamExamples.take(samplesPerCategory).forEach { 
            allExamples.add(ModelTrainer.TrainingExample(it, "financial_scam")) 
        }
        
        malwareExamples.take(samplesPerCategory).forEach { 
            allExamples.add(ModelTrainer.TrainingExample(it, "malware")) 
        }
        
        safeExamples.take(samplesPerCategory).forEach { 
            allExamples.add(ModelTrainer.TrainingExample(it, "safe")) 
        }
        
        return allExamples.shuffled() // Shuffle to avoid order bias
    }
    
    /**
     * Generate pattern templates for synthetic data
     */
    fun getSyntheticPatterns(): Map<String, List<String>> {
        return mapOf(
            "phishing" to listOf(
                "Your {COMPANY} account needs {ACTION}. Click the link below to continue.",
                "{URGENCY} security alert: Your account shows {THREAT}. {ACTION} now.",
                "We detected unusual activity in your account. Verify your identity.",
                "Your subscription will expire soon. Update your payment information."
            ),
            
            "credential_stealing" to listOf(
                "Please enter your {COMPANY} {CREDENTIAL} to continue.",
                "Your session has expired. Sign in again to continue.",
                "Verify your identity by entering your account details below.",
                "Security check: Please confirm your {CREDENTIAL}."
            ),
            
            "financial_scam" to listOf(
                "Congratulations! You've won $1,000,000 in our lottery. Contact us to claim.",
                "Investment opportunity: Guaranteed 40% returns in just two weeks.",
                "I am a banker with access to abandoned funds. I need your help.",
                "Your inheritance of $3.5 million is ready for transfer."
            ),
            
            "malware" to listOf(
                "Your device has viruses! Download this cleaner app now.",
                "Your {COMPANY} software is outdated. Update now by clicking this link.",
                "Important system update required. Run this file immediately.",
                "Security vulnerability detected. Install patch now."
            ),
            
            "safe" to listOf(
                "Your order has been shipped. It will arrive on Tuesday.",
                "Reminder: Your appointment is tomorrow at 3:30 PM.",
                "Thank you for your payment. Your receipt is attached.",
                "Meeting scheduled for Friday at 2 PM in Conference Room B."
            )
        )
    }
}