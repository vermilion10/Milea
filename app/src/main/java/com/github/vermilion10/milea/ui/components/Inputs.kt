package com.github.vermilion10.milea.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Keeps only digits and a single decimal point. Both "," and "." are accepted
 * as the decimal key, since keyboards differ by locale.
 */
fun sanitizeDecimal(input: String, maxDecimals: Int = 3): String {
    val builder = StringBuilder()
    var seenPoint = false
    var decimals = 0
    for (c in input) {
        when {
            c.isDigit() -> {
                if (seenPoint) {
                    if (decimals >= maxDecimals) continue
                    decimals++
                }
                builder.append(c)
            }
            (c == '.' || c == ',') && !seenPoint && maxDecimals > 0 -> {
                seenPoint = true
                builder.append('.')
            }
        }
    }
    return builder.toString()
}

fun sanitizeInteger(input: String): String = input.filter { it.isDigit() }.take(9)

/** Plain "1234.5" representation for prefilling fields from stored numbers. */
fun Float.toInputString(maxDecimals: Int = 2): String {
    val rounded = String.format(Locale.US, "%.${maxDecimals}f", this)
    return if (rounded.contains('.')) rounded.trimEnd('0').trimEnd('.') else rounded
}

/**
 * Shows "1234567.5" as "1,234,567.5" (using the locale's separators) while the
 * underlying value stays plain, so parsing never has to guess.
 */
class GroupedNumberTransformation : VisualTransformation {
    private val symbols = DecimalFormatSymbols.getInstance(Locale.getDefault())

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val pointIndex = raw.indexOf('.').takeIf { it >= 0 } ?: raw.length
        val intPart = raw.substring(0, pointIndex)
        val out = StringBuilder()
        // originalToTransformed[i] = index in output for input index i
        val o2t = IntArray(raw.length + 1)
        intPart.forEachIndexed { i, c ->
            val fromEnd = intPart.length - i
            if (i > 0 && fromEnd % 3 == 0) out.append(symbols.groupingSeparator)
            o2t[i] = out.length
            out.append(c)
        }
        for (i in pointIndex until raw.length) {
            o2t[i] = out.length
            out.append(if (raw[i] == '.') symbols.decimalSeparator else raw[i])
        }
        o2t[raw.length] = out.length
        // A cursor at transformed offset t sits after every original char drawn before t.
        val t2o = IntArray(out.length + 1) { t -> (0 until raw.length).count { o2t[it] < t } }
        return TransformedText(
            AnnotatedString(out.toString()),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = o2t[offset.coerceIn(0, raw.length)]
                override fun transformedToOriginal(offset: Int) = t2o[offset.coerceIn(0, out.length)]
            }
        )
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimals: Int = 2,
    prefix: String? = null,
    suffix: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            onValueChange(if (decimals == 0) sanitizeInteger(it) else sanitizeDecimal(it, decimals))
        },
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        visualTransformation = GroupedNumberTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimals == 0) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = imeAction
        ),
        modifier = modifier
    )
}

/**
 * Date picker that keeps the time of day from [initial] (or now), so entries
 * made on the same day still sort in the order they were logged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeKeepingPicker(
    initial: Long,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit
) {
    val local = Calendar.getInstance().apply { timeInMillis = initial }
    val utcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
    val state = rememberDatePickerState(
        initialSelectedDateMillis = utcMidnight,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                utcTimeMillis <= System.currentTimeMillis()
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { picked ->
                    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = picked }
                    val result = Calendar.getInstance().apply {
                        timeInMillis = initial
                        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
                    }
                    onPicked(minOf(result.timeInMillis, System.currentTimeMillis()))
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}
