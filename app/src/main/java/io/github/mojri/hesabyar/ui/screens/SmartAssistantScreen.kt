package io.github.mojri.hesabyar.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import java.util.Calendar
import io.github.mojri.hesabyar.api.ParsedResult
import io.github.mojri.hesabyar.data.Category
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.CategoryViewModel
import io.github.mojri.hesabyar.ui.DashboardViewModel
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.ParserUIState
import io.github.mojri.hesabyar.ui.theme.ExpenseRed
import io.github.mojri.hesabyar.ui.theme.IncomeGreen
import io.github.mojri.hesabyar.ui.theme.WarningOrange
import java.util.*

@Composable
fun SmartAssistantScreen(
    aiAssistantViewModel: AiAssistantViewModel,
    categoryViewModel: CategoryViewModel,
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0 = Smart Registration, 1 = Budget Advice
    var inputText by remember { mutableStateOf("") }
    val parserState by aiAssistantViewModel.parserState.collectAsState()
    val categories by categoryViewModel.categories.collectAsState()
    val scrollState = rememberScrollState()

    // Intent-based Speech Recognition Launcher for native Persian voice-to-text
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenTextList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = spokenTextList?.firstOrNull() ?: ""
            if (text.isNotBlank()) {
                inputText = text
                aiAssistantViewModel.parseSmartSentence(text, aiAssistantViewModel.isOnlineMode.value)
            }
        }
    }

    fun startVoiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "صحبت کنید (مثلا: امروز مرغ خریدم ۴۵۰ هزار تومن)")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            settingsViewModel.showMessage("سیستم گفتار به نوشتار در دستگاه شما در دسترس نیست")
        }
    }

    if (parserState is ParserUIState.Confirming) {
        val confirmingState = parserState as ParserUIState.Confirming
        ConfirmationDialog(
            result = confirmingState.result,
            onConfirm = { 
                aiAssistantViewModel.confirmParsedResult(confirmingState.result)
            },
            onCancel = { aiAssistantViewModel.clearParserState() }
        )
    } else if (parserState is ParserUIState.Success) {
        val successState = parserState as ParserUIState.Success
        ParsedResultCard(
            result = successState.result,
            categories = categories,
            onApprove = { updatedResult, approvedTimestamp ->
                aiAssistantViewModel.approveParsedResult(updatedResult, approvedTimestamp)
                inputText = "" // clear input on success
            },
            onCancel = { aiAssistantViewModel.clearParserState() },
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        )
    } else {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Material 3 Responsive Tab Selector
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("ثبت هوشمند تراکنش", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium) },
                    icon = { Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("مشاوره و تحلیل بودجه", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium) },
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
            }

            if (activeTab == 0) {
                // Main Transaction Entry Scrollable Form Screen
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // AI Title / Visual Identity Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "دستیار صوتی و متنی هوشمند",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "جمله خود را به زبان فارسی ساده تایپ کنید یا بگویید تا هوش مصنوعی آن را تحلیل کند.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    // Suggested Examples Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "💡 عبارت‌های نمونه جهت آزمایش:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val examples = listOf(
                                "۵۰۰ هزار تومن بابت برق",
                                "امروز حقوق گرفتم ۲۰ میلیون",
                                "به علی ۲ میلیون قرض دادم",
                                "از رضا ۳ میلیون طلب دارم",
                                "قسط وام مسکن رو پرداخت کردم"
                            )
                            examples.forEach { ex ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .clickable {
                                            inputText = ex
                                            aiAssistantViewModel.parseSmartSentence(ex, aiAssistantViewModel.isOnlineMode.value)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ex,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ArrowBack, // using arrow back pointing left for Persian RTL flow triggers
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Input Area
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        "عبارت مالی خود را اینجا بنویسید...",
                                        fontSize = 14.sp
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .testTag("sentence_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Voice Mic button (Aggressive support for speech)
                                IconButton(
                                    onClick = { startVoiceInput() },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                        .testTag("voice_input_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = "ثبت صوتی",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Main Text Parse Submit Button
                                Button(
                                    onClick = {
                                        if (inputText.isNotBlank()) {
                                            aiAssistantViewModel.parseSmartSentence(inputText, aiAssistantViewModel.isOnlineMode.value)
                                        } else {
                                            settingsViewModel.showMessage("لطفا متنی را جهت تحلیل وارد کنید")
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("submit_assistant_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.NavigateNext,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "آماده‌سازی تراکنش با هوش مصنوعی",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Parser State Outputs (Interactive Results Card)
                    AnimatedContent(
                        targetState = parserState,
                        label = "ParserState"
                    ) { state ->
                        when (state) {
                            is ParserUIState.Idle -> {
                                // Say nothing or idle tip
                            }
                            is ParserUIState.Loading -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "درحال تحلیل ساختاری توسط حسابیار هوشمند...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            is ParserUIState.Error -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Text(
                                        text = state.message + "\nتلاش برای استخراج خودکار انجام نشد. لطفا واضح‌تر وارد کنید.",
                                        modifier = Modifier.padding(16.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            is ParserUIState.Confirming -> {
                                // Handled at top-level
                            }
                            is ParserUIState.Success -> {
                                // Handled at top-level
                            }
                        }
                    }
                }
            } else {
                // Budget Advice Tab (Gemini/Offline)
                val advisorState by aiAssistantViewModel.advisorState.collectAsState()
                val isApiKeyReady = aiAssistantViewModel.isAiConfigured()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Advice Header Banner
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TipsAndUpdates,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "مشاور مدیریت هوشمند بودجه",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isApiKeyReady) "طراحی شده با مدل هوش مصنوعی ابری (${aiAssistantViewModel.getProviderStatusText()})" else "طراحی شده با مدل تحلیل هوش مالی محلی (آفلاین)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Main recommendations body state machine
                    when (val state = advisorState) {
                        is io.github.mojri.hesabyar.ui.AdvisorUIState.Idle -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Analytics,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    Text(
                                        text = "آماده دریافت توصیه‌های مالی هستید؟",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )

                                    Text(
                                        text = "دستیار مالی حسابیار با پایش ریز تراکنش‌ها، مبالغ اقساط و روند تراز دریافتی و پرداختی‌های شما، بهترین نکات کلیدی کاهش مخارج و بهبود نرخ ثروت‌آفرینی را ارزیابی می‌کند.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp
                                    )

                                    Button(
                                        onClick = { aiAssistantViewModel.fetchBudgetAdvice(dashboardViewModel.transactions.value, dashboardViewModel.categories.value, aiAssistantViewModel.isOnlineMode.value) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("تحلیل هوشمند بودجه و مخارج", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        is io.github.mojri.hesabyar.ui.AdvisorUIState.Loading -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "حسابیار با طعم هوش مصنوعی...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "در حال تجمیع اطلاعات حساب‌ها، مخارج جاری، تراز اقساط و کشف الگوهای مخارج پنهان شما برای نگارش گزارش مشورتی شخصی...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                        is io.github.mojri.hesabyar.ui.AdvisorUIState.Success -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "💡 نتایج مشاوره و توصیه‌های بودجه",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        IconButton(onClick = { aiAssistantViewModel.fetchBudgetAdvice(dashboardViewModel.transactions.value, dashboardViewModel.categories.value, aiAssistantViewModel.isOnlineMode.value, forceRefresh = true) }) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = "بروزرسانی گزارش",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))

                                    MarkdownText(text = state.advice)

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = { aiAssistantViewModel.fetchBudgetAdvice(dashboardViewModel.transactions.value, dashboardViewModel.categories.value, aiAssistantViewModel.isOnlineMode.value, forceRefresh = true) },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("به‌روزرسانی مشاوره", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        is io.github.mojri.hesabyar.ui.AdvisorUIState.Error -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = "خطا در تنظیم و ارزیابی عادات مالی",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = { aiAssistantViewModel.fetchBudgetAdvice(dashboardViewModel.transactions.value, dashboardViewModel.categories.value, aiAssistantViewModel.isOnlineMode.value, forceRefresh = true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("تلاش مجدد", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val lines = text.split("\n")
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
            } else if (trimmed.startsWith("###")) {
                val headerText = trimmed.removePrefix("###").trim()
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            } else if (trimmed.startsWith("##")) {
                val headerText = trimmed.removePrefix("##").trim()
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
            } else if (trimmed.startsWith("#")) {
                val headerText = trimmed.removePrefix("#").trim()
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )
            } else if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                val itemText = trimmed.removePrefix("-").removePrefix("*").trim()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = parseBoldMarkdown(itemText),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                }
            } else {
                Text(
                    text = parseBoldMarkdown(trimmed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

fun parseBoldMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    val parts = text.split("**")
    for (i in parts.indices) {
        if (i % 2 == 1) {
            builder.pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
            builder.append(parts[i])
            builder.pop()
        } else {
            builder.append(parts[i])
        }
    }
    return builder.toAnnotatedString()
}

@Composable
fun ParsedResultCard(
    result: ParsedResult,
    categories: List<Category>,
    onApprove: (ParsedResult, Long) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // States for interactive editing
    var amountText by remember(result) { 
        mutableStateOf((result.amount / 1000).toString())
    }
    var descriptionText by remember(result) { mutableStateOf(result.description) }
    var selectedType by remember(result) { mutableStateOf(result.type) }
    var selectedCategoryKey by remember(result) { mutableStateOf(result.category) }
    var personNameText by remember(result) { mutableStateOf(result.personName ?: "") }
    var titleText by remember(result) { mutableStateOf(result.title ?: "") }
    var daysFromNowText by remember(result) { mutableStateOf(result.daysFromNow?.toString() ?: "30") }
    
    var customDate by remember(result) {
        val finalCal = Calendar.getInstance()
        val offsetDays = if (result.daysFromNow != null && result.daysFromNow != 0) {
            result.daysFromNow
        } else {
            result.dateOffsetDays ?: 0
        }
        finalCal.add(Calendar.DAY_OF_YEAR, offsetDays)
        result.hour?.let { h ->
            finalCal.set(Calendar.HOUR_OF_DAY, h)
        }
        result.minute?.let { m ->
            finalCal.set(Calendar.MINUTE, m)
        }
        mutableStateOf(finalCal.timeInMillis)
    }
    
    val filteredCategories = categories.filter { cat ->
        when (selectedType) {
            "INCOME" -> cat.type == "INCOME" || cat.type == "BOTH"
            "EXPENSE" -> cat.type == "EXPENSE" || cat.type == "BOTH"
            else -> cat.key == "Loans" || cat.key == "Installments" || cat.key == "Other"
        }
    }
    
    val typeColor = when (selectedType) {
        "INCOME", "LOAN_DEBTOR" -> IncomeGreen
        "EXPENSE", "LOAN_CREDITOR" -> ExpenseRed
        else -> WarningOrange
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✏️ ویرایش و تایید نهایی تراکنش",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "انصراف",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Confidence Score Display
            val confidenceColor = when {
                result.confidence >= 0.9f -> IncomeGreen
                result.confidence >= 0.7f -> WarningOrange
                else -> ExpenseRed
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = confidenceColor.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اطمینان تحلیل:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(result.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = confidenceColor
                    )
                }
            }
            
            // Notes Display (if exists)
            if (!result.notes.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = result.notes,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            // Scrollable Form Content Container
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Transaction Type Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                    text = "نوع تراکنش شناسایی شده:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val types = listOf(
                        Pair("EXPENSE", "هزینه"),
                        Pair("INCOME", "درآمد"),
                        Pair("LOAN_DEBTOR", "طلب (قرض دادم)"),
                        Pair("LOAN_CREDITOR", "بدهی (قرض گرفتم)"),
                        Pair("INSTALLMENT", "قسط")
                    )
                    types.forEach { (typeKey, typeLabel) ->
                        val isSelected = selectedType == typeKey
                        val chipColor = when (typeKey) {
                            "INCOME", "LOAN_DEBTOR" -> IncomeGreen
                            "EXPENSE", "LOAN_CREDITOR" -> ExpenseRed
                            else -> WarningOrange
                        }
                        CustomChip(
                            text = typeLabel,
                            isSelected = isSelected,
                            onClick = { 
                                selectedType = typeKey
                                // Auto-assign logical category based on type
                                selectedCategoryKey = when (typeKey) {
                                    "INCOME" -> "Income"
                                    "LOAN_DEBTOR", "LOAN_CREDITOR" -> "Loans"
                                    "INSTALLMENT" -> "Installments"
                                    else -> selectedCategoryKey
                                }
                            },
                            selectedColor = chipColor,
                            onSelectedColor = Color.White
                        )
                    }
                }
            }

            // 2. Amount Input Field
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "مبلغ استخراج شده (تومان):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parsed_amount_input"),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.AttachMoney,
                            contentDescription = null,
                            tint = typeColor
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = typeColor,
                        focusedLabelColor = typeColor
                    )
                )
                // Formatted Amount preview in Persian words
                val amtToman = amountText.toLongOrNull() ?: 0L
                if (amtToman > 0L) {
                    val amtRial = amtToman * 1000L
                    Text(
                        text = "معادل: ${formatToman(amtRial)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = typeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            // 3. Category Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "دسته‌بندی مربوطه:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filteredCategories.forEach { cat ->
                        val isSelected = selectedCategoryKey == cat.key
                        CustomChip(
                            text = cat.name,
                            isSelected = isSelected,
                            onClick = { selectedCategoryKey = cat.key }
                        )
                    }
                }
            }

            // 4. Description Field
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "شرح یا توضیح تراکنش:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parsed_description_input"),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true
                )
            }

            // Shamsi Date & Time Picker
            JalaliDateTimePicker(
                initialTimestamp = customDate,
                onTimestampChanged = { customDate = it }
            )

            // 5. Conditional Person Name Field (Loans)
            if (selectedType == "LOAN_DEBTOR" || selectedType == "LOAN_CREDITOR") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "طرف حساب (شخص مربوطه):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    OutlinedTextField(
                        value = personNameText,
                        onValueChange = { personNameText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parsed_person_input"),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        placeholder = { Text("مثلا: علی محمودی", style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true
                    )
                }
            }

            // 6. Conditional Installment Fields
            if (selectedType == "INSTALLMENT") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "عنوان قسط:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        OutlinedTextField(
                            value = titleText,
                            onValueChange = { titleText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("parsed_title_input"),
                            shape = RoundedCornerShape(12.dp),
                            placeholder = { Text("مثلا: قسط بانک مسکن", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "فاصله تا موعد پرداخت (روز):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        OutlinedTextField(
                            value = daysFromNowText,
                            onValueChange = { daysFromNowText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("parsed_days_input"),
                            shape = RoundedCornerShape(12.dp),
                            placeholder = { Text("پیش‌فرض ۳۰ روز دیگر", style = MaterialTheme.typography.bodyMedium) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
            }

            } // Cerrar Column

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("انصراف")
                }

                Button(
                    onClick = {
                        val finalAmountToman = amountText.toLongOrNull() ?: 0L
                        if (finalAmountToman <= 0L) {
                            android.widget.Toast.makeText(context, "لطفا مبلغ معتبر و بزرگتر از صفر وارد کنید", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val finalDaysFromNow = if (selectedType == "INSTALLMENT") daysFromNowText.toIntOrNull() else null
                        val finalPersonName = if (selectedType == "LOAN_DEBTOR" || selectedType == "LOAN_CREDITOR") personNameText.trim() else null
                        val finalTitle = if (selectedType == "INSTALLMENT") titleText.trim() else null

                        val updatedResult = ParsedResult(
                            type = selectedType,
                            amount = finalAmountToman * 1000L,
                            category = selectedCategoryKey,
                            personName = if (finalPersonName.isNullOrBlank()) null else finalPersonName,
                            description = descriptionText.ifBlank { "ثبت دستیار هوشمند" },
                            daysFromNow = finalDaysFromNow,
                            title = if (finalTitle.isNullOrBlank()) null else finalTitle
                        )
                        
                        onApprove(updatedResult, customDate)
                    },
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = typeColor)
                ) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ذخیره و ثبت نهایی", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CustomChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    onSelectedColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val bgColor = if (isSelected) selectedColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val textColor = if (isSelected) onSelectedColor else MaterialTheme.colorScheme.onSurfaceVariant
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun ConfirmationDialog(
    result: ParsedResult,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val confidenceColor = when {
        result.confidence >= 0.9f -> IncomeGreen
        result.confidence >= 0.7f -> WarningOrange
        else -> ExpenseRed
    }
    
    val typeLabel = when (result.type) {
        "EXPENSE" -> "هزینه"
        "INCOME" -> "درآمد"
        "LOAN_DEBTOR" -> "طلب (قرض دادم)"
        "LOAN_CREDITOR" -> "بدهی (قرض گرفتم)"
        "INSTALLMENT" -> "قسط"
        else -> result.type
    }
    
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "تایید تراکنش",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Confidence Score
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = confidenceColor.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "اطمینان:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(result.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = confidenceColor
                        )
                    }
                }
                
                // Transaction Type
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("نوع:", style = MaterialTheme.typography.bodyMedium)
                    Text(typeLabel, fontWeight = FontWeight.Bold)
                }
                
                // Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("مبلغ:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = formatToman(result.amount),
                        fontWeight = FontWeight.Bold,
                        color = if (result.type == "INCOME") IncomeGreen else ExpenseRed
                    )
                }
                
                // Category
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("دسته:", style = MaterialTheme.typography.bodyMedium)
                    Text(result.category, fontWeight = FontWeight.Bold)
                }
                
                // Description
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("شرح:", style = MaterialTheme.typography.bodyMedium)
                    Text(result.description, fontWeight = FontWeight.Bold)
                }
                
                // Person Name (if exists)
                if (!result.personName.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("شخص:", style = MaterialTheme.typography.bodyMedium)
                        Text(result.personName, fontWeight = FontWeight.Bold)
                    }
                }
                
                // Notes (if exists)
                if (!result.notes.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = result.notes,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                // Warning for low confidence
                if (result.confidence < 0.7f) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.1f))
                    ) {
                        Text(
                            text = "⚠️ اطمینان پایین است. لطفاً اطلاعات را بررسی کنید.",
                            modifier = Modifier.padding(8.dp),
                            color = ExpenseRed,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen)
            ) {
                Text("تایید و ادامه", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("انصراف")
            }
        }
    )
}
