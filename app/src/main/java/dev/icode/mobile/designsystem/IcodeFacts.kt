package dev.icode.mobile.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/** One fact of an [IcodeFactGrid]: what it is, and what it says. */
data class IcodeFact(val label: String, val value: String)

/**
 * Facts two to a row, each its label over its value (docs/screens-polish-plan.md D1): a build's, a
 * runtime's. A label never meets its value, however long the value; a value takes two lines at most.
 * Put it in a card, or under a card's own heading.
 */
@Composable
fun IcodeFactGrid(facts: List<IcodeFact>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
        facts.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
                pair.forEach { fact ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro)) {
                        Text(fact.label, style = IcodeType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fact.value, style = IcodeType.Body, color = IcodeColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
