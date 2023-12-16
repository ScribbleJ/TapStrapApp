package com.scribblej.tapstrapapp.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment

import androidx.wear.compose.material.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.scribblej.tapstrapapp.presentation.theme.BasicTheme
import androidx.wear.compose.material.Text

import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSlider

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.InlineSliderDefaults

import com.scribblej.tapstrapapp.getAppSpecificExternalDirPath
import com.scribblej.tapstrapapp.listCsvFiles
import com.scribblej.tapstrapapp.BuildConfig


class TapInputSettings : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BasicTheme {
                SettingsScreenWrapper()
            }
        }
    }
}


@Preview
@Composable
fun SettingsScreenWrapper() {
    // You can add any additional setup or logic here if needed
    SettingsScreen()
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    val loopMultiTaps    = remember { mutableStateOf(preferences.getBoolean("loop_multi_taps", true)) }
    val multiTapTimeout  = remember { mutableStateOf(preferences.getInt("multi_tap_timeout", 300)) }

    Column(
    ) {

        Text("The directory where we expect to find your configuration file(s) is: " + getAppSpecificExternalDirPath(context))

        Spacer(modifier = Modifier.height(16.dp))

        Text("We will attempt to load these detected CSV files as tap maps:")

        listCsvFiles(getAppSpecificExternalDirPath(context)).forEach { file ->
            Text(text = file.name)
            // Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Double Tap Timeout")

        IntegerValueSelector (initialValue = multiTapTimeout.value) { newValue ->
            preferences.edit().putInt("multi_tap_timeout", newValue).apply()
            multiTapTimeout.value = newValue
        }

        Spacer(modifier = Modifier.height(16.dp))

        SwitchWithLabel(
                label = "Loop Multi-Taps",
                isChecked = loopMultiTaps.value,
                onCheckedChange = { newValue ->
                    preferences.edit().putBoolean("loop_multi_taps", newValue).apply()
                    loopMultiTaps.value = newValue
                }
        )
        // TODO: Make this more-seperater
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Version: ${BuildConfig.VERSION_NAME}")

    }
}


@Composable
fun IntegerValueSelector(
    range: IntRange = 0..1000,
    initialValue: Int = 300,
    onValueChange: (Int) -> Unit
) {

    val sliderPosition = remember { mutableStateOf(initialValue) }

    InlineSlider(
        value = sliderPosition.value,
        onValueChange = { newValue ->
            sliderPosition.value = newValue
            onValueChange(newValue)
        },
        valueProgression = range step 100,
        decreaseIcon = { InlineSliderDefaults.Decrease }, // Replace with your icon
        increaseIcon = { InlineSliderDefaults.Increase }, // Replace with your icon
        modifier = Modifier,
        enabled = true,
        segmented = true,
        colors = InlineSliderDefaults.colors()
    )
}


@Composable
fun SwitchWithLabel(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    checkedColor: Color = Color.Green,
    uncheckedColor: Color = Color.Red,
    checkedIcon: ImageVector = Icons.Default.Check,
    uncheckedIcon: ImageVector = Icons.Default.Close
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (isChecked) checkedIcon else uncheckedIcon,
            contentDescription = null, // decorative element
            tint = if (isChecked) checkedColor else uncheckedColor
        )
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = checkedColor,
                uncheckedThumbColor = uncheckedColor
            )
        )
    }
}

