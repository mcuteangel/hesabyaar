package io.github.mojri.hesabyar.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import io.github.mojri.hesabyar.ui.HesabyarViewModel
import java.io.InputStream
import java.io.OutputStream

@Composable
fun SettingsScreen(
    viewModel: HesabyarViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Activity Result Launcher for choosing where to export/save backup
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    viewModel.exportBackupToFile(outputStream)
                } else {
                    viewModel.showMessage("خطا در باز کردن نویسنده فایل")
                }
            } catch (e: Exception) {
                viewModel.showMessage("خطای ناشناخته در شروع خروجی تفصیلی")
            }
        }
    }

    // Activity Result Launcher for opening/choosing backup to restore
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    viewModel.importBackupFromFile(inputStream)
                } else {
                    viewModel.showMessage("فایل قابل دریافت اطلاعات نیست")
                }
            } catch (e: Exception) {
                viewModel.showMessage("خطا در بارگذاری فایل")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // App settings branding profile
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

                Text(
                    text = "Package: io.github.mojri.hesabyar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }

        Text(
            text = "⚙️ تنظیمات عمومی",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Theme and Options card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dark Mode Switch
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
                        checked = viewModel.isDarkMode.value,
                        onCheckedChange = { viewModel.toggleDarkMode() },
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Notifications Status
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

        // AI Provider Settings Section
        Text(
            text = "🤖 تنظیمات هوش مصنوعی (API Settings)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        AiProviderSettingsCard(viewModel = viewModel)

        Text(
            text = "💾 پشتیبان‌گیری و بازیابی داده‌ها (آفلاین)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Backup and Restore Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Description
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
                    // Import Button
                    OutlinedButton(
                        onClick = { importFileLauncher.launch("application/json") },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("restore_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("بازیابی پشتیبان", style = MaterialTheme.typography.labelSmall)
                    }

                    // Export Button
                    Button(
                        onClick = { exportFileLauncher.launch("hesabyar_backup_${System.currentTimeMillis() / 1000}.json") },
                        modifier = Modifier
                            .weight(1.1f)
                            .height(48.dp)
                            .testTag("backup_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ذخیره فایل پشتیبان", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun AiProviderSettingsCard(viewModel: HesabyarViewModel) {
    val currentConfig by viewModel.aiProviderConfig
    var selectedProvider by remember { mutableStateOf(currentConfig.providerType) }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var model by remember { mutableStateOf(currentConfig.model) }
    var baseUrl by remember { mutableStateOf(currentConfig.baseUrl) }
    var showApiKey by remember { mutableStateOf(false) }

    val defaultModels = mapOf(
        AiProviderType.GEMINI to listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash", "gemini-1.5-pro"),
        AiProviderType.OPENROUTER to listOf("google/gemini-2.0-flash-001", "anthropic/claude-3.5-sonnet", "openai/gpt-4o-mini", "meta-llama/llama-3.1-8b-instruct:free"),
        AiProviderType.CUSTOM to emptyList()
    )

    val isConfigured = currentConfig.isConfigured

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isConfigured) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isConfigured) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = if (isConfigured) "هوش مصنوعی فعال" else "هوش مصنوعی غیرفعال",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = viewModel.getProviderStatusText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // Provider Type Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "ارائه‌دهنده هوش مصنوعی:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiProviderType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedProvider == type,
                            onClick = {
                                selectedProvider = type
                                model = defaultModels[type]?.firstOrNull() ?: ""
                                baseUrl = when (type) {
                                    AiProviderType.OPENROUTER -> "https://openrouter.ai/api/v1"
                                    AiProviderType.CUSTOM -> ""
                                    AiProviderType.GEMINI -> ""
                                }
                            },
                            label = { Text(type.displayName, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (selectedProvider == type) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // API Key Input
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "کلید API Key:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(
                            when (selectedProvider) {
                                AiProviderType.GEMINI -> "AIza..."
                                AiProviderType.OPENROUTER -> "sk-or-..."
                                AiProviderType.CUSTOM -> "your-api-key"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showApiKey) "مخفی کردن" else "نمایش"
                            )
                        }
                    },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true
                )
            }

            // Model Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "مدل (Model):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (selectedProvider != AiProviderType.CUSTOM) {
                    val models = defaultModels[selectedProvider] ?: emptyList()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        models.forEach { m ->
                            FilterChip(
                                selected = model == m,
                                onClick = { model = m },
                                label = { Text(m, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(
                            when (selectedProvider) {
                                AiProviderType.GEMINI -> "gemini-2.0-flash"
                                AiProviderType.OPENROUTER -> "google/gemini-2.0-flash-001"
                                AiProviderType.CUSTOM -> "model-name"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true
                )
            }

            // Base URL (only for OpenRouter and Custom)
            if (selectedProvider != AiProviderType.GEMINI) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "آدرس API (Base URL):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = {
                            Text(
                                when (selectedProvider) {
                                    AiProviderType.OPENROUTER -> "https://openrouter.ai/api/v1"
                                    AiProviderType.CUSTOM -> "https://api.example.com/v1"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true
                    )
                }
            }

            // Save Button
            Button(
                onClick = {
                    viewModel.updateAiProviderConfig(
                        AiProviderConfig(
                            providerType = selectedProvider,
                            apiKey = apiKey.trim(),
                            model = model.trim(),
                            baseUrl = baseUrl.trim()
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ذخیره تنظیمات", fontWeight = FontWeight.Bold)
            }
        }
    }
}
