package com.onetap.security.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Utility class to handle user preferences for security features
 */
class SecurityPreferences(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "security_preferences"
        private const val KEY_SECURITY_ENABLED = "security_enabled"
        
        // Singleton instance
        @Volatile
        private var INSTANCE: SecurityPreferences? = null
        
        fun getInstance(context: Context): SecurityPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurityPreferences(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Check if security monitoring is enabled
     * @return true if security monitoring is enabled, false otherwise
     */
    fun isSecurityEnabled(): Boolean {
        return prefs.getBoolean(KEY_SECURITY_ENABLED, true) // Enabled by default
    }
    
    /**
     * Enable or disable security monitoring
     * @param enabled true to enable security monitoring, false to disable
     */
    fun setSecurityEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SECURITY_ENABLED, enabled).apply()
    }
}