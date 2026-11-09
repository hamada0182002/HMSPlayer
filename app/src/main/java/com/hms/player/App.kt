package com.hms.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun HmsApp(vm: AppVM) {
    HmsTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = Bg, contentColor = TextMain) {
                var splash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) { delay(1500); splash = false }

                BackHandler(enabled = vm.player == null && vm.stack.size > 1) { vm.pop() }

                Box(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = vm.stack.last(),
                        transitionSpec = {
                            if (vm.forward)
                                (slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(320))) togetherWith
                                    (slideOutHorizontally(tween(320)) { it / 4 } + fadeOut(tween(200)))
                            else
                                (slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(320))) togetherWith
                                    (slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(200)))
                        },
                        label = "nav"
                    ) { s ->
                        when (s) {
                            is Screen.Accounts -> AccountsScreen(vm)
                            is Screen.Home -> HomeScreen(vm)
                            is Screen.Continue -> ContinueScreen(vm)
                            is Screen.Settings -> SettingsScreen(vm)
                            is Screen.Browse -> BrowseScreen(vm, s.section)
                            is Screen.Movie -> MovieScreen(vm, s.item)
                            is Screen.Series -> SeriesScreen(vm, s.item)
                        }
                    }

                    vm.player?.let { req -> key(req) { PlayerScreen(vm, req) } }

                    val up = vm.pendingUpdate
                    if (up != null && !splash && vm.player == null) UpdateDialog(up) { vm.dismissUpdate() }

                    AnimatedVisibility(splash, enter = fadeIn(), exit = fadeOut(tween(450))) { Splash() }
                }
            }
        }
    }
}

@Composable
private fun Splash() {
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) { k.animateTo(1f, tween(700)) }
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(
            Modifier.graphicsLayer { alpha = k.value; scaleX = 0.7f + 0.3f * k.value; scaleY = 0.7f + 0.3f * k.value },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painterResource(R.drawable.logo_mark), null, Modifier.size(120.dp))
            Spacer(Modifier.height(12.dp))
            Text("HMS Player", style = TextStyle(brush = BrandBrush, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold))
        }
    }
}
