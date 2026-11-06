package io.github.vivekg7.galla.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import io.github.vivekg7.galla.app
import io.github.vivekg7.galla.data.Db
import io.github.vivekg7.galla.data.ShopInfo
import io.github.vivekg7.galla.data.Sync

enum class Tab(
    val label: String,
    val icon: ImageVector,
) {
    Bill("Bill", Icons.Cart),
    Inventory("Inventory", Icons.Inventory),
    Bills("Sales", Icons.Receipt),
    Settings("Shop", Icons.Settings),
}

sealed interface Route {
    data class Home(
        val tab: Tab,
    ) : Route

    /** [id] null makes a new product; [barcode] fills it in; [addToCart] puts it on the bill being made once saved. */
    data class Product(
        val id: Long?,
        val barcode: String? = null,
        val addToCart: Boolean = false,
    ) : Route

    data class Slip(
        val billId: Long,
    ) : Route
}

/** A back stack in a list: four tabs and two detail screens need nothing more. */
class Nav(
    start: Tab,
) {
    val stack = mutableStateListOf<Route>(Route.Home(start))
    val top get() = stack.last()

    fun go(route: Route) = stack.add(route)

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else stack[0] = Route.Home(Tab.Bill)
    }

    fun tab(tab: Tab) {
        stack.clear()
        stack.add(Route.Home(tab))
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("no Nav") }

@Composable
fun Shell() {
    val app = LocalContext.current.app
    // Nothing until the settings are read: they decide what shows, and a
    // Bill screen drawn for one frame would ask for the camera.
    val settings = app.settings.collectAsState().value ?: return
    val joining =
        app.welcome.busy
            .collectAsState()
            .value != null

    // First run: sign in or not, then the shop's name, which the slip opens with.
    // A phone joining a shop gets the name from Drive; the welcome screen stays
    // until it has the rest of the shop's data too.
    val shop = ShopInfo.from(settings)
    if (joining || shop.name.isBlank()) {
        val values = settings.associate { it.key to it.value }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
            if (joining || (values[Db.Triggers.DEVICE] == null && values[Welcome.LOCAL] == null)) {
                WelcomeScreen()
            } else {
                SetupScreen(shop, values[Sync.ACCOUNT])
            }
        }
        return
    }

    val nav = remember { Nav(Tab.Bill) }
    val top = nav.top

    BackHandler(enabled = nav.stack.size > 1 || top != Route.Home(Tab.Bill)) { nav.back() }

    Scaffold(
        bottomBar = {
            if (top is Route.Home) {
                NavigationBar {
                    for (tab in Tab.entries) {
                        NavigationBarItem(
                            selected = top.tab == tab,
                            onClick = { nav.tab(tab) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            CompositionLocalProvider(LocalNav provides nav) {
                when (top) {
                    is Route.Home -> {
                        when (top.tab) {
                            Tab.Bill -> BillScreen()
                            Tab.Inventory -> InventoryScreen()
                            Tab.Bills -> SalesScreen()
                            Tab.Settings -> SettingsScreen()
                        }
                    }

                    is Route.Product -> {
                        ProductScreen(top)
                    }

                    is Route.Slip -> {
                        SlipScreen(top.billId)
                    }
                }
            }
        }
    }
}
