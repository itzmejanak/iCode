package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.icode.mobile.designsystem.IcodeExpandableCard
import dev.icode.mobile.designsystem.IcodeKeyGrid
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.KeyRows
import dev.icode.mobile.ui.LeftToRight

/**
 * A keys row's settings (docs/editor-and-language-services.md), the editor's or the terminal's. Closed, the card
 * shows how many keys the row has and the row itself, in its order. Open, it holds the row's keys,
 * every one the same size: hold one and drag it to move it, tap one to take it out. Below them are the
 * keys left out, each added at the row's end with a tap, and Reset to Default: all of [all], in their
 * order. [chip] draws a key with the modifier, look and tap it is given.
 */
@Composable
internal fun <K : Any> KeyRowSettings(
    all: List<K>,
    keys: List<K>,
    onKeys: (List<K>) -> Unit,
    chip: @Composable (key: K, modifier: Modifier, active: Boolean, onClick: (() -> Unit)?) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    IcodeExpandableCard(
        title = "${keys.size} of ${all.size} keys",
        open = open,
        onOpen = { open = it },
        closed = {
            if (keys.isEmpty()) {
                Text(NO_KEYS, style = IcodeType.Caption)
            } else {
                // The row as it stands, said by the title: a glance, not keys to press.
                LeftToRight {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).clearAndSetSemantics {},
                        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
                    ) {
                        keys.forEach { chip(it, Modifier.size(IcodeSizes.TouchTarget), false, null) }
                    }
                }
            }
        },
    ) {
        Text("Hold a key and drag it to move it. Tap a key to take it out.", style = IcodeType.Caption)
        if (keys.isEmpty()) {
            Text(NO_KEYS, style = IcodeType.Caption)
        } else {
            LeftToRight {
                IcodeKeyGrid(keys, onOrder = onKeys) { key, modifier, held ->
                    chip(key, modifier, held) { if (!held) onKeys(KeyRows.toggled(keys, key)) }
                }
            }
        }
        val others = KeyRows.others(all, keys)
        SectionLabel("More keys", Modifier.padding(top = IcodeSpacing.Space2))
        if (others.isEmpty()) {
            Text("Every key is in the row.", style = IcodeType.Caption)
        } else {
            Text("Tap a key to add it at the end of the row.", style = IcodeType.Caption)
            LeftToRight {
                IcodeKeyGrid(others, onOrder = null) { key, modifier, _ ->
                    chip(key, modifier, false) { onKeys(KeyRows.toggled(keys, key)) }
                }
            }
        }
        IcodeTextButton("Reset to Default", { onKeys(all) }, Modifier.align(Alignment.End), enabled = keys != all)
    }
}

private const val NO_KEYS = "No keys: the row is not shown."
