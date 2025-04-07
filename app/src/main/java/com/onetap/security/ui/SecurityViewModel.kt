package com.onetap.security.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.onetap.security.utils.SecurityPreferences

/**
 * ViewModel for the Security UI
 */
class SecurityViewModel(application: Application) : AndroidViewModel(application) {
    
    private val securityPreferences = SecurityPreferences.getInstance(application.applicationContext)
    
    private val _securityEnabled = mutableStateOf(securityPreferences.isSecurityEnabled())
    val securityEnabled: State<Boolean> = _securityEnabled
    
    /**
     * Update the security enabled state
     */
    fun setSecurityEnabled(enabled: Boolean) {
        securityPreferences.setSecurityEnabled(enabled)
        _securityEnabled.value = enabled
    }
}
