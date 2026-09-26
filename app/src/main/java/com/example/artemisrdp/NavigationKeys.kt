package com.example.artemisrdp

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeNavKey : NavKey

@Serializable
data class EditNavKey(val connectionId: String = "new") : NavKey

@Serializable
data class SessionNavKey(val connectionId: String) : NavKey
