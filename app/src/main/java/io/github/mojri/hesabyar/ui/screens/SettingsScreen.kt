package io.github.mojri.hesabyar.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.AppLogger
import io.github.mojri.hesabyar.ui.BackupOperationState
import io.github.mojri.hesabyar.ui.BackupViewModel
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.ModelFetchState
import io.github.mojri.hesabyar.data.RestoreMode
import java.io.InputStream
import java.io.OutputStream
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

@Composable
fun SettingsScreen(
    aiAssistantViewModel: AiAssistantViewModel,
    backupViewModel: BackupViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToCategories: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    backupViewModel.exportBackupToFile(outputStream)
                } else {
                    settingsViewModel.showMessage("خطا در باز کردن نویسنده فایل")
                }
            } catch (e: Exception) {
                settingsViewModel.showMessage("خطای ناشناخته در شروع خروجی تفصیلی")
            }
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    backupViewModel.validateAndStageImport(inputStream)
                }
            } catch (e: Exception) {
                settingsViewModel.showMessage("خطا در بارگذاری فایل")
            }
        }
    }

    val operationState by backupViewModel.operationState
    val pendingRestore = backupViewModel.pendingRestoreBackup
    val restoreMode by backupViewModel.selectedRestoreMode

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
        val backup = pendingRestore.value!!
        AlertDialog(
            onDismissRequest = { backupViewModel.cancelPendingRestore() },
            title = {
                Text("بازیابی پشتیبان", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "فایل پشتیبان معتبر است. لطفاً نوع بازیابی را انتخاب کنید:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "تراکنش‌ها: ${backup.transactions.size} | وام‌ها: ${backup.loans.size} | اقساط: ${backup.installments.size} | دسته‌بندی‌ها: ${backup.categories.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = restoreMode == RestoreMode.REPLACE,
                            onClick = { backupViewModel.selectedRestoreMode.value = RestoreMode.REPLACE }
                        )
                        Column {
                            Text("جایگزینی کامل", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("تمام داده‌های فعلی حذف و جایگزین می‌شود", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = restoreMode == RestoreMode.MERGE,
                            onClick = { backupViewModel.selectedRestoreMode.value = RestoreMode.MERGE }
                        )
                        Column {
                            Text("ادغام", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("داده‌های جدید به اطلاعات فعلی اضافه می‌شود", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { backupViewModel.executeRestore() },
                    shape = RoundedCornerShape(12.dp),
                    colors = if (restoreMode == RestoreMode.REPLACE)
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else
                        ButtonDefaults.buttonColors()
                ) {
                    Text(
                        if (restoreMode == RestoreMode.REPLACE) "جایگزینی کامل" else "ادغام",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { backupViewModel.cancelPendingRestore() }) {
                    Text("انصراف")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // App branding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "حسابیار هوشمند فارسی",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "نسخه ۱.۰ | توسعه‌دهنده: mcuteangel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // General Settings
        Text(
            text = "⚙️ تنظیمات عمومی",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCategories() }
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("تم تاریک فعال (Dark Theme)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = settingsViewModel.isDarkMode.value,
                        onCheckedChange = { settingsViewModel.toggleDarkMode() },
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("هشدار خودکار اقساط و بدهی‌ها", style = MaterialTheme.typography.bodyMedium)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "روشن / سراسری",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // AI Provider Settings
        Text(
            text = "🤖 تنظیمات هوش مصنوعی (API Settings)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        AiProviderSettingsCard(aiAssistantViewModel = aiAssistantViewModel)

        // Backup
        Text(
            text = "💾 پشتیبان‌گیری و بازیابی داده‌ها (آفلاین)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "جهت جلوگیری از دست رفتن امور مالی خود، به صورت دوره‌ای اقدام به تهیه‌ی پشتیبان بفرمایید. فایل خروجی به شکل استاندارد JSON در حافظه ذخیره می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    lineHeight = 18.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { importFileLauncher.launch("application/json") },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("restore_button"),
                        shape = RoundedCornerShape(12.dp),
                        enabled = operationState !is BackupOperationState.Importing
                    ) {
                        if (operationState is BackupOperationState.Importing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(imageVector = Icons.Filled.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("بازیابی پشتیبان", style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = { exportFileLauncher.launch("hesabyar_backup_${System.currentTimeMillis() / 1000}.json") },
                        modifier = Modifier.weight(1.1f).height(48.dp).testTag("backup_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = operationState !is BackupOperationState.Exporting
                    ) {
                        if (operationState is BackupOperationState.Exporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("ذخیره فایل پشتیبان", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Debug Logs Section
        DebugLogsSection()
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
        text = "🔍 لاگ‌های دیباگ (Debug Logs)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    IconButton(onClick = { AppLogger.clear(); logs = emptyList() }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "پاک کردن",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = autoRefresh,
                    onCheckedChange = { autoRefresh = it },
                    modifier = Modifier.testTag("auto_refresh_switch")
                )
                Text(
                    text = if (autoRefresh) "بروزرسانی خودکار (هر ۱ ثانیه)" else "بروزرسانی خودکار غیرفعال",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "هنوز لاگی ثبت نشده است.\nیک عملیات AI انجام دهید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    logs.reversed().forEach { entry ->
                        val levelColor = when (entry.level) {
                            "E" -> MaterialTheme.colorScheme.error
                            "W" -> MaterialTheme.colorScheme.tertiary
                            "I" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        }
                        Text(
                            text = entry.formatted(),
                            style = MaterialTheme.typography.bodySmall,
                            color = levelColor,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Online/Offline Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isOnlineMode) Icons.Filled.Cloud else Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = if (isOnlineMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Switch(
                    checked = isOnlineMode,
                    onCheckedChange = { aiAssistantViewModel.toggleOnlineMode() }
                )
            }

            // Saved Configs List
            if (configs.isNotEmpty()) {
                Text(
                    text = "تنظیمات ذخیره شده:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                configs.forEach { config ->
                    val isActive = config.id == activeConfigId
                    ConfigItem(
                        config = config,
                        isActive = isActive,
                        onSelect = { aiAssistantViewModel.setActiveConfig(config.id) },
                        onEdit = { editingConfig = config },
                        onDelete = { aiAssistantViewModel.deleteAiConfig(config.id) }
                    )
                }
            }

            // Add New Config Button
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("افزودن تنظیمات جدید", fontWeight = FontWeight.Bold)
            }
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
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
            .clickable { onSelect() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (isActive) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "ویرایش",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "حذف",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigDialog(
    initialConfig: AiProviderConfig?,
    onDismiss: () -> Unit,
    onSave: (AiProviderConfig) -> Unit,
    onFetchModels: (AiProviderType, String, String?) -> Unit,
    modelFetchState: ModelFetchState,
    onClearModelFetchState: () -> Unit
) {
    val isEditing = initialConfig != null
    var label by remember { mutableStateOf(initialConfig?.label ?: "") }
    var selectedProvider by remember { mutableStateOf(initialConfig?.providerType ?: AiProviderType.GEMINI) }
    var apiKey by remember { mutableStateOf(initialConfig?.apiKey ?: "") }
    var model by remember { mutableStateOf(initialConfig?.model ?: "") }
    var baseUrl by remember { mutableStateOf(initialConfig?.baseUrl ?: "") }
    var showApiKey by remember { mutableStateOf(false) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var modelSearchQuery by remember { mutableStateOf("") }

    val fetchedModels = when (val state = modelFetchState) {
        is ModelFetchState.Success -> state.models
        else -> emptyList()
    }

    val filteredModels = if (modelSearchQuery.isBlank()) fetchedModels
    else fetchedModels.filter { it.contains(modelSearchQuery, ignoreCase = true) }

    val geminiFamilies = fetchedModels.groupBy { model ->
        when {
            model.contains("gemini", ignoreCase = true) -> "Gemini"
            model.contains("gemma", ignoreCase = true) -> "Gemma"
            model.contains("imagen", ignoreCase = true) -> "Imagen"
            else -> "Other"
        }
    }

    val openRouterGroups = fetchedModels.groupBy { model ->
        model.split("/").firstOrNull() ?: "Unknown"
    }

    AlertDialog(
        onDismissRequest = {
            onClearModelFetchState()
            onDismiss()
        },
        title = {
            Text(
                text = if (isEditing) "ویرایش تنظیمات" else "افزودن تنظیمات جدید",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Label
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    label = { Text("نام اختصاصی (اختیاری)") },
                    placeholder = { Text("مثلاً: کلید اصلی Gemini") },
                    singleLine = true
                )

                // Provider Dropdown
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "ارائه‌دهنده:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    ExposedDropdownMenuBox(
                        expanded = providerDropdownExpanded,
                        onExpandedChange = { providerDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedProvider.displayName,
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            readOnly = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerDropdownExpanded) }
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
                                        baseUrl = when (type) {
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
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    label = { Text("کلید API Key") },
                    placeholder = {
                        Text(
                            when (selectedProvider) {
                                AiProviderType.GEMINI -> "AIza..."
                                AiProviderType.OPENROUTER -> "sk-or-..."
                                AiProviderType.CUSTOM -> "your-api-key"
                            }
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showApiKey) "مخفی" else "نمایش"
                            )
                        }
                    },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true
                )

                // Base URL (for OpenRouter and Custom)
                if (selectedProvider != AiProviderType.GEMINI) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        label = { Text("آدرس API (Base URL)") },
                        placeholder = {
                            Text(
                                when (selectedProvider) {
                                    AiProviderType.OPENROUTER -> "https://openrouter.ai/api/v1"
                                    AiProviderType.CUSTOM -> "https://api.example.com/v1"
                                    else -> ""
                                }
                            )
                        },
                        singleLine = true
                    )
                }

                // Fetch Models Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onFetchModels(selectedProvider, apiKey, baseUrl.ifBlank { null }) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = apiKey.isNotBlank() && modelFetchState !is ModelFetchState.Loading
                    ) {
                        if (modelFetchState is ModelFetchState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("دریافت مدل‌ها", fontWeight = FontWeight.Bold)
                    }

                    if (modelFetchState is ModelFetchState.Error) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "انتخاب مدل (${fetchedModels.size} مدل موجود):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        // Search field
                        OutlinedTextField(
                            value = modelSearchQuery,
                            onValueChange = { modelSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            placeholder = { Text("جستجوی مدل...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            singleLine = true
                        )

                        ExposedDropdownMenuBox(
                            expanded = modelDropdownExpanded,
                            onExpandedChange = { modelDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = model,
                                onValueChange = { model = it },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                                shape = RoundedCornerShape(12.dp),
                                placeholder = { Text("نام مدل را تایپ یا انتخاب کنید") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) }
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
                                                    "📁 $family (${models.size})",
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
                                                text = { Text("  ... و ${models.size - 10} مدل دیگر", style = MaterialTheme.typography.bodySmall) },
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
                                                    "📁 $provider (${models.size}${if (freeCount > 0) ", $freeCount رایگان" else ""})",
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
                                                            Spacer(modifier = Modifier.width(4.dp))
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
                                                text = { Text("  ... و ${models.size - 8} مدل دیگر", style = MaterialTheme.typography.bodySmall) },
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
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        label = { Text("نام مدل (Model)") },
                        placeholder = {
                            Text(
                                when (selectedProvider) {
                                    AiProviderType.GEMINI -> "gemini-2.0-flash"
                                    AiProviderType.OPENROUTER -> "google/gemini-2.0-flash-001"
                                    AiProviderType.CUSTOM -> "model-name"
                                }
                            )
                        },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        AiProviderConfig(
                            id = initialConfig?.id ?: "",
                            providerType = selectedProvider,
                            apiKey = apiKey.trim(),
                            model = model.trim(),
                            baseUrl = baseUrl.trim(),
                            label = label.trim()
                        )
                    )
                    onClearModelFetchState()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ذخیره", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onClearModelFetchState()
                    onDismiss()
                }
            ) {
                Text("انصراف")
            }
        }
    )
}
