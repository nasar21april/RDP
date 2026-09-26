package com.example.artemisrdp

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.artemisrdp.data.DefaultConnectionRepository
import com.example.artemisrdp.data.GitHubCloudRdpManager
import com.example.artemisrdp.ui.screens.EditConnectionScreen
import com.example.artemisrdp.ui.screens.HomeScreen
import com.example.artemisrdp.ui.screens.SessionScreen

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val repository = remember { DefaultConnectionRepository(context) }
    val cloudManager = remember { GitHubCloudRdpManager(context) }
    val backStack = rememberNavBackStack(HomeNavKey)

    val safePop: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            (context as? Activity)?.finish()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { safePop() },
        entryProvider = entryProvider {
            entry<HomeNavKey> {
                HomeScreen(
                    repository = repository,
                    cloudManager = cloudManager,
                    onConnect = { id -> backStack.add(SessionNavKey(id)) },
                    onAddConnection = { backStack.add(EditNavKey("new")) },
                    onEditConnection = { id -> backStack.add(EditNavKey(id)) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<EditNavKey> { key ->
                EditConnectionScreen(
                    connectionId = key.connectionId,
                    repository = repository,
                    onSaveSuccess = { safePop() },
                    onBack = { safePop() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<SessionNavKey> { key ->
                SessionScreen(
                    connectionId = key.connectionId,
                    repository = repository,
                    onDisconnectComplete = { safePop() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}
