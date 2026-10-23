package com.local.salarytracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.salarytracker.data.TxnEntity
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.ui.AppLabels
import com.local.salarytracker.ui.Fmt
import com.local.salarytracker.ui.theme.AppColors
import com.local.salarytracker.ui.theme.MoneyStyle
import java.time.ZoneId

/**
 * One transaction: category icon, shop (1 line) over "8:21 PM · SMS", amount on the right.
 * Money received shows "+" in green; not-spending entries are struck through with a "Not spending" note.
 */
@Composable
fun EntryRow(t: TxnEntity, zone: ZoneId, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val credit = t.type == TxnType.CREDIT
    val colors = AppColors.current
    val title = t.merchant?.takeIf { it.isNotBlank() }
        ?: t.note?.takeIf { it.isNotBlank() }
        ?: if (credit) "Money received" else t.category.displayName
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val source = when (t.source) {
        TxnSource.SMS -> "SMS"
        TxnSource.NOTIFICATION -> AppLabels.label(ctx, t.sender)?.let { "App alert · $it" } ?: "App alert"
        TxnSource.MANUAL -> "Cash"
    }
    val sub = Fmt.time(t.timestamp, zone) + " · " + source + if (t.excluded) " · Not spending" else ""
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(t.category, 40.dp, credit = credit)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        MoneyText(
            t.amountPaise,
            style = MoneyStyle.Row,
            whole = false,
            plus = credit,
            strikethrough = t.excluded,
            textAlign = TextAlign.End,
            color = when {
                credit -> colors.credit
                t.excluded -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
