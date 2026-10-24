package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

private val NUMBERED_LIST_REGEX = Regex("""^(\d+)[.)]\s+(.*)""")

/**
 * Lightweight in-house Markdown text renderer for AI advice and analytical summaries.
 * Supports headers (`#`, `##`, `###`), bold formatting (`**`), bullet lists (`-`, `*`),
 * and numbered lists (`1. `, `1) `).
 */
@Composable
fun MarkdownText(
  text: String,
  modifier: Modifier = Modifier,
  textStyle: TextStyle = LocalTextStyle.current,
  textColor: Color = Color.Unspecified
) {
  val lines = text.split("\n")
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
  ) {
    lines.forEach { line ->
      MarkdownLine(
        line = line,
        textStyle = textStyle,
        textColor = textColor
      )
    }
  }
}

@Composable
private fun MarkdownLine(
  line: String,
  textStyle: TextStyle,
  textColor: Color
) {
  val trimmed = line.trim()
  if (trimmed.isEmpty()) {
    Spacer(modifier = Modifier.height(SpacingTokens.xs))
    return
  }

  val resolvedColor = if (textColor != Color.Unspecified) textColor else MaterialTheme.colorScheme.onSurface

  when {
    trimmed.startsWith("###") -> {
      HeaderLine(
        text = trimmed.removePrefix("###").trim(),
        style = MaterialTheme.typography.titleMedium,
        topPadding = SpacingTokens.sm,
        bottomPadding = 2.dp
      )
    }
    trimmed.startsWith("##") -> {
      HeaderLine(
        text = trimmed.removePrefix("##").trim(),
        style = MaterialTheme.typography.titleLarge,
        topPadding = SpacingTokens.md,
        bottomPadding = SpacingTokens.xs
      )
    }
    trimmed.startsWith("#") -> {
      HeaderLine(
        text = trimmed.removePrefix("#").trim(),
        style = MaterialTheme.typography.headlineSmall,
        topPadding = SpacingTokens.md,
        bottomPadding = SpacingTokens.sm
      )
    }
    trimmed.startsWith("-") || trimmed.startsWith("*") -> {
      val itemText = trimmed.removePrefix("-").removePrefix("*").trim()
      ListItemRow(
        prefix = "•",
        itemText = itemText,
        textStyle = textStyle,
        textColor = resolvedColor
      )
    }
    else -> {
      RenderNumberedOrRegularText(
        trimmed = trimmed,
        textStyle = textStyle,
        textColor = resolvedColor
      )
    }
  }
}

@Composable
private fun RenderNumberedOrRegularText(
  trimmed: String,
  textStyle: TextStyle,
  textColor: Color
) {
  val numberedMatch = NUMBERED_LIST_REGEX.find(trimmed)
  if (numberedMatch != null) {
    val number = numberedMatch.groupValues[1]
    val itemText = numberedMatch.groupValues[2]
    ListItemRow(
      prefix = "$number.",
      itemText = itemText,
      textStyle = textStyle,
      textColor = textColor
    )
  } else {
    Text(
      text = parseBoldMarkdown(trimmed),
      style = textStyle,
      color = textColor,
      textAlign = TextAlign.Start,
      lineHeight = 22.sp
    )
  }
}

@Composable
private fun HeaderLine(
  text: String,
  style: TextStyle,
  topPadding: Dp,
  bottomPadding: Dp
) {
  Text(
    text = text,
    style = style,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary,
    textAlign = TextAlign.Start,
    modifier =
      Modifier
        .semantics { heading() }
        .padding(top = topPadding, bottom = bottomPadding)
  )
}

@Composable
private fun ListItemRow(
  prefix: String,
  itemText: String,
  textStyle: TextStyle,
  textColor: Color
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = SpacingTokens.xs),
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
    verticalAlignment = Alignment.Top
  ) {
    Text(
      text = prefix,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      text = parseBoldMarkdown(itemText),
      style = textStyle,
      color = textColor,
      textAlign = TextAlign.Start,
      lineHeight = 22.sp
    )
  }
}

/**
 * Parses `**bold text**` segments into an [AnnotatedString] with [FontWeight.Bold].
 */
fun parseBoldMarkdown(text: String): AnnotatedString {
  val builder = AnnotatedString.Builder()
  val parts = text.split("**")
  for (i in parts.indices) {
    if (i % 2 == 1) {
      builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
      builder.append(parts[i])
      builder.pop()
    } else {
      builder.append(parts[i])
    }
  }
  return builder.toAnnotatedString()
}
