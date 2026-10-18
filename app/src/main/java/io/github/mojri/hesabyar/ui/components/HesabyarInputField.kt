package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
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
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.fillMaxWidth(),
    label = label?.let { { Text(it) } },
    placeholder = placeholder?.let { { Text(it) } },
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
    placeholder = placeholder?.let { { Text(it) } },
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
