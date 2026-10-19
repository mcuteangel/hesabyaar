package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens

@Composable
fun HesabyarInputField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  placeholder: String? = null,
  leadingIcon: @Composable (() -> Unit)? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  isError: Boolean = false,
  supportingText: @Composable (() -> Unit)? = null,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  singleLine: Boolean = true,
  shape: Shape = ShapeTokens.Large,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  colors: TextFieldColors? = null
) {
  // Mirror the Material 3 String overload: keep TextFieldValue state so the
  // selection survives recompositions while the caller stays on String state.
  var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = value)) }
  if (value != textFieldValueState.text) {
    // External value change (filter/normalize/reset): map the previous caret
    // through the text change instead of resetting it to offset 0.
    val old = textFieldValueState
    textFieldValueState =
      TextFieldValue(
        text = value,
        selection = remapSelection(old.text, value, old.selection)
      )
  }
  HesabyarInputField(
    value = textFieldValueState,
    onValueChange = { newValue ->
      textFieldValueState = newValue
      onValueChange(newValue.text)
    },
    modifier = modifier,
    label = label,
    placeholder = placeholder,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    isError = isError,
    supportingText = supportingText,
    visualTransformation = visualTransformation,
    keyboardOptions = keyboardOptions,
    singleLine = singleLine,
    shape = shape,
    enabled = enabled,
    readOnly = readOnly,
    colors = colors
  )
}

@Composable
fun HesabyarInputField(
  value: TextFieldValue,
  onValueChange: (TextFieldValue) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  placeholder: String? = null,
  leadingIcon: @Composable (() -> Unit)? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  isError: Boolean = false,
  supportingText: @Composable (() -> Unit)? = null,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  singleLine: Boolean = true,
  shape: Shape = ShapeTokens.Large,
  enabled: Boolean = true,
  readOnly: Boolean = false,
  colors: TextFieldColors? = null
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.fillMaxWidth(),
    label = label?.let { { Text(it) } },
    placeholder =
      placeholder?.let {
        {
          Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium
          )
        }
      },
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    isError = isError,
    supportingText = supportingText,
    visualTransformation = visualTransformation,
    keyboardOptions = keyboardOptions,
    singleLine = singleLine,
    enabled = enabled,
    readOnly = readOnly,
    shape = shape,
    colors = colors ?: defaultInputFieldColors()
  )
}

@Composable
private fun defaultInputFieldColors(): TextFieldColors =
  OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    errorBorderColor = MaterialTheme.colorScheme.error
  )

/**
 * Map a selection from [oldText] onto [newText] when [newText] is provided externally.
 *
 * - 1:1 character normalizations (e.g. Persian digits to ASCII) preserve caret offset.
 * - Modifications where caret sits in an unchanged common prefix preserve caret offset.
 * - Appends keep the caret at the end of the new text.
 * - Unrelated external replacements (voice input, example chips, resets) move the caret to the end.
 */
internal fun remapSelection(
  oldText: String,
  newText: String,
  selection: TextRange
): TextRange =
  when {
    newText.isEmpty() -> TextRange.Zero
    // 1:1 character normalization (e.g. Persian to English digits via filterDigits)
    oldText.length == newText.length ->
      TextRange(
        selection.start.coerceIn(0, newText.length),
        selection.end.coerceIn(0, newText.length)
      )
    // Caret sits at or within the unchanged common prefix boundary
    oldText.commonPrefixWith(newText).length.let { prefixLen ->
      prefixLen > 0 && selection.end <= prefixLen
    } -> selection
    // New text is an append or unrelated external replacement: caret lands at the end
    else -> TextRange(newText.length)
  }
