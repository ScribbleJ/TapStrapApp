package com.scribblej.tapstrapapp.presentation

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment

import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.scribblej.tapstrapapp.CommandList
import com.scribblej.tapstrapapp.MetaKeysManager
import com.scribblej.tapstrapapp.TapCount
import com.scribblej.tapstrapapp.TapPattern
import kotlinx.coroutines.delay

val circlerowpadding = 16.dp
val elementpadding = 0.dp
val circlesize = 30.dp
val textprescaledsize = 20.sp

class TapInputView(context: Context, private val tapInputViewModel: TapInputViewModel) : AbstractComposeView(context) {
    @Composable
    override fun Content() {
        TapStateView(tapInputViewModel)
    }
}

@Preview(showBackground = true, device = Devices.PHONE)
@Composable
fun PreviewTapStateView() {  // TODO: Figure out how to get this Preview rendering with the context etc.
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
    val tapInputViewModel = TapInputViewModel(sharedPreferences)
    tapInputViewModel.setCommandLists(listOf(listOf("foo", "bar", "baz"), listOf("bat", "moo", "lin"), listOf("B")))
    tapInputViewModel.setTapCount(1)
    tapInputViewModel.updateTapPattern(13)
    TapStateView(tapInputViewModel = tapInputViewModel)
}

@Composable
fun TapStateView(tapInputViewModel: TapInputViewModel) {


    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TapPatternIndicator(tapInputViewModel)
            Spacer(modifier = Modifier.width(20.dp))
            MetaKeyIndicators(tapInputViewModel)
        }
        Spacer(modifier = Modifier.height(8.dp)) // Add spacing between rows
        Row {
            CommandListIndicator(tapInputViewModel)
        }
    }
}


@Composable
fun CommandListIndicator(tapInputViewModel: TapInputViewModel) {
    val tapCount by tapInputViewModel.tapCount.collectAsState()
    val commandLists by tapInputViewModel.commandLists.collectAsState()


    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val padding = 16.dp
    val boxSize = (screenWidth - (padding * 2)) / commandLists.size

    val baseFontSize = textprescaledsize // Starting font size
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics
    val boxSizePx = with(LocalDensity.current) { boxSize.toPx() }
    // Simplified scale factor calculation based on max 5 characters
    val scaleFactor = boxSizePx / displayMetrics.density / (baseFontSize.value)

    Row(modifier = Modifier.fillMaxWidth()) {
        commandLists.forEachIndexed { index, commandList ->
            val concatenatedCommands = "☝️".repeat(index+1) + commandList.joinToString(" ")
            var backColor = Color.Red.copy(alpha = 0f)
            if (tapCount == index + 1)
                backColor = Color.Red.copy(alpha = 0.25f)
            Text(
                text = concatenatedCommands,
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = padding)
                    .clickable { tapInputViewModel.commandListClick(commandList) }
                    .background(backColor, shape = CircleShape )
            )
        }
    }
}

@Composable
fun MetaKeyIndicators(tapInputViewModel: TapInputViewModel) {

    var metaKeys by remember { mutableStateOf(0) }
    var modOnce by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val observer: (Int, Boolean) -> Unit = { newMetaKeys, newModOnce ->
            metaKeys = newMetaKeys
            modOnce = newModOnce
        }

        MetaKeysManager.addObserver(observer)
        onDispose { MetaKeysManager.removeObserver(observer) }
    }

    Row(
        // modifier = Modifier.padding(circlerowpadding),
        // horizontalArrangement = Arrangement.SpaceBetween
    ){
        ResponsiveMetaKeyButton("CTRL", KeyEvent.META_CTRL_ON, metaKeys, modOnce)
        ResponsiveMetaKeyButton("SHIFT", KeyEvent.META_SHIFT_ON, metaKeys, modOnce)
        ResponsiveMetaKeyButton("ALT", KeyEvent.META_ALT_ON, metaKeys, modOnce)
        ResponsiveMetaKeyButton("META", KeyEvent.META_META_ON, metaKeys, modOnce)
        ResponsiveSettingsButton()
    }
}




@Composable
fun ResponsiveMetaKeyButton(name: String, keyEvent: Int, metaKeys: Int, modOnce: Boolean, buttonSize: Dp = circlesize) {
    val maxChars = 5
    val baseFontSize = textprescaledsize // Starting font size
    val context = LocalContext.current
    val resources = context.resources
    val displayMetrics = resources.displayMetrics
    val buttonSizePx = with(LocalDensity.current) { buttonSize.toPx() }
    // Simplified scale factor calculation based on max 5 characters
    val scaleFactor = buttonSizePx / displayMetrics.density / (baseFontSize.value * maxChars)

    val purple = Color(0xFF9C2FF0)
    val buttonColor = when {
        metaKeys and keyEvent != 0 && modOnce -> purple
        metaKeys and keyEvent != 0 -> Color.Green
        else -> Color.Gray
    }


    Button(
        modifier = Modifier
            .size(buttonSize)
            .padding(elementpadding),
        onClick = { MetaKeysManager.toggleMetaKey(keyEvent) },
        colors = ButtonDefaults.buttonColors(backgroundColor = buttonColor)

    ) {
        Text(
            text = name,
            fontSize = (baseFontSize * scaleFactor),
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}



@Composable
fun ResponsiveSettingsButton() {
    val maxChars = 5
    val baseFontSize = textprescaledsize // Starting font size
    val context = LocalContext.current
    val resources = context.resources
    val displayMetrics = resources.displayMetrics
    val buttonSizePx = with(LocalDensity.current) { circlesize.toPx() }
    // Simplified scale factor calculation based on max 5 characters
    val scaleFactor = buttonSizePx / displayMetrics.density / (baseFontSize.value * maxChars)

    Button(
        modifier = Modifier
            .size(circlesize)
            .padding(elementpadding),
        onClick = {
            val intent = Intent(context, TapInputSettings::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        },
        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray)

    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Settings"
        )

        /*Text(
            text = "Settings",
            fontSize = (baseFontSize * scaleFactor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )*/
    }
}


@Composable
fun TapPatternIndicator(tapInputViewModel: TapInputViewModel) {

    val tapPattern   by tapInputViewModel.tapPattern.collectAsState()
    val lifetimeTaps by tapInputViewModel.lifetimeTaps.collectAsState()

    val indicatorStates = remember(tapPattern) {
        List(5) { index -> (tapPattern shr index) and 1 == 1 }
    }

    Row(
        //modifier = Modifier.padding(circlerowpadding),
        //horizontalArrangement = Arrangement.EqualWeight
    ) {
        indicatorStates.forEach { state ->
            CircleIndicator(isOn = state, updateSerialNumber = lifetimeTaps)
        }
    }
}



@Composable
fun CircleIndicator(isOn: Boolean, updateSerialNumber: Int) {
    val color = remember { Animatable(Color.Gray) }


    LaunchedEffect(isOn, updateSerialNumber) {
            if (isOn) {
                color.animateTo(Color.Green, animationSpec = tween(25))
                delay(1000) // Delay for 1 second while green
                color.animateTo(Color.Gray, animationSpec = tween(500))
            }
            else
                color.animateTo(Color.Gray, animationSpec = tween(25))
        }

    Box(
        modifier = Modifier
            .size(circlesize) // Example size, adjust as needed
            .background(color.value, shape = CircleShape)
            .padding(elementpadding)
    )
}

/* @Preview(showBackground = true, device = Devices.PHONE)
@Composable
fun PreviewBinaryIndicatorsPhone() {
    //val fvm = FingerViewModel()
    //fvm.setFingers(booleanArrayOf(false, true, true, false, true))
    //BinaryIndicatorsUIWithViewModel(fvm)
    BinaryIndicatorsUI(listOf(false, true, false, false, true))
} */