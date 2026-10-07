package io.github.mojri.hesabyar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.domain.utils.PersonBalanceCalculator
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.PersonViewModel
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.HesabyarChip
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

/**
 * Person ledger screen (plans/011 Phase 3).
 *
 * One row per person showing net position (receivables − debts). Direction is
 * conveyed by an icon + text label + tint (never color-only): DEBTOR tint is
 * `primary` with ArrowCircleDown; CREDITOR tint is `secondary` with
 * ArrowCircleUp; balanced rows use `onSurfaceVariant`. Each row exposes the
 * direction semantics for screen readers and color-blind users.
 *
 * [onPersonClick] opens the detail sheet/screen. Pass a filtered direction
 * from the dashboard DebtorCreditorCards via [initialDirectionFilter].
 */
@Composable
fun PersonsScreen(
  personViewModel: PersonViewModel,
  modifier: Modifier = Modifier,
  initialDirectionFilter: LoanDirectionFilter = LoanDirectionFilter.ALL,
  onDirectionFilterChange: (LoanDirectionFilter) -> Unit = {},
  onPersonClick: (personId: Long, personName: String) -> Unit = { _, _ -> },
) {
  // Single source of truth for the search text: the ViewModel StateFlow.
  val query by personViewModel.searchQuery.collectAsState()
  // Keep local state for the active direction filter. External updates (e.g. from
  // dashboard cards) synchronize via LaunchedEffect, preventing re-keying from
  // resetting local state when chip taps propagate upward to MainActivity.
  var directionFilter by remember { mutableStateOf(initialDirectionFilter) }
  LaunchedEffect(initialDirectionFilter) {
    directionFilter = initialDirectionFilter
  }
  PersonListContent(
    personViewModel = personViewModel,
    searchQuery = query,
    directionFilter = directionFilter,
    onSearchChange = { personViewModel.setSearchQuery(it) },
    onDirectionFilterChange = {
      directionFilter = it
      onDirectionFilterChange(it)
    },
    onPersonClick = onPersonClick,
    modifier = modifier.fillMaxSize(),
  )
}

enum class LoanDirectionFilter {
  ALL,
  DEBTOR,
  CREDITOR,
  SETTLED,
}

@Composable
private fun PersonListContent(
  personViewModel: PersonViewModel,
  searchQuery: String,
  directionFilter: LoanDirectionFilter,
  onSearchChange: (String) -> Unit,
  onDirectionFilterChange: (LoanDirectionFilter) -> Unit,
  onPersonClick: (Long, String) -> Unit,
  modifier: Modifier,
) {
  val balances by personViewModel.personBalances.collectAsState()
  val filtered = balances.filter { matchesDirection(it, directionFilter) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .imePadding()
        .navigationBarsPadding()
  ) {
    PersonSearchBar(
      searchQuery = searchQuery,
      onSearchChange = onSearchChange,
      modifier = Modifier.padding(horizontal = SpacingTokens.md, vertical = SpacingTokens.sm)
    )

    PersonFilterChips(
      selected = directionFilter,
      onSelect = onDirectionFilterChange,
      modifier = Modifier.padding(horizontal = SpacingTokens.md)
    )

    if (filtered.isEmpty()) {
      PersonEmptyState(searchQuery = searchQuery, directionFilter = directionFilter)
    } else {
      LazyColumn(
        // weight(1f): consume only the height left after the search bar and
        // filter chips instead of requesting the full parent height.
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding =
          PaddingValues(
            horizontal = SpacingTokens.md,
            vertical = SpacingTokens.sm
          ),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
      ) {
        items(filtered, key = { it.personId }) { balance ->
          PersonRow(
            balance = balance,
            onClick = { onPersonClick(balance.personId, balance.personName) },
          )
        }
      }
    }
  }
}

private val FILTER_LABELS: List<Pair<LoanDirectionFilter, String>> =
  listOf(
    LoanDirectionFilter.ALL to "همه",
    LoanDirectionFilter.DEBTOR to "بدهکاران",
    LoanDirectionFilter.CREDITOR to "طلبکاران",
    LoanDirectionFilter.SETTLED to "تسویه‌شده",
  )

@Composable
private fun PersonFilterChips(
  selected: LoanDirectionFilter,
  onSelect: (LoanDirectionFilter) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .testTag("persons_filters"),
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
  ) {
    FILTER_LABELS.forEach { (filter, label) ->
      HesabyarChip(
        selected = filter == selected,
        onClick = { onSelect(filter) },
        label = label
      )
    }
  }
}

private fun matchesDirection(
  balance: PersonBalanceCalculator.PersonBalance,
  filter: LoanDirectionFilter,
): Boolean =
  when (filter) {
    LoanDirectionFilter.ALL -> {
      true
    }

    LoanDirectionFilter.DEBTOR -> {
      balance.netBalance > 0L
    }

    LoanDirectionFilter.CREDITOR -> {
      balance.netBalance < 0L
    }

    LoanDirectionFilter.SETTLED -> {
      balance.activeLoanCount == 0 && balance.settledLoanCount > 0
    }
  }

@Composable
private fun PersonSearchBar(
  searchQuery: String,
  onSearchChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth().testTag("persons_search")) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        modifier = Modifier.weight(1f),
        label = { Text("جستجو بر اساس نام شخص") },
        placeholder = { Text("نام شخص") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
          ),
      )
      if (searchQuery.isNotEmpty()) {
        IconButton(onClick = { onSearchChange("") }) {
          Icon(
            imageVector = Icons.Filled.Clear,
            contentDescription = "پاک‌سازی جستجو",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun ColumnScope.PersonEmptyState(
  searchQuery: String,
  directionFilter: LoanDirectionFilter,
) {
  val hasQuery = searchQuery.isNotEmpty()
  val hasActiveFilter = directionFilter != LoanDirectionFilter.ALL
  val text =
    when {
      hasQuery -> "هیچ شخصی یافت نشد."
      hasActiveFilter -> "هیچ شخصی با این فیلتر یافت نشد."
      else -> "هیچ شخصی ثبت نشده است."
    }
  Box(
    modifier =
      Modifier
        .weight(1f)
        .fillMaxWidth(),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
internal fun PersonRow(
  balance: PersonBalanceCalculator.PersonBalance,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val direction = personDirection(balance)

  HesabyarCard(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = SpacingTokens.md)
        .clickable(onClick = onClick)
        .clearAndSetSemantics {
          role = Role.Button
          stateDescription = direction.contentDescription
          contentDescription =
            "${balance.personName}: ${direction.label}, موجودی ${CurrencyFormatter.format(balance.netBalance)}"
          onClick(label = "مشاهده جزئیات ${balance.personName}") {
            onClick()
            true
          }
        },
    shape = MaterialTheme.shapes.medium,
  ) {
    PersonRowContent(balance = balance, direction = direction)
  }
}

@Composable
private fun PersonRowContent(
  balance: PersonBalanceCalculator.PersonBalance,
  direction: PersonDirection,
) {
  Row(
    modifier = Modifier.padding(SpacingTokens.md),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      IconCircle(
        icon = direction.icon,
        contentDescription = null,
        tint = direction.tint,
        backgroundColor = direction.tint,
        containerSize = 32.dp
      )
      Column {
        Text(
          text = balance.personName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = direction.label,
          style = MaterialTheme.typography.labelSmall,
          color = direction.tint
        )
      }
    }
    Text(
      text = CurrencyFormatter.format(balance.netBalance),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = direction.tint,
      textAlign = TextAlign.End
    )
  }
}

private data class PersonDirection(
  val icon: ImageVector,
  val tint: Color,
  val label: String,
  val contentDescription: String,
)

@Composable
private fun personDirection(balance: PersonBalanceCalculator.PersonBalance): PersonDirection {
  val net = balance.netBalance
  return when {
    net > 0L -> {
      PersonDirection(
        icon = Icons.Filled.ArrowCircleDown,
        tint = MaterialTheme.colorScheme.primary,
        label = stringResource(R.string.loan_party_debtor),
        contentDescription = "بدهکار — موجودی مثبت"
      )
    }

    net < 0L -> {
      PersonDirection(
        icon = Icons.Filled.ArrowCircleUp,
        tint = MaterialTheme.colorScheme.secondary,
        label = stringResource(R.string.loan_party_creditor),
        contentDescription = "طلبکار — موجودی منفی"
      )
    }

    else -> {
      PersonDirection(
        icon = Icons.Filled.AccountCircle,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        label = "تعادل",
        contentDescription = "متعادل — موجودی صفر"
      )
    }
  }
}
