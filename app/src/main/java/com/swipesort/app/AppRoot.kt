package com.swipesort.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    val nav = vm.nav
    val actions = rememberMediaActions()
    val snackbar = remember { SnackbarHostState() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionResult()
    }
    LaunchedEffect(Unit) {
        if (!vm.hasPermission) permLauncher.launch(MediaRepo.allPermissions())
    }
    LaunchedEffect(actions.toast) {
        actions.toast?.let { snackbar.showSnackbar(it); actions.toast = null }
    }

    CompositionLocalProvider(LocalNav provides nav, LocalVm provides vm, LocalMediaActions provides actions) {
        if (!vm.hasPermission) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrandTitle()
                Spacer(Modifier.height(20.dp))
                Text("Your gallery, sorted beautifully.\nSwipeSort needs to see your photos and videos.",
                    color = Palette.Ink, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                GradientButton("Allow access", { permLauncher.launch(MediaRepo.allPermissions()) }, Modifier.fillMaxWidth())
            }
            return@CompositionLocalProvider
        }

        val top = nav.stack.lastOrNull()
        val pageRoute = nav.stack.lastOrNull { it !is Route.ViewerRoute }
        val swiping = nav.tab == HomeTab.ORGANIZE && vm.screen == Screen.SWIPE && pageRoute == null

        BackHandler(enabled = nav.stack.isNotEmpty()) { nav.pop() }
        BackHandler(enabled = nav.stack.isEmpty() && nav.tab != HomeTab.PHOTOS && !swiping) { nav.tab = HomeTab.PHOTOS }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().then(if (swiping) Modifier else Modifier.statusBarsPadding())) {
                    when (pageRoute) {
                        is Route.AlbumRoute -> AlbumDetail(pageRoute.bucketId)
                        is Route.CountryRoute -> CountryDetail(pageRoute.country)
                        is Route.SmartRoute -> SmartDetail(pageRoute.kind)
                        else -> when (nav.tab) {
                            HomeTab.PHOTOS -> BrowseScreen(
                                title = "Photos", subtitle = null, items = Library.items,
                                defaultGroup = GroupBy.MONTH, onBack = null, stateKey = "photos"
                            )
                            HomeTab.ALBUMS -> AlbumsTab()
                            HomeTab.PLACES -> PlacesTab()
                            HomeTab.ORGANIZE -> when (vm.screen) {
                                Screen.SETUP -> SetupScreen(vm)
                                Screen.SWIPE -> SwipeScreen(vm)
                            }
                        }
                    }
                }
                if (!swiping) BottomBar(nav)
            }

            if (top is Route.ViewerRoute) {
                ViewerScreen(top.ids, top.start, onClose = { nav.pop() })
            }

            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp)) { data ->
                Snackbar(data, containerColor = Palette.Ink, contentColor = Color.White, shape = RoundedCornerShape(18.dp))
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavState) {
    NavigationBar(containerColor = Color.White.copy(alpha = 0.94f), tonalElevation = 0.dp) {
        HomeTab.entries.forEach { tab ->
            val icon: ImageVector = when (tab) {
                HomeTab.PHOTOS -> Icons.Default.Photo
                HomeTab.ALBUMS -> Icons.Default.Collections
                HomeTab.PLACES -> Icons.Default.Public
                HomeTab.ORGANIZE -> Icons.Default.Swipe
            }
            NavigationBarItem(
                selected = nav.tab == tab && nav.stack.isEmpty(),
                onClick = { nav.stack.clear(); nav.tab = tab },
                icon = { Icon(icon, tab.label) },
                label = { Text(tab.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Palette.PinkDeep,
                    selectedTextColor = Palette.PinkDeep,
                    indicatorColor = Palette.PinkSoft,
                    unselectedIconColor = Palette.InkSoft,
                    unselectedTextColor = Palette.InkSoft,
                )
            )
        }
    }
}
