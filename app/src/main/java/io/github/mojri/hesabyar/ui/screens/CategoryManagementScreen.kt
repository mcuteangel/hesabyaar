package io.github.mojri.hesabyar.ui.screens
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.ui.CategoryViewModel
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.ConfirmDialog
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.HesabyarDialog
import io.github.mojri.hesabyar.ui.components.HesabyarFab
import io.github.mojri.hesabyar.ui.components.HesabyarInputField
import io.github.mojri.hesabyar.ui.components.HesabyarScaffold
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.components.SectionHeader
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

private val CATEGORY_ICONS =
  mapOf(
    "Restaurant" to Icons.Filled.Restaurant,
    "DirectionsCar" to Icons.Filled.DirectionsCar,
    "ShoppingBag" to Icons.Filled.ShoppingBag,
    "ReceiptLong" to Icons.Filled.ReceiptLong,
    "CreditCard" to Icons.Filled.CreditCard,
    "HistoryEdu" to Icons.Filled.HistoryEdu,
    "Paid" to Icons.Filled.Paid,
    "AttachMoney" to Icons.Filled.AttachMoney,
    "Home" to Icons.Filled.Home,
    "HealthAndSafety" to Icons.Filled.HealthAndSafety,
    "School" to Icons.Filled.School,
    "Flight" to Icons.Filled.Flight,
    "LocalCafe" to Icons.Filled.LocalCafe,
    "Pets" to Icons.Filled.Pets,
    "CardGiftcard" to Icons.Filled.CardGiftcard,
    "Work" to Icons.Filled.Work,
    "SportsEsports" to Icons.Filled.SportsEsports,
    "Checkroom" to Icons.Filled.Checkroom,
    "LocalGroceryStore" to Icons.Filled.LocalGroceryStore,
    "Savings" to Icons.Filled.Savings,
    "AccountBalance" to Icons.Filled.AccountBalance,
    "TrendingUp" to Icons.Filled.TrendingUp,
    "TrendingDown" to Icons.Filled.TrendingDown,
    "Build" to Icons.Filled.Build,
    "Phone" to Icons.Filled.Phone,
    "Wifi" to Icons.Filled.Wifi,
    "LocalHospital" to Icons.Filled.LocalHospital,
    "ChildCare" to Icons.Filled.ChildCare,
    "LocalDining" to Icons.Filled.LocalDining,
    "CleaningServices" to Icons.Filled.CleaningServices
  )

private val CATEGORY_COLORS =
  listOf(
    0xFF4CAF50L,
    0xFFFF9800L,
    0xFF2196F3L,
    0xFF009688L,
    0xFFF44336L,
    0xFF9C27B0L,
    0xFF757575L,
    0xFFE91E63L,
    0xFF3F51B5L,
    0xFF00BCD4L,
    0xFF8BC34AL,
    0xFFFF5722L,
    0xFF607D8BL,
    0xFF795548L,
    0xFFCDDC39L,
    0xFF03A9F4L
  )

private val CategoryType.displayName: String
  @Composable
  get() =
    when (this) {
      CategoryType.EXPENSE -> stringResource(R.string.category_type_expense)
      CategoryType.INCOME -> stringResource(R.string.category_type_income)
      CategoryType.BOTH -> stringResource(R.string.category_type_both)
      CategoryType.UNKNOWN -> stringResource(R.string.category_type_unknown)
    }

@Composable
fun CategoryManagementScreen(
  categoryViewModel: CategoryViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val categories by categoryViewModel.categories.collectAsState()
  var showAddDialog by remember { mutableStateOf(false) }
  var editingCategory by remember { mutableStateOf<Category?>(null) }
  var showDeleteConfirmation by remember { mutableStateOf<Category?>(null) }

  HesabyarScaffold(
    modifier = modifier,
    title = "مدیریت دسته‌بندی‌ها",
    onBack = onBack,
    floatingActionButton = {
      HesabyarFab(
        onClick = { showAddDialog = true },
        contentDescription = "افزودن دسته‌بندی"
      )
    }
  ) { contentPadding ->
    CategoryManagementContent(
      categories = categories,
      contentPadding = contentPadding,
      onEdit = { editingCategory = it },
      onDelete = { showDeleteConfirmation = it }
    )
  }

  CategoryManagementDialogs(
    showAddDialog = showAddDialog,
    editingCategory = editingCategory,
    showDeleteConfirmation = showDeleteConfirmation,
    categoryViewModel = categoryViewModel,
    onDismissAdd = { showAddDialog = false },
    onDismissEdit = { editingCategory = null },
    onDismissDelete = { showDeleteConfirmation = null }
  )
}

@Composable
private fun CategoryManagementContent(
  categories: List<Category>,
  contentPadding: PaddingValues,
  onEdit: (Category) -> Unit,
  onDelete: (Category) -> Unit,
) {
  if (categories.isEmpty()) {
    Box(
      modifier = Modifier.fillMaxSize().padding(contentPadding),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "هنوز دسته‌بندی‌ای ثبت نشده است.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  } else {
    val defaultCategories = categories.filter { it.isDefault }
    val customCategories = categories.filter { !it.isDefault }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = contentPadding,
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      if (defaultCategories.isNotEmpty()) {
        item {
          SectionHeader(title = "دسته‌بندی‌های پیش‌فرض")
        }
        items(defaultCategories, key = { it.id }) { category ->
          CategoryItem(
            category = category,
            isDefault = true,
            onEdit = onEdit,
            onDelete = onDelete
          )
        }
      }

      if (customCategories.isNotEmpty()) {
        item {
          SectionHeader(title = "دسته‌بندی‌های اختصاصی")
        }
        items(customCategories, key = { it.id }) { category ->
          CategoryItem(
            category = category,
            isDefault = false,
            onEdit = onEdit,
            onDelete = onDelete
          )
        }
      }

      item {
        Spacer(modifier = Modifier.height(SpacingTokens.lg))
      }
    }
  }
}

@Composable
private fun CategoryManagementDialogs(
  showAddDialog: Boolean,
  editingCategory: Category?,
  showDeleteConfirmation: Category?,
  categoryViewModel: CategoryViewModel,
  onDismissAdd: () -> Unit,
  onDismissEdit: () -> Unit,
  onDismissDelete: () -> Unit,
) {
  if (showAddDialog) {
    CategoryDialog(
      initialCategory = null,
      onDismiss = onDismissAdd,
      onSave = { name, key, icon, color, type ->
        categoryViewModel.addCategory(name, key, icon, color, type)
        onDismissAdd()
      }
    )
  }

  if (editingCategory != null) {
    CategoryDialog(
      initialCategory = editingCategory,
      onDismiss = onDismissEdit,
      onSave = { name, key, icon, color, type ->
        categoryViewModel.updateCategory(
          editingCategory.copy(
            name = name,
            key = key,
            icon = icon,
            color = color,
            type = type
          )
        )
        onDismissEdit()
      }
    )
  }

  if (showDeleteConfirmation != null) {
    val categoryToDelete = showDeleteConfirmation
    ConfirmDialog(
      title = "حذف دسته‌بندی",
      message = "آیا از حذف دسته‌بندی «${categoryToDelete.name}» اطمینان دارید؟",
      confirmText = stringResource(R.string.action_delete),
      dismissText = stringResource(R.string.action_cancel),
      onDismiss = onDismissDelete,
      onConfirm = {
        categoryViewModel.deleteCategory(categoryToDelete)
        onDismissDelete()
      }
    )
  }
}

@Composable
private fun CategoryItem(
  category: Category,
  isDefault: Boolean,
  onEdit: (Category) -> Unit,
  onDelete: (Category) -> Unit
) {
  val icon = CATEGORY_ICONS[category.icon] ?: Icons.Filled.Paid
  val categoryColor = Color(category.color)

  HesabyarCard(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = SpacingTokens.lg),
    shape = ShapeTokens.Medium,
    contentPadding = PaddingValues(SpacingTokens.md)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      IconCircle(
        icon = icon,
        tint = categoryColor,
        backgroundColor = categoryColor,
        containerSize = Dimens.AvatarMedium,
        iconSize = Dimens.IconAvatarMedium
      )

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = category.name,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = category.key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = category.type.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = categoryColor,
            fontWeight = FontWeight.Medium
          )
        }
      }

      IconButton(
        onClick = { onEdit(category) },
        // Enforce the 48dp minimum touch area; the minimum must come before
        // the fixed size, otherwise the fixed size wins and the target stays small.
        modifier = Modifier.minimumInteractiveComponentSize().size(Dimens.ContainerSmall)
      ) {
        Icon(
          imageVector = Icons.Filled.Edit,
          contentDescription = "ویرایش",
          modifier = Modifier.size(Dimens.IconMenu),
          tint = MaterialTheme.colorScheme.primary
        )
      }

      if (!isDefault) {
        IconButton(
          onClick = { onDelete(category) },
          // Enforce the 48dp minimum touch area; the minimum must come before
          // the fixed size, otherwise the fixed size wins and the target stays small.
          modifier = Modifier.minimumInteractiveComponentSize().size(Dimens.ContainerSmall)
        ) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "حذف",
            modifier = Modifier.size(Dimens.IconMenu),
            tint = MaterialTheme.colorScheme.error
          )
        }
      }
    }
  }
}

private class CategoryFormState(
  val isEditing: Boolean,
  initialName: String = "",
  initialKey: String = "",
  initialIcon: String = "Paid",
  initialColor: Long = 0xFF4CAF50L,
  initialType: CategoryType = CategoryType.EXPENSE,
  val isKeyEnabled: Boolean = true
) {
  var name by mutableStateOf(initialName)
  var key by mutableStateOf(initialKey)
  var selectedIcon by mutableStateOf(initialIcon)
  var selectedColor by mutableStateOf(initialColor)
  var selectedType by mutableStateOf(initialType)
  var typeDropdownExpanded by mutableStateOf(false)

  val isValid: Boolean
    get() = name.isNotBlank()

  val finalKey: String
    get() =
      key.trim().ifBlank {
        name.trim().replace("\\s+".toRegex(), "")
      }
}

@Composable
private fun CategoryDialog(
  initialCategory: Category?,
  onDismiss: () -> Unit,
  onSave: (name: String, key: String, icon: String, color: Long, type: CategoryType) -> Unit
) {
  val isEditing = initialCategory != null
  val isKeyEnabled = !isEditing || initialCategory?.isDefault != true
  val state =
    remember(initialCategory) {
      CategoryFormState(
        isEditing = isEditing,
        initialName = initialCategory?.name.orEmpty(),
        initialKey = initialCategory?.key.orEmpty(),
        initialIcon = initialCategory?.icon ?: "Paid",
        initialColor = initialCategory?.color ?: 0xFF4CAF50L,
        initialType = initialCategory?.type ?: CategoryType.EXPENSE,
        isKeyEnabled = isKeyEnabled
      )
    }

  HesabyarDialog(
    onDismissRequest = onDismiss,
    title = if (isEditing) "ویرایش دسته‌بندی" else "افزودن دسته‌بندی جدید",
    confirmButton = {
      HesabyarButton(
        onClick = {
          onSave(
            state.name.trim(),
            state.finalKey,
            state.selectedIcon,
            state.selectedColor,
            state.selectedType
          )
        },
        text = stringResource(R.string.action_save),
        variant = ButtonVariant.Filled,
        enabled = state.isValid
      )
    },
    dismissButton = {
      HesabyarButton(
        onClick = onDismiss,
        text = stringResource(R.string.action_cancel),
        variant = ButtonVariant.Text
      )
    }
  ) {
    CategoryDialogContent(state = state)
  }
}

@Composable
private fun CategoryDialogContent(
  state: CategoryFormState,
  modifier: Modifier = Modifier
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(max = 400.dp)
        .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
  ) {
    HesabyarInputField(
      value = state.name,
      onValueChange = { state.name = it },
      label = "نام فارسی",
      placeholder = "مثلاً: حمل و نقل",
      shape = ShapeTokens.Medium
    )

    HesabyarInputField(
      value = state.key,
      onValueChange = { state.key = it },
      label = "کلید انگلیسی (اختیاری)",
      placeholder = "مثلاً: Transportation",
      shape = ShapeTokens.Medium,
      enabled = state.isKeyEnabled
    )

    CategoryTypeDropdown(
      selectedType = state.selectedType,
      onTypeSelected = { state.selectedType = it },
      expanded = state.typeDropdownExpanded,
      onExpandedChange = { state.typeDropdownExpanded = it }
    )

    CategoryColorPicker(
      selectedColor = state.selectedColor,
      onColorSelected = { state.selectedColor = it }
    )

    CategoryIconPicker(
      selectedIcon = state.selectedIcon,
      onIconSelected = { state.selectedIcon = it }
    )

    CategoryPreviewCard(
      name = state.name,
      selectedType = state.selectedType,
      selectedIcon = state.selectedIcon,
      selectedColor = state.selectedColor
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryTypeDropdown(
  selectedType: CategoryType,
  onTypeSelected: (CategoryType) -> Unit,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
  ) {
    Text(
      text = "نوع:",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = onExpandedChange
    ) {
      HesabyarInputField(
        value = selectedType.displayName,
        onValueChange = {},
        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        readOnly = true,
        shape = ShapeTokens.Medium,
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
      )
      ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = { onExpandedChange(false) }
      ) {
        DropdownMenuItem(
          text = { Text("هزینه") },
          onClick = {
            onTypeSelected(CategoryType.EXPENSE)
            onExpandedChange(false)
          }
        )
        DropdownMenuItem(
          text = { Text("درآمد") },
          onClick = {
            onTypeSelected(CategoryType.INCOME)
            onExpandedChange(false)
          }
        )
        DropdownMenuItem(
          text = { Text("هر دو") },
          onClick = {
            onTypeSelected(CategoryType.BOTH)
            onExpandedChange(false)
          }
        )
      }
    }
  }
}

@Composable
private fun CategoryColorPicker(
  selectedColor: Long,
  onColorSelected: (Long) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
  ) {
    Text(
      text = "رنگ:",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LazyVerticalGrid(
      columns = GridCells.Fixed(8),
      modifier = Modifier.height(60.dp),
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs),
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
    ) {
      items(CATEGORY_COLORS) { color ->
        Box(
          modifier =
            Modifier
              .size(Dimens.ContainerXSmall)
              .clip(CircleShape)
              .background(Color(color))
              .clickable { onColorSelected(color) },
          contentAlignment = Alignment.Center
        ) {
          if (selectedColor == color) {
            Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(Dimens.IconSmall)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CategoryIconPicker(
  selectedIcon: String,
  onIconSelected: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
  ) {
    Text(
      text = "آیکون:",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LazyVerticalGrid(
      columns = GridCells.Fixed(7),
      modifier = Modifier.height(180.dp),
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs),
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
    ) {
      items(CATEGORY_ICONS.entries.toList()) { (iconName, iconVector) ->
        val isSelected = selectedIcon == iconName
        Box(
          modifier =
            Modifier
              .size(Dimens.ContainerSmall)
              .clip(ShapeTokens.Small)
              .background(
                if (isSelected) {
                  MaterialTheme.colorScheme.primary.copy(alpha = Dimens.SURFACE_TINT_ALPHA)
                } else {
                  MaterialTheme.colorScheme.surfaceContainerLow
                }
              ).clickable { onIconSelected(iconName) },
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = iconVector,
            contentDescription = iconName,
            modifier = Modifier.size(Dimens.IconAvatarMedium),
            tint =
              if (isSelected) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              }
          )
        }
      }
    }
  }
}

@Composable
private fun CategoryPreviewCard(
  name: String,
  selectedType: CategoryType,
  selectedIcon: String,
  selectedColor: Long,
  modifier: Modifier = Modifier
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(ShapeTokens.Medium)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .padding(SpacingTokens.md),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
  ) {
    val previewIcon = CATEGORY_ICONS[selectedIcon] ?: Icons.Filled.Paid
    val previewColor = Color(selectedColor)
    IconCircle(
      icon = previewIcon,
      tint = previewColor,
      backgroundColor = previewColor,
      containerSize = Dimens.AvatarMedium,
      iconSize = Dimens.IconAvatarMedium
    )
    Column {
      Text(
        text = name.ifBlank { "نام دسته‌بندی" },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = selectedType.displayName,
        style = MaterialTheme.typography.labelSmall,
        color = previewColor
      )
    }
  }
}
