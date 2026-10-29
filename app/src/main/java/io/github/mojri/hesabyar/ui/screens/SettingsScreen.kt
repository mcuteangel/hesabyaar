package io.github.mojri.hesabyar.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.github.mojri.hesabyar.BuildConfig
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import io.github.mojri.hesabyar.auth.BiometricHelper
import io.github.mojri.hesabyar.auth.PinStorage
import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.RestoreMode
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.BackupOperationState
import io.github.mojri.hesabyar.ui.BackupViewModel
import io.github.mojri.hesabyar.ui.CurrencyUnit
import io.github.mojri.hesabyar.ui.ExportState
import io.github.mojri.hesabyar.ui.ExportViewModel
import io.github.mojri.hesabyar.ui.PassphraseDialogState
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.UiResult
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.ConfirmDialog
import io.github.mojri.hesabyar.ui.components.ExportPassphraseDialog
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.HesabyarDialog
import io.github.mojri.hesabyar.ui.components.HesabyarInputField
import io.github.mojri.hesabyar.ui.components.ImportPassphraseDialog
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

fun Context.findActivity(): FragmentActivity? {
  var currentContext = this
  while (currentContext is ContextWrapper) {
    if (currentContext is FragmentActivity) {
      return currentContext
    }
    currentContext = currentContext.baseContext
  }
  return null
}

@Composable
fun SettingsScreen(
  aiAssistantViewModel: AiAssistantViewModel,
  backupViewModel: BackupViewModel,
  exportViewModel: ExportViewModel,
  settingsViewModel: SettingsViewModel,
  onNavigateToCategories: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  val exportFileLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
      if (uri != null) {
        try {
          val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
          if (outputStream != null) {
            backupViewModel.exportCoordinator.writeStagedExportToFile(outputStream)
          } else {
            // openOutputStream returned null (SAF failure). Reset the export
            // state so the UI doesn't stay stuck in Exporting with staged data.
            backupViewModel.exportCoordinator.onExportPickerCancelled()
            settingsViewModel.showMessage("خطا در باز کردن نویسنده فایل")
          }
        } catch (e: Exception) {
          AppLogger.e("SettingsScreen", "خطای ناشناخته در شروع خروجی تفصیلی", e)
          backupViewModel.exportCoordinator.onExportPickerCancelled()
          settingsViewModel.showMessage("خطا در شروع خروجی تفصیلی")
        }
      } else {
        backupViewModel.exportCoordinator.onExportPickerCancelled()
      }
    }

  val exportPickerLaunchRequest by backupViewModel.exportPickerLaunchRequest

  // Launch the save-location picker only after staging finished. Launching it
  // inline in the click handler raced the async PBKDF2/encryption: the picker
  // opened even when staging would fail, and a fast picker return could hit
  // writeStagedExportToFile before the JSON was staged.
  LaunchedEffect(exportPickerLaunchRequest) {
    if (exportPickerLaunchRequest) {
      exportFileLauncher.launch("hesabyar_backup_${System.currentTimeMillis() / 1000}.json")
      backupViewModel.exportCoordinator.consumeExportPickerLaunchRequest()
    }
  }

  val importFileLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.GetContent()
    ) { uri ->
      if (uri != null) {
        try {
          val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
          if (inputStream != null) {
            backupViewModel.importCoordinator.validateAndStageImport(inputStream)
          }
        } catch (e: Exception) {
          AppLogger.e("SettingsScreen", "خطا در بارگذاری فایل", e)
          settingsViewModel.showMessage("خطا در بارگذاری فایل: ${e.localizedMessage}")
        }
      }
    }

  val operationState by backupViewModel.operationState
  val pendingRestore = backupViewModel.pendingRestoreBackup
  val restoreMode by backupViewModel.selectedRestoreMode
  val exportState by exportViewModel.exportState
  val passphraseDialog by backupViewModel.passphraseDialogState
  val isCryptoInProgress by backupViewModel.isCryptoInProgress

  LaunchedEffect(exportState) {
    when (val state = exportState) {
      is ExportState.Success -> {
        settingsViewModel.showMessage(state.summary)
        exportViewModel.clearState()
      }
      is ExportState.Error -> {
        settingsViewModel.showMessage(state.message)
        exportViewModel.clearState()
      }
      else -> {}
    }
  }

  LaunchedEffect(operationState) {
    when (val state = operationState) {
      is BackupOperationState.ExportSuccess -> {
        settingsViewModel.showMessage(state.message)
        backupViewModel.clearOperationState()
      }
      is BackupOperationState.ImportSuccess -> {
        settingsViewModel.showMessage(state.message)
        backupViewModel.clearOperationState()
      }
      is BackupOperationState.Error -> {
        settingsViewModel.showMessage(state.message)
        backupViewModel.clearOperationState()
      }
      is BackupOperationState.ValidationFailed -> {
        settingsViewModel.showMessage("خطا در اعتبارسنجی: ${state.errors.first()}")
        backupViewModel.clearOperationState()
      }
      else -> {}
    }
  }

  if (pendingRestore.value != null) {
    val backup: BackupPayload = pendingRestore.value!!
    HesabyarDialog(
      onDismissRequest = { backupViewModel.importCoordinator.cancelPendingRestore() },
      title = "بازیابی پشتیبان",
      confirmButton = {
        HesabyarButton(
          onClick = { backupViewModel.importCoordinator.executeRestore() },
          text = if (restoreMode == RestoreMode.REPLACE) "جایگزینی کامل" else "ادغام",
          variant = ButtonVariant.Filled,
          colors =
            if (restoreMode == RestoreMode.REPLACE) {
              ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
              ButtonDefaults.buttonColors()
            }
        )
      },
      dismissButton = {
        HesabyarButton(
          onClick = { backupViewModel.importCoordinator.cancelPendingRestore() },
          text = stringResource(R.string.action_cancel),
          variant = ButtonVariant.Text
        )
      }
    ) {
      RestoreDialogContent(
        backup = backup,
        restoreMode = restoreMode,
        onModeSelect = { backupViewModel.selectedRestoreMode.value = it }
      )
    }
  }

  // --- Export passphrase dialog ---
  if (passphraseDialog is PassphraseDialogState.ExportPassphrase) {
    ExportPassphraseDialog(
      isCryptoInProgress = isCryptoInProgress,
      onConfirm = backupViewModel.exportCoordinator::exportWithPassphrase,
      onSaveWithoutEncryption = backupViewModel.exportCoordinator::exportWithoutPassphrase,
      onDismiss = backupViewModel.importCoordinator::cancelPassphraseDialog
    )
  }

  // --- Import passphrase dialog ---
  if (passphraseDialog is PassphraseDialogState.ImportPassphrase) {
    val importPassphrase = passphraseDialog as PassphraseDialogState.ImportPassphrase
    ImportPassphraseDialog(
      errorMessage = importPassphrase.errorMessage,
      isCryptoInProgress = isCryptoInProgress,
      onConfirm = backupViewModel.importCoordinator::decryptAndStageImport,
      onDismiss = backupViewModel.importCoordinator::cancelPassphraseDialog
    )
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(SpacingTokens.lg)
        .verticalScroll(scrollState),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg),
    horizontalAlignment = Alignment.Start
  ) {
    // App branding
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .clip(ShapeTokens.XLarge)
          .background(MaterialTheme.colorScheme.primary.copy(alpha = Dimens.SUBTLE_BRAND_ALPHA))
          .padding(SpacingTokens.xl)
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = Icons.Filled.AccountBalance,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(Dimens.AvatarLarge)
        )
        Text(
          text = "حسابیار هوشمند فارسی",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = "نسخه ${BuildConfig.VERSION_NAME} | توسعه‌دهنده: mcuteangel",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // General Settings
    Text(
      text = "تنظیمات عمومی",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.semantics { heading() },
    )

    HesabyarCard(
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
      ) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clickable(role = Role.Button) { onNavigateToCategories() }
              .clip(ShapeTokens.Medium)
              .background(MaterialTheme.colorScheme.surfaceContainerLowest)
              .padding(SpacingTokens.md),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
        ) {
          Icon(
            imageVector = Icons.Filled.Category,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "مدیریت دسته‌بندی‌ها",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "افزودن، ویرایش و حذف دسته‌بندی‌های تراکنش",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Icon(
            imageVector = Icons.Filled.ChevronLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        Row(
          // The whole row toggles the switch. TalkBack reads the label and the state.
          modifier =
            Modifier.fillMaxWidth().toggleable(
              value = settingsViewModel.isDarkMode.value,
              onValueChange = { settingsViewModel.toggleDarkMode() },
              role = Role.Switch
            ),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
          ) {
            Icon(
              imageVector = Icons.Filled.DarkMode,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
            Text("تم تاریک فعال (Dark Theme)", style = MaterialTheme.typography.bodyMedium)
          }
          Switch(
            checked = settingsViewModel.isDarkMode.value,
            onCheckedChange = null,
            enabled = true,
            modifier = Modifier.testTag("dark_mode_switch")
          )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        // Currency unit selector
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
          ) {
            Icon(
              imageVector = Icons.Filled.AttachMoney,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
            Text("واحد پول", style = MaterialTheme.typography.bodyMedium)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
            val currentUnit = settingsViewModel.currencyUnit.value
            CurrencyUnit.entries.forEach { unit ->
              FilterChip(
                selected = currentUnit == unit,
                onClick = { settingsViewModel.setCurrencyUnit(unit) },
                label = { Text(unit.label) }
              )
            }
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        ReminderSettingsSection(settingsViewModel = settingsViewModel)

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        SecuritySection(context = context, settingsViewModel = settingsViewModel)
      }
    }

    // AI Provider Settings
    Text(
      text = "تنظیمات هوش مصنوعی (API Settings)",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.semantics { heading() },
    )

    AiProviderSettingsCard(aiAssistantViewModel = aiAssistantViewModel)

    // Backup
    Text(
      text = "پشتیبان‌گیری و بازیابی داده‌ها (آفلاین)",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
      modifier =
        Modifier
          .padding(top = SpacingTokens.sm)
          .semantics { heading() },
    )

    HesabyarCard(
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
      ) {
        Text(
          text =
            "جهت جلوگیری از دست رفتن امور مالی خود، به صورت دوره‌ای اقدام به" +
              " تهیه‌ی پشتیبان بفرمایید. فایل خروجی به شکل استاندارد JSON در حافظه ذخیره می‌شود.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 18.sp
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
        ) {
          HesabyarButton(
            onClick = { importFileLauncher.launch("application/json") },
            modifier = Modifier.weight(1f).testTag("restore_button"),
            text = "بازیابی پشتیبان",
            icon = Icons.Filled.UploadFile,
            variant = ButtonVariant.Outlined,
            loading = operationState is BackupOperationState.Importing,
            enabled = operationState !is BackupOperationState.Importing
          )

          HesabyarButton(
            onClick = { backupViewModel.exportCoordinator.requestExportPassphraseDialog() },
            modifier = Modifier.weight(1.1f).testTag("backup_button"),
            text = "ذخیره فایل پشتیبان",
            icon = Icons.Filled.Save,
            loading = operationState is BackupOperationState.Exporting || isCryptoInProgress,
            enabled = operationState !is BackupOperationState.Exporting && !isCryptoInProgress
          )
        }
      }
    }

    // Excel Export
    Text(
      text = "خروجی اکسل (Excel)",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
      modifier =
        Modifier
          .padding(top = SpacingTokens.sm)
          .semantics { heading() },
    )

    HesabyarCard(
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
      ) {
        Text(
          text =
            "گزارش کامل مالی شامل تراکنش‌ها، دریافتی‌ها، پرداختی‌ها، وام‌ها و" +
              " اقساط در قالب فایل اکسل (.xlsx) خروجی گرفته شود.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 18.sp
        )

        HesabyarButton(
          onClick = { exportViewModel.exportExcel() },
          modifier = Modifier.fillMaxWidth().testTag("export_excel_button"),
          text = "ذخیره در Downloads",
          icon = Icons.Filled.TableChart,
          loading = exportState is ExportState.Exporting,
          enabled = exportState !is ExportState.Exporting
        )
      }
    }

    if (BuildConfig.DEBUG) {
      DebugLogsSection()
    }
  }
}

@Composable
fun SecuritySection(
  context: Context,
  settingsViewModel: SettingsViewModel
) {
  var isPinSet by remember { mutableStateOf(PinStorage.isPinSet(context)) }
  var hasBiometric by remember { mutableStateOf(BiometricHelper.isBiometricAvailable(context)) }
  var showSetPinDialog by remember { mutableStateOf(false) }
  var showVerifyPinDialog by remember { mutableStateOf(false) }
  var pendingDisable by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  val pinLockChecked = isPinSet && !pendingDisable
  val onPinLockChange: (Boolean) -> Unit = { checked ->
    if (checked) {
      pendingDisable = false
      if (!isPinSet) {
        showSetPinDialog = true
      }
    } else {
      if (isPinSet) {
        pendingDisable = true
        showVerifyPinDialog = true
      }
    }
  }
  Row(
    // The whole row toggles the switch. TalkBack reads the label and the state.
    modifier =
      Modifier.fillMaxWidth().toggleable(
        value = pinLockChecked,
        onValueChange = onPinLockChange,
        role = Role.Switch
      ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
  ) {
    Icon(imageVector = Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "قفل برنامه",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = if (isPinSet) "قفل با رمز فعال" else "قفل غیرفعال",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Switch(
      checked = pinLockChecked,
      onCheckedChange = null,
      enabled = true
    )
  }

  if (isPinSet) {
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .clickable(role = Role.Button) {
            if (BiometricHelper.isBiometricAvailable(context)) {
              val activity = context.findActivity()
              if (activity != null) {
                BiometricHelper.authenticate(
                  activity = activity,
                  onSuccess = { settingsViewModel.showMessage("احراز هویت با موفقیت انجام شد") },
                  onError = { settingsViewModel.showMessage("خطا در احراز هویت") },
                  onFailed = { settingsViewModel.showMessage("احراز هویت ناموفق") }
                )
              }
            } else {
              settingsViewModel.showMessage("احراز هویت بیومتریک در دستگاه شما پشتیبانی نمی‌شود")
            }
          }.clip(ShapeTokens.Small)
          .padding(vertical = SpacingTokens.sm),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      Icon(
        imageVector = Icons.Filled.Fingerprint,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "تست اثر انگشت",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = if (hasBiometric) "قفل با اثر انگشت فعال" else "پشتیبانی نمی‌شود",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }

  if (showVerifyPinDialog) {
    SecurityDialogs.VerifyPinDialog(
      onDismiss = {
        showVerifyPinDialog = false
        pendingDisable = false
      },
      onPinVerifiedAndCleared = {
        isPinSet = false
        pendingDisable = false
        showVerifyPinDialog = false
        settingsViewModel.showMessage("قفل برنامه غیرفعال شد")
      },
      onClearFailed = { error ->
        isPinSet = PinStorage.isPinSet(context)
        pendingDisable = false
        showVerifyPinDialog = false
        settingsViewModel.showMessage(error)
      }
    )
  }

  if (showSetPinDialog) {
    SecurityDialogs.SetPinDialog(
      onDismiss = { showSetPinDialog = false },
      onPinSet = { pin ->
        try {
          PinStorage.setPin(context, pin)
          isPinSet = true
          showSetPinDialog = false
          settingsViewModel.showMessage("قفل برنامه با رمز فعال شد")
        } catch (_: Exception) {
          settingsViewModel.showMessage("خطا در تنظیم رمز عبور")
        }
      }
    )
  }
}

@Composable
private fun RestoreDialogContent(
  backup: BackupPayload,
  restoreMode: RestoreMode,
  onModeSelect: (RestoreMode) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
  ) {
    Text(
      text = "فایل پشتیبان معتبر است. لطفاً نوع بازیابی را انتخاب کنید:",
      style = MaterialTheme.typography.bodyMedium
    )
    Text(
      text =
        "تراکنش‌ها: ${backup.transactions.size} | وام‌ها: ${backup.loans.size} |" +
          " اقساط: ${backup.installments.size} | دسته‌بندی‌ها: ${backup.categories.size}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(SpacingTokens.xs))

    RestoreModeOption(
      selected = restoreMode == RestoreMode.REPLACE,
      onClick = { onModeSelect(RestoreMode.REPLACE) },
      title = "جایگزینی کامل",
      description = "تمام داده‌های فعلی حذف و جایگزین می‌شود"
    )

    RestoreModeOption(
      selected = restoreMode == RestoreMode.MERGE,
      onClick = { onModeSelect(RestoreMode.MERGE) },
      title = "ادغام",
      description = "داده‌های جدید به اطلاعات فعلی اضافه می‌شود"
    )
  }
}

@Composable
private fun RestoreModeOption(
  selected: Boolean,
  onClick: () -> Unit,
  title: String,
  description: String
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .selectable(
          selected = selected,
          onClick = onClick,
          role = Role.RadioButton
        ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
  ) {
    RadioButton(
      selected = selected,
      onClick = null
    )
    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Suppress("TooGenericExceptionCaught")
private object SecurityDialogs {
  private const val PIN_LENGTH = 6

  private suspend fun safeClearPin(context: Context): Boolean =
    try {
      withContext(Dispatchers.IO) { PinStorage.clearPin(context) }
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: GeneralSecurityException) {
      AppLogger.e("SettingsScreen", "Security exception clearing PIN", e)
      false
    } catch (e: IOException) {
      AppLogger.e("SettingsScreen", "IO exception clearing PIN", e)
      false
    } catch (e: SecurityException) {
      AppLogger.e("SettingsScreen", "Permission exception clearing PIN", e)
      false
    } catch (e: Exception) {
      AppLogger.e("SettingsScreen", "Unexpected exception clearing PIN", e)
      false
    }

  private suspend fun verifyAndClearPin(
    context: Context,
    pin: String,
    onSuccess: () -> Unit,
    onClearFailed: (String) -> Unit,
    onInvalidPin: (String) -> Unit,
    onVerifyError: (String) -> Unit
  ) {
    val verified =
      try {
        withContext(Dispatchers.IO) { PinStorage.verifyPin(context, pin) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        AppLogger.e("SettingsScreen", "Exception verifying PIN", e)
        null
      }

    when (verified) {
      null -> onVerifyError("خطا در بررسی رمز عبور")
      false -> onInvalidPin("رمز عبور اشتباه است")
      true -> {
        try {
          if (safeClearPin(context)) {
            onSuccess()
          } else {
            onClearFailed("خطا در غیرفعالسازی قفل برنامه")
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          AppLogger.e("SettingsScreen", "Unexpected exception during safeClearPin", e)
          onClearFailed("خطا در غیرفعالسازی قفل برنامه")
        }
      }
    }
  }

  @Composable
  fun VerifyPinDialog(
    onDismiss: () -> Unit,
    onPinVerifiedAndCleared: () -> Unit,
    onClearFailed: ((String) -> Unit)? = null
  ) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    val parentOnClearFailed = onClearFailed

    HesabyarDialog(
      onDismissRequest = { if (!verifying) onDismiss() },
      title = "تأیید رمز عبور",
      confirmText = "تأیید و غیرفعال‌سازی",
      confirmEnabled = currentPin.length == PIN_LENGTH && !verifying,
      confirmLoading = verifying,
      onConfirm = {
        scope.launch {
          verifying = true
          try {
            verifyAndClearPin(
              context = context,
              pin = currentPin,
              onSuccess = onPinVerifiedAndCleared,
              onClearFailed = { error ->
                pinError = error
                if (parentOnClearFailed != null) {
                  parentOnClearFailed(error)
                } else {
                  onDismiss()
                }
              },
              onInvalidPin = { pinError = it },
              onVerifyError = { pinError = it }
            )
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            AppLogger.e("SettingsScreen", "Unexpected exception in VerifyPinDialog", e)
            pinError = "خطا در بررسی رمز عبور"
          } finally {
            verifying = false
          }
        }
      },
      dismissText = stringResource(R.string.action_cancel),
      onDismiss = { if (!verifying) onDismiss() }
    ) {
      HesabyarInputField(
        value = currentPin,
        onValueChange = {
          currentPin = it.filter { c -> c.isDigit() }.take(PIN_LENGTH)
          pinError = null
        },
        label = "رمز عبور فعلی",
        placeholder = "۶ رقم",
        isError = pinError != null,
        supportingText = pinError?.let { error -> { Text(error) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
      )
    }
  }

  @Composable
  fun SetPinDialog(
    onDismiss: () -> Unit,
    onPinSet: (String) -> Unit
  ) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    HesabyarDialog(
      onDismissRequest = onDismiss,
      title = "تنظیم رمز عبور",
      confirmText = stringResource(R.string.action_save),
      confirmEnabled = newPin.length == PIN_LENGTH && confirmPin.length == PIN_LENGTH,
      onConfirm = {
        when {
          newPin.length != PIN_LENGTH || !newPin.all { it.isDigit() } -> {
            pinError = "رمز عبور باید دقیقاً ۶ رقم باشد"
          }
          newPin != confirmPin -> {
            pinError = "رمز عبور مطابقت ندارد"
          }
          else -> {
            onPinSet(newPin)
          }
        }
      },
      dismissText = stringResource(R.string.action_cancel),
      onDismiss = onDismiss
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)) {
        HesabyarInputField(
          value = newPin,
          onValueChange = {
            newPin = it.filter { c -> c.isDigit() }.take(PIN_LENGTH)
            pinError = null
          },
          label = "رمز عبور جدید",
          placeholder = "۶ رقم",
          isError = pinError != null,
          supportingText = pinError?.let { error -> { Text(error) } },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        HesabyarInputField(
          value = confirmPin,
          onValueChange = {
            confirmPin = it.filter { c -> c.isDigit() }.take(PIN_LENGTH)
            pinError = null
          },
          label = "تکرار رمز عبور",
          placeholder = "۶ رقم",
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
      }
    }
  }
}

@Composable
fun DebugLogsSection() {
  var isExpanded by remember { mutableStateOf(false) }
  var logs by remember { mutableStateOf(AppLogger.getAiLogs()) }
  var autoRefresh by remember { mutableStateOf(true) }
  val context = LocalContext.current

  LaunchedEffect(autoRefresh) {
    if (autoRefresh) {
      while (true) {
        logs = AppLogger.getAiLogs()
        kotlinx.coroutines.delay(1000)
      }
    }
  }

  Text(
    text = "لاگ‌های دیباگ (Debug Logs)",
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onBackground,
    modifier =
      Modifier
        .padding(top = SpacingTokens.sm)
        .semantics { heading() },
  )

  HesabyarCard(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
        ) {
          Icon(
            imageVector = Icons.Filled.BugReport,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.IconMedium)
          )
          Column {
            Text(
              text = "لاگ‌های هوش مصنوعی",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "${logs.size} رویداد ثبت شده",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs)) {
          IconButton(onClick = { logs = AppLogger.getAiLogs() }) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "بروزرسانی",
              tint = MaterialTheme.colorScheme.primary
            )
          }
          IconButton(onClick = {
            val logsText = logs.joinToString("\n") { it.formatted() }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("AI Logs", logsText)
            clipboard.setPrimaryClip(clip)
          }) {
            Icon(
              imageVector = Icons.Filled.ContentCopy,
              contentDescription = "کپی لاگ‌ها",
              tint = MaterialTheme.colorScheme.primary
            )
          }
          IconButton(onClick = {
            AppLogger.clear()
            logs = emptyList()
          }) {
            Icon(
              imageVector = Icons.Filled.DeleteSweep,
              contentDescription = "پاک کردن",
              tint = MaterialTheme.colorScheme.error
            )
          }
        }
      }

      Row(
        // The whole row toggles the switch. TalkBack reads the label and the state.
        modifier =
          Modifier.fillMaxWidth().toggleable(
            value = autoRefresh,
            onValueChange = { autoRefresh = it },
            role = Role.Switch
          ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
      ) {
        Switch(
          checked = autoRefresh,
          onCheckedChange = null,
          enabled = true,
          modifier = Modifier.testTag("auto_refresh_switch")
        )
        Text(
          text = if (autoRefresh) "بروزرسانی خودکار (هر ۱ ثانیه)" else "بروزرسانی خودکار غیرفعال",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      if (logs.isEmpty()) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(100.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "هنوز لاگی ثبت نشده است.\nیک عملیات AI انجام دهید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
          )
        }
      } else {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .heightIn(max = 300.dp)
              .verticalScroll(rememberScrollState())
              .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                ShapeTokens.Small
              ).padding(SpacingTokens.sm),
          verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
        ) {
          logs.reversed().forEach { entry ->
            val levelColor =
              when (entry.level) {
                "E" -> MaterialTheme.colorScheme.error
                "W" -> MaterialTheme.colorScheme.tertiary
                "I" -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = Dimens.SECONDARY_TEXT_ALPHA)
              }
            Text(
              text = entry.formatted(),
              style = MaterialTheme.typography.bodySmall,
              color = levelColor,
              lineHeight = 16.sp,
              modifier = Modifier.padding(vertical = SpacingTokens.xs)
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderSettingsCard(aiAssistantViewModel: AiAssistantViewModel) {
  val configs by aiAssistantViewModel.aiConfigs
  val activeConfigId by aiAssistantViewModel.activeConfigId
  val isOnlineMode by aiAssistantViewModel.isOnlineMode
  val modelFetchState by aiAssistantViewModel.modelFetchState.collectAsState()

  var showAddDialog by remember { mutableStateOf(false) }
  var editingConfig by remember { mutableStateOf<AiProviderConfig?>(null) }

  HesabyarCard(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
    ) {
      // Online/Offline Toggle
      Row(
        // The whole row toggles the switch. TalkBack reads the label and the state.
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(ShapeTokens.Medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .toggleable(value = isOnlineMode, role = Role.Switch) { aiAssistantViewModel.toggleOnlineMode() }
            .padding(SpacingTokens.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
        ) {
          Icon(
            imageVector = if (isOnlineMode) Icons.Filled.Cloud else Icons.Filled.CloudOff,
            contentDescription = null,
            tint = if (isOnlineMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.IconMedium)
          )
          Column {
            Text(
              text = if (isOnlineMode) "حالت آنلاین" else "حالت آفلاین",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = if (isOnlineMode) "استفاده از ارائه‌دهنده هوش مصنوعی" else "استفاده از موتور محلی",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        Switch(
          checked = isOnlineMode,
          onCheckedChange = null,
          enabled = true
        )
      }

      // Saved Configs List
      if (configs.isNotEmpty()) {
        Text(
          text = "تنظیمات ذخیره شده:",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        configs.forEach { config ->
          val isActive = config.id == activeConfigId
          ConfigItem(
            config = config,
            isActive = isActive,
            onSelect = { aiAssistantViewModel.setActiveConfig(config.id) },
            onEdit = { editingConfig = config },
            onDelete = { cfg -> aiAssistantViewModel.deleteAiConfig(cfg.id) }
          )
        }
      }

      // Add New Config Button
      HesabyarButton(
        onClick = { showAddDialog = true },
        modifier = Modifier.fillMaxWidth(),
        text = "افزودن تنظیمات جدید",
        icon = Icons.Filled.Add,
        variant = ButtonVariant.Outlined
      )
    }
  }

  // Add/Edit Dialog
  if (showAddDialog || editingConfig != null) {
    AiConfigDialog(
      initialConfig = editingConfig,
      onDismiss = {
        showAddDialog = false
        editingConfig = null
      },
      onSave = { config ->
        if (editingConfig != null) {
          aiAssistantViewModel.updateAiConfig(config)
        } else {
          aiAssistantViewModel.addAiConfig(config)
        }
        showAddDialog = false
        editingConfig = null
      },
      onFetchModels = { providerType, apiKey, baseUrl ->
        aiAssistantViewModel.fetchModels(providerType, apiKey, baseUrl)
      },
      modelFetchState = modelFetchState,
      onClearModelFetchState = { aiAssistantViewModel.clearModelFetchState() }
    )
  }
}

@Composable
fun ConfigItem(
  config: AiProviderConfig,
  isActive: Boolean,
  onSelect: () -> Unit,
  onEdit: () -> Unit,
  onDelete: (AiProviderConfig) -> Unit
) {
  // Single-select list: expose the row as a radio button with its selected state.
  val containerColor =
    if (isActive) {
      MaterialTheme.colorScheme.primaryContainer.copy(alpha = Dimens.BANNER_CONTAINER_ALPHA)
    } else {
      MaterialTheme.colorScheme.surfaceContainerLowest
    }
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(ShapeTokens.Medium)
        .background(containerColor)
        .selectable(selected = isActive, onClick = { onSelect() }, role = Role.RadioButton)
        .padding(SpacingTokens.md),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
  ) {
    Icon(
      imageVector = if (isActive) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
      contentDescription = null,
      tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(Dimens.IconMedium)
    )

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = config.displayName,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = "${config.providerType.displayName} | ${config.model.ifBlank { "بدون مدل" }}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    // Enforce the 48dp minimum touch area; the minimum must come before
    // the fixed size, otherwise the fixed size wins and the target stays small.
    IconButton(onClick = onEdit, modifier = Modifier.minimumInteractiveComponentSize().size(Dimens.IconLarge)) {
      Icon(
        imageVector = Icons.Filled.Edit,
        contentDescription = "ویرایش",
        modifier = Modifier.size(Dimens.IconSmall),
        tint = MaterialTheme.colorScheme.primary
      )
    }

    ConfigDeleteButton(config = config, onDelete = onDelete)
  }
}

/**
 * Delete button with a confirmation dialog. Holds the tapped config itself,
 * not a boolean: the list is positional, so a reorder while the dialog is
 * open must not retarget the delete to a different row.
 */
@Composable
private fun ConfigDeleteButton(
  config: AiProviderConfig,
  onDelete: (AiProviderConfig) -> Unit
) {
  var pendingDelete by remember { mutableStateOf<AiProviderConfig?>(null) }

  // Enforce the 48dp minimum touch area; the minimum must come before
  // the fixed size, otherwise the fixed size wins and the target stays small.
  IconButton(
    onClick = { pendingDelete = config },
    modifier = Modifier.minimumInteractiveComponentSize().size(Dimens.IconLarge)
  ) {
    Icon(
      imageVector = Icons.Filled.Delete,
      contentDescription = "حذف",
      modifier = Modifier.size(Dimens.IconSmall),
      tint = MaterialTheme.colorScheme.error
    )
  }

  if (pendingDelete != null) {
    val target = pendingDelete!!
    ConfirmDialog(
      title = "حذف پیکربندی",
      message =
        "آیا از حذف پیکربندی «${target.displayName}» اطمینان دارید؟" +
          " کلید API ذخیره‌شده هم پاک می‌شود و این عمل قابل بازگشت نیست.",
      confirmText = stringResource(R.string.action_delete),
      dismissText = stringResource(R.string.action_cancel),
      onDismiss = { pendingDelete = null },
      onConfirm = {
        pendingDelete = null
        onDelete(target)
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigDialog(
  initialConfig: AiProviderConfig?,
  onDismiss: () -> Unit,
  onSave: (AiProviderConfig) -> Unit,
  onFetchModels: (AiProviderType, String, String?) -> Unit,
  modelFetchState: UiResult<List<String>>,
  onClearModelFetchState: () -> Unit
) {
  val isEditing = initialConfig != null
  var label by remember { mutableStateOf(initialConfig?.label.orEmpty()) }
  var selectedProvider by remember { mutableStateOf(initialConfig?.providerType ?: AiProviderType.GEMINI) }
  var apiKey by remember { mutableStateOf(initialConfig?.apiKey.orEmpty()) }
  var model by remember { mutableStateOf(initialConfig?.model.orEmpty()) }
  var baseUrl by remember { mutableStateOf(initialConfig?.baseUrl.orEmpty()) }
  var showApiKey by remember { mutableStateOf(false) }
  var providerDropdownExpanded by remember { mutableStateOf(false) }
  var modelDropdownExpanded by remember { mutableStateOf(false) }
  var modelSearchQuery by remember { mutableStateOf("") }

  val fetchedModels =
    when (val state = modelFetchState) {
      is UiResult.Success -> state.data
      else -> emptyList()
    }

  val filteredModels =
    if (modelSearchQuery.isBlank()) {
      fetchedModels
    } else {
      fetchedModels.filter { it.contains(modelSearchQuery, ignoreCase = true) }
    }

  val geminiFamilies =
    fetchedModels.groupBy { model ->
      when {
        model.contains("gemini", ignoreCase = true) -> "Gemini"
        model.contains("gemma", ignoreCase = true) -> "Gemma"
        model.contains("imagen", ignoreCase = true) -> "Imagen"
        else -> "Other"
      }
    }

  val openRouterGroups =
    fetchedModels.groupBy { model ->
      model.split("/").firstOrNull() ?: "Unknown"
    }

  HesabyarDialog(
    title = if (isEditing) "ویرایش تنظیمات" else "افزودن تنظیمات جدید",
    onDismissRequest = {
      onClearModelFetchState()
      onDismiss()
    },
    actions = {
      HesabyarButton(
        onClick = {
          onSave(
            AiProviderConfig(
              id = initialConfig?.id.orEmpty(),
              providerType = selectedProvider,
              apiKey = apiKey.trim(),
              model = model.trim(),
              baseUrl = baseUrl.trim(),
              label = label.trim()
            )
          )
          onClearModelFetchState()
        },
        text = stringResource(R.string.action_save),
        variant = ButtonVariant.Filled
      )
      HesabyarButton(
        onClick = {
          onClearModelFetchState()
          onDismiss()
        },
        text = stringResource(R.string.action_cancel),
        variant = ButtonVariant.Text
      )
    }
  ) {
    // Label
    HesabyarInputField(
      value = label,
      onValueChange = { label = it },
      label = "نام اختصاصی (اختیاری)",
      placeholder = "مثلاً: کلید اصلی Gemini"
    )

    // Provider Dropdown
    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)) {
      Text(
        text = "ارائه‌دهنده:",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      ExposedDropdownMenuBox(
        expanded = providerDropdownExpanded,
        onExpandedChange = { providerDropdownExpanded = it }
      ) {
        HesabyarInputField(
          value = selectedProvider.displayName,
          onValueChange = {},
          modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
          readOnly = true,
          shape = ShapeTokens.Large,
          trailingIcon = {
            ExposedDropdownMenuDefaults.TrailingIcon(
              expanded = providerDropdownExpanded
            )
          }
        )
        ExposedDropdownMenu(
          expanded = providerDropdownExpanded,
          onDismissRequest = { providerDropdownExpanded = false }
        ) {
          AiProviderType.entries.forEach { type ->
            DropdownMenuItem(
              text = { Text(type.displayName) },
              onClick = {
                selectedProvider = type
                model = ""
                baseUrl =
                  when (type) {
                    AiProviderType.OPENROUTER -> "https://openrouter.ai/api/v1"
                    else -> ""
                  }
                providerDropdownExpanded = false
                onClearModelFetchState()
              }
            )
          }
        }
      }
    }

    // API Key
    HesabyarInputField(
      value = apiKey,
      onValueChange = { apiKey = it },
      label = "کلید API Key",
      placeholder =
        when (selectedProvider) {
          AiProviderType.GEMINI -> "AIza..."
          AiProviderType.OPENROUTER -> "sk-or-..."
          AiProviderType.CUSTOM -> "your-api-key"
        },
      trailingIcon = {
        IconButton(onClick = { showApiKey = !showApiKey }) {
          Icon(
            imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = if (showApiKey) "مخفی" else "نمایش"
          )
        }
      },
      visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation()
    )

    // Base URL (for OpenRouter and Custom)
    if (selectedProvider != AiProviderType.GEMINI) {
      HesabyarInputField(
        value = baseUrl,
        onValueChange = { baseUrl = it },
        label = "آدرس API (Base URL)",
        placeholder =
          when (selectedProvider) {
            AiProviderType.OPENROUTER -> "https://openrouter.ai/api/v1"
            AiProviderType.CUSTOM -> "https://api.example.com/v1"
            else -> ""
          }
      )
    }

    // Fetch Models Button
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      HesabyarButton(
        onClick = { onFetchModels(selectedProvider, apiKey, baseUrl.ifBlank { null }) },
        modifier = Modifier.weight(1f),
        text = "دریافت مدل‌ها",
        icon = Icons.Filled.Refresh,
        loading = modelFetchState is UiResult.Loading,
        enabled = apiKey.isNotBlank() && modelFetchState !is UiResult.Loading
      )

      if (modelFetchState is UiResult.Error) {
        Text(
          text = modelFetchState.message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.align(Alignment.CenterVertically)
        )
      }
    }

    // Model Dropdown with search
    if (fetchedModels.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)) {
        Text(
          text = "انتخاب مدل (${fetchedModels.size} مدل موجود):",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Search field
        HesabyarInputField(
          value = modelSearchQuery,
          onValueChange = { modelSearchQuery = it },
          placeholder = "جستجوی مدل...",
          singleLine = true
        )

        ExposedDropdownMenuBox(
          expanded = modelDropdownExpanded,
          onExpandedChange = { modelDropdownExpanded = it }
        ) {
          HesabyarInputField(
            value = model,
            onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            shape = ShapeTokens.Large,
            placeholder = "نام مدل را تایپ یا انتخاب کنید",
            trailingIcon = {
              ExposedDropdownMenuDefaults.TrailingIcon(
                expanded = modelDropdownExpanded
              )
            }
          )

          ExposedDropdownMenu(
            expanded = modelDropdownExpanded,
            onDismissRequest = { modelDropdownExpanded = false }
          ) {
            if (selectedProvider == AiProviderType.GEMINI) {
              geminiFamilies.forEach { (family, models) ->
                DropdownMenuItem(
                  text = {
                    Text(
                      "$family (${models.size})",
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary
                    )
                  },
                  onClick = { }
                )
                models.take(10).forEach { m ->
                  DropdownMenuItem(
                    text = {
                      Text(
                        "  $m",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    },
                    onClick = {
                      model = m
                      modelDropdownExpanded = false
                      modelSearchQuery = ""
                    }
                  )
                }
                if (models.size > 10) {
                  DropdownMenuItem(
                    text = {
                      Text(
                        "  ... و ${models.size - 10} مدل دیگر",
                        style = MaterialTheme.typography.bodySmall
                      )
                    },
                    onClick = { }
                  )
                }
              }
            } else if (selectedProvider == AiProviderType.OPENROUTER) {
              openRouterGroups.forEach { (provider, models) ->
                val freeCount = models.count { it.endsWith(":free") }
                DropdownMenuItem(
                  text = {
                    Text(
                      "$provider (${models.size}${if (freeCount > 0) ", $freeCount رایگان" else ""})",
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary
                    )
                  },
                  onClick = { }
                )
                models.take(8).forEach { m ->
                  val isFree = m.endsWith(":free")
                  DropdownMenuItem(
                    text = {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                          "  $m",
                          style = MaterialTheme.typography.bodySmall,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.weight(1f)
                        )
                        if (isFree) {
                          Spacer(modifier = Modifier.width(SpacingTokens.xs))
                          Text(
                            "رایگان",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                          )
                        }
                      }
                    },
                    onClick = {
                      model = m
                      modelDropdownExpanded = false
                      modelSearchQuery = ""
                    }
                  )
                }
                if (models.size > 8) {
                  DropdownMenuItem(
                    text = {
                      Text(
                        "  ... و ${models.size - 8} مدل دیگر",
                        style = MaterialTheme.typography.bodySmall
                      )
                    },
                    onClick = { }
                  )
                }
              }
            } else {
              filteredModels.take(20).forEach { m ->
                DropdownMenuItem(
                  text = {
                    Text(
                      m,
                      style = MaterialTheme.typography.bodySmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  },
                  onClick = {
                    model = m
                    modelDropdownExpanded = false
                    modelSearchQuery = ""
                  }
                )
              }
            }
          }
        }
      }
    } else {
      // Manual model input
      HesabyarInputField(
        value = model,
        onValueChange = { model = it },
        label = "نام مدل (Model)",
        placeholder =
          when (selectedProvider) {
            AiProviderType.GEMINI -> "gemini-2.0-flash"
            AiProviderType.OPENROUTER -> "google/gemini-2.0-flash-001"
            AiProviderType.CUSTOM -> "model-name"
          }
      )
    }
  }
}

@Composable
fun ReminderSettingsSection(settingsViewModel: SettingsViewModel) {
  val context = LocalContext.current
  val settingsManager =
    remember {
      io.github.mojri.hesabyar.reminder
        .ReminderSettingsManager(context)
    }
  var config by remember { mutableStateOf(settingsManager.getConfig()) }
  val onMasterReminderChange: (Boolean) -> Unit = { enabled ->
    config = config.copy(masterEnabled = enabled)
    settingsManager.setMasterEnabled(enabled)
    io.github.mojri.hesabyar.reminder.ReminderScheduler
      .refreshReminders(context)
  }

  Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)) {
    Row(
      // The whole row toggles the switch. TalkBack reads the label and the state.
      modifier =
        Modifier.fillMaxWidth().toggleable(
          value = config.masterEnabled,
          onValueChange = onMasterReminderChange,
          role = Role.Switch
        ),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
      ) {
        Icon(
          imageVector = Icons.Filled.Notifications,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary
        )
        Text("هشدار خودکار اقساط و بدهی‌ها", style = MaterialTheme.typography.bodyMedium)
      }
      Switch(
        checked = config.masterEnabled,
        onCheckedChange = null,
        enabled = true
      )
    }

    if (config.masterEnabled) {
      HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

      // Installment reminders toggle
      Row(
        // The whole row toggles the switch. TalkBack reads the label and the state.
        modifier =
          Modifier.fillMaxWidth().toggleable(
            value = config.installmentReminderEnabled,
            onValueChange = { enabled ->
              config = config.copy(installmentReminderEnabled = enabled)
              settingsManager.setInstallmentReminderEnabled(enabled)
              io.github.mojri.hesabyar.reminder.ReminderScheduler
                .refreshReminders(context)
            },
            role = Role.Switch
          ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
        ) {
          Icon(
            imageVector = Icons.Filled.CreditCard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.IconMedium)
          )
          Text("یادآوری اقساط", style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
          checked = config.installmentReminderEnabled,
          onCheckedChange = null,
          enabled = true
        )
      }

      // Loan reminders toggle
      Row(
        // The whole row toggles the switch. TalkBack reads the label and the state.
        modifier =
          Modifier.fillMaxWidth().toggleable(
            value = config.loanReminderEnabled,
            onValueChange = { enabled ->
              config = config.copy(loanReminderEnabled = enabled)
              settingsManager.setLoanReminderEnabled(enabled)
              io.github.mojri.hesabyar.reminder.ReminderScheduler
                .refreshReminders(context)
            },
            role = Role.Switch
          ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
        ) {
          Icon(
            imageVector = Icons.Filled.HistoryEdu,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.IconMedium)
          )
          Text("یادآوری وام‌ها", style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
          checked = config.loanReminderEnabled,
          onCheckedChange = null,
          enabled = true
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

      // Days before due
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "روز قبل از سررسید",
            style = MaterialTheme.typography.bodyMedium
          )
          Text(
            text = "${config.reminderDaysBeforeDue} روز",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
          )
        }
        Slider(
          value = config.reminderDaysBeforeDue.toFloat(),
          onValueChange = { days ->
            config = config.copy(reminderDaysBeforeDue = days.toInt())
          },
          onValueChangeFinished = {
            settingsManager.setReminderDaysBeforeDue(config.reminderDaysBeforeDue)
            io.github.mojri.hesabyar.reminder.ReminderScheduler
              .refreshReminders(context)
          },
          valueRange = 1f..14f,
          steps = 12,
          modifier = Modifier.fillMaxWidth()
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            "۱ روز",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            "۱۴ روز",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

      // Reminder time
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
        ) {
          Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.IconMedium)
          )
          Text("زمان یادآوری", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
          text = String.format("%02d:%02d", config.reminderHour, config.reminderMinute),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold
        )
      }

      // Time picker row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Hour
        HesabyarButton(
          onClick = {
            val newHour = (config.reminderHour + 1) % 24
            config = config.copy(reminderHour = newHour)
            settingsManager.setReminderTime(newHour, config.reminderMinute)
            io.github.mojri.hesabyar.reminder.ReminderScheduler
              .refreshReminders(context)
          },
          modifier = Modifier.weight(1f),
          text = "+ ساعت",
          variant = ButtonVariant.Outlined
        )

        HesabyarButton(
          onClick = {
            val newHour = (config.reminderHour - 1 + 24) % 24
            config = config.copy(reminderHour = newHour)
            settingsManager.setReminderTime(newHour, config.reminderMinute)
            io.github.mojri.hesabyar.reminder.ReminderScheduler
              .refreshReminders(context)
          },
          modifier = Modifier.weight(1f),
          text = "- ساعت",
          variant = ButtonVariant.Outlined
        )

        // Minute
        HesabyarButton(
          onClick = {
            val newMinute = (config.reminderMinute + 15) % 60
            config = config.copy(reminderMinute = newMinute)
            settingsManager.setReminderTime(config.reminderHour, newMinute)
            io.github.mojri.hesabyar.reminder.ReminderScheduler
              .refreshReminders(context)
          },
          modifier = Modifier.weight(1f),
          text = "+ دقیقه",
          variant = ButtonVariant.Outlined
        )

        HesabyarButton(
          onClick = {
            val newMinute = (config.reminderMinute - 15 + 60) % 60
            config = config.copy(reminderMinute = newMinute)
            settingsManager.setReminderTime(config.reminderHour, newMinute)
            io.github.mojri.hesabyar.reminder.ReminderScheduler
              .refreshReminders(context)
          },
          modifier = Modifier.weight(1f),
          text = "- دقیقه",
          variant = ButtonVariant.Outlined
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

      // Loan reminder interval
      Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "دوره یادآوری وام (روز)",
            style = MaterialTheme.typography.bodyMedium
          )
          Text(
            text = "هر ${config.loanReminderDaysInterval} روز",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
          )
        }
        Slider(
          value = config.loanReminderDaysInterval.toFloat(),
          onValueChange = { days ->
            config = config.copy(loanReminderDaysInterval = days.toInt())
          },
          onValueChangeFinished = {
            settingsManager.setLoanReminderInterval(config.loanReminderDaysInterval)
            io.github.mojri.hesabyar.reminder.ReminderScheduler
              .refreshReminders(context)
          },
          valueRange = 1f..30f,
          steps = 28,
          modifier = Modifier.fillMaxWidth()
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            "۱ روز",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            "۳۰ روز",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}
