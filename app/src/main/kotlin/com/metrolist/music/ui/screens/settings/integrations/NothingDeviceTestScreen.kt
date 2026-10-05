package com.metrolist.music.ui.screens.settings.integrations

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.nothing.NothingBackend
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain

private data class TestCommand(val label: Int, val code: Int, val payload: ByteArray = byteArrayOf())

private val testCommands = listOf(
    TestCommand(R.string.nothing_command_battery, 0xc007),
    TestCommand(R.string.nothing_command_firmware, 0xc042),
    TestCommand(R.string.nothing_command_serial, 0xc006),
    TestCommand(R.string.nothing_command_anc, 0xc01e),
    TestCommand(R.string.nothing_command_eq, 0xc01f),
    TestCommand(R.string.nothing_command_gestures, 0xc018),
    TestCommand(R.string.nothing_command_latency, 0xc041),
    TestCommand(R.string.nothing_command_anc_off, 0xf00f, byteArrayOf(1, 5, 0)),
    TestCommand(R.string.nothing_command_anc_on, 0xf00f, byteArrayOf(1, 1, 0)),
    TestCommand(R.string.nothing_command_transparency, 0xf00f, byteArrayOf(1, 7, 0)),
    TestCommand(R.string.nothing_command_latency_on, 0xf040, byteArrayOf(1, 0)),
    TestCommand(R.string.nothing_command_latency_off, 0xf040, byteArrayOf(2, 0)),
)

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NothingDeviceTestScreen(navController: NavController) {
    val context = LocalContext.current
    val backend = remember { NothingBackend(context) }
    val state by backend.state.collectAsState()
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    val deniedMessage = stringResource(R.string.nothing_permission_denied)
    val refresh = {
        try {
            devices = backend.pairedDevices()
        } catch (e: Exception) {
            backend.report("Bluetooth: ${e.message}")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) refresh() else backend.report(deniedMessage)
    }
    DisposableEffect(backend) {
        onDispose { backend.close() }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(stringResource(R.string.nothing_pair_hint))
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else refresh()
        }) {
            Text(stringResource(R.string.nothing_paired_devices))
        }
        if (devices.isEmpty()) Text(stringResource(R.string.nothing_no_devices))
        devices.forEach { device ->
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.connecting && !state.connected,
                onClick = { backend.connect(device) },
            ) {
                Text("${device.name ?: device.address}\n${device.address}")
            }
        }
        OutlinedButton(enabled = state.connected || state.connecting, onClick = backend::disconnect) {
            Text(stringResource(R.string.nothing_disconnect))
        }
        Text(stringResource(R.string.nothing_test_hint))
        testCommands.forEach { command ->
            OutlinedButton(
                enabled = state.connected,
                onClick = { backend.send(command.code, command.payload) },
            ) {
                Text(stringResource(command.label))
            }
        }
        SelectionContainer {
            Text(state.log.joinToString("\n"), fontFamily = FontFamily.Monospace)
        }
    }
    TopAppBar(
        title = { Text(stringResource(R.string.nothing_device_test)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}
