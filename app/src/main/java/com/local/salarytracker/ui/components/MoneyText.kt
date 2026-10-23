package com.local.salarytracker.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.local.salarytracker.ui.theme.MoneyStyle
import com.local.salarytracker.util.Money

/**
 * Rupee amount in Indian format with tabular figures. [whole] = true rounds to whole rupees (big numbers),
 * false keeps paise when not zero (entries). [plus] adds "+" in front (money received).
 */
@Composable
fun MoneyText(
    paise: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MoneyStyle.Card,
    color: Color = Color.Unspecified,
    whole: Boolean = true,
    plus: Boolean = false,
    strikethrough: Boolean = false,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
) {
    val text = (if (plus) "+" else "") + if (whole) Money.formatWhole(paise) else Money.format(paise)
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (strikethrough) TextDecoration.LineThrough else null,
    )
}

/** Style helper for callers that need the current text style with tabular figures. */
@Composable
fun tabularStyle(): TextStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
