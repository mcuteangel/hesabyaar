package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.theme.WarningOrange

private const val MAX_AMOUNT_TOMAN = 999_999_999_999L

@Composable
fun AmountQuickFillButtons(
    amountValue: TextFieldValue,
    onValueChanged: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentAmount = amountValue.text.toLongOrNull() ?: 0L

    data class QuickFillOption(val label: String, val factor: Long)

    val options = listOf(
        QuickFillOption("هزار", 1_000L),
        QuickFillOption("میلیون", 1_000_000L),
        QuickFillOption("میلیارد", 1_000_000_000L)
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val newValue = (currentAmount * option.factor).coerceAtMost(MAX_AMOUNT_TOMAN)
            val isEnabled = currentAmount > 0 && newValue <= MAX_AMOUNT_TOMAN

            Text(
                text = option.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isEnabled) WarningOrange.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                    .clickable(enabled = isEnabled) {
                        val newText = newValue.toString()
                        onValueChanged(
                            TextFieldValue(
                                text = newText,
                                selection = TextRange(newText.length)
                            )
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (isEnabled) WarningOrange
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}
