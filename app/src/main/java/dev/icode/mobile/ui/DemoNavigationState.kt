package dev.icode.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/**
 * Demo back-stack and screen-catalog state for [IcodeDemoApp].
 *
 * Keeps navigation concerns out of the composition root and survives
 * configuration changes through [Saver].
 */
@Stable
class DemoNavigationState private constructor(
    initialHistory: List<String>,
    initialCatalogOpen: Boolean,
    private val staticDemo: Boolean,
) {
    var historyNames by mutableStateOf(initialHistory)
        private set

    var catalogOpen by mutableStateOf(initialCatalogOpen)
        private set

    val current: DemoScreen get() = DemoScreen.valueOf(historyNames.last())

    val canGoBack: Boolean get() = historyNames.size > 1

    val actions: DemoActions = DemoActions(
        navigate = { destination ->
            if (destination != current) historyNames = historyNames + destination.name
            catalogOpen = false
        },
        back = { if (canGoBack) historyNames = historyNames.dropLast(1) },
        staticDemo = staticDemo,
    )

    fun openCatalog() {
        catalogOpen = true
    }

    fun closeCatalog() {
        catalogOpen = false
    }

    companion object {
        fun create(initialScreen: DemoScreen, staticDemo: Boolean): DemoNavigationState =
            DemoNavigationState(
                initialHistory = listOf(initialScreen.name),
                initialCatalogOpen = false,
                staticDemo = staticDemo,
            )

        fun saver(initialScreen: DemoScreen, staticDemo: Boolean): Saver<DemoNavigationState, Any> =
            listSaver(
                save = { state -> listOf(ArrayList(state.historyNames), state.catalogOpen) },
                restore = { saved ->
                    DemoNavigationState(
                        initialHistory = (saved[0] as? ArrayList<*>)?.filterIsInstance<String>().orEmpty().ifEmpty { listOf(initialScreen.name) },
                        initialCatalogOpen = saved[1] as? Boolean ?: false,
                        staticDemo = staticDemo,
                    )
                },
            )
    }
}

@Composable
fun rememberDemoNavigationState(initialScreen: DemoScreen, staticDemo: Boolean): DemoNavigationState =
    rememberSaveable(initialScreen, saver = DemoNavigationState.saver(initialScreen, staticDemo)) {
        DemoNavigationState.create(initialScreen, staticDemo)
    }
