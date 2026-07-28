package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
fun AccountSelector(
  accounts: List<AccountEntity>,
  selectedAccountId: Long?,
  onAccountSelected: (Long?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    HesabyarChip(
      selected = selectedAccountId == null,
      onClick = { onAccountSelected(null) },
      label = "همه حساب‌ها",
      shape = ShapeTokens.Small,
    )

    accounts.filter { !it.isArchived }.forEach { account ->
      HesabyarChip(
        selected = selectedAccountId == account.id,
        onClick = { onAccountSelected(account.id) },
        label = account.name,
        leadingIcon = {
          Box(
            modifier =
              Modifier
                .size(Dimens.IconSmall)
                .background(color = Color(account.color.toULong()), shape = CircleShape),
          )
        },
        shape = ShapeTokens.Small,
      )
    }
  }
}
