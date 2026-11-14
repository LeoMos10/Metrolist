package com.metrolist.music.ui.screens.settings.integrations

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.NothingDeviceAddressKey
import com.metrolist.music.constants.NothingDeviceNameKey
import com.metrolist.music.nothing.NothingBackend
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference

private data class EffectiveCommand(val label: Int, val code: Int, val payload: ByteArray = byteArrayOf())

private val statusCommands = listOf(
    EffectiveCommand(R.string.nothing_command_battery, 0xc007),
    EffectiveCommand(R.string.nothing_command_firmware, 0xc042),
    EffectiveCommand(R.string.nothing_command_serial, 0xc006),
)

private val ancCommands = listOf(
    EffectiveCommand(R.string.nothing_command_anc, 0xc01e),
    EffectiveCommand(R.string.nothing_command_anc_off, 0xf00f, byteArrayOf(1, 5, 0)),
    EffectiveCommand(R.string.nothing_command_anc_on, 0xf00f, byteArrayOf(1, 1, 0)),
    EffectiveCommand(R.string.nothing_command_transparency, 0xf00f, byteArrayOf(1, 7, 0)),
)

private val audioCommands = listOf(
    EffectiveCommand(R.string.nothing_command_eq, 0xc01f),
    EffectiveCommand(R.string.nothing_command_gestures, 0xc018),
)

private val latencyCommands = listOf(
    EffectiveCommand(R.string.nothing_command_latency, 0xc041),
    EffectiveCommand(R.string.nothing_command_latency_on, 0xf040, byteArrayOf(1, 0)),
    EffectiveCommand(R.string.nothing_command_latency_off, 0xf040, byteArrayOf(2, 0)),
)

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NothingDeviceTestScreen(navController: NavController) {
    val context = LocalContext.current
    val backend = remember { NothingBackend(context.applicationContext) }
    val state by backend.state.collectAsState()
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var choosing by remember { mutableStateOf(false) }
    var deviceAddress by rememberPreference(NothingDeviceAddressKey, "")
    var deviceName by rememberPreference(NothingDeviceNameKey, "")
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
    fun ensurePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else action()
    }
    fun connectConfigured() {
        val device = backend.bondedByAddress(deviceAddress) ?: return
        backend.connect(device)
    }
    LaunchedEffect(deviceAddress) {
        if (deviceAddress.isNotBlank()) ensurePermission { connectConfigured() }
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
        Text(
            stringResource(R.string.nothing_section_device),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        if (deviceAddress.isBlank()) {
            Text(stringResource(R.string.nothing_no_configured_device))
        } else {
            Text("${deviceName.ifBlank { deviceAddress }}\n$deviceAddress")
            Text(
                if (state.connected) stringResource(R.string.nothing_status_connected)
                else if (state.connecting) stringResource(R.string.nothing_status_connecting)
                else stringResource(R.string.nothing_status_disconnected),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                ensurePermission {
                    refresh()
                    choosing = true
                }
            }) {
                Text(stringResource(R.string.nothing_choose_device))
            }
            if (deviceAddress.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    backend.disconnect()
                    deviceAddress = ""
                    deviceName = ""
                }) {
                    Text(stringResource(R.string.nothing_forget_device))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    enabled = !state.connecting && !state.connected,
                    onClick = { ensurePermission { connectConfigured() } },
                ) {
                    Text(stringResource(R.string.nothing_reconnect))
                }
            }
        }
        if (choosing) {
            Spacer(Modifier.height(8.dp))
            if (devices.isEmpty()) Text(stringResource(R.string.nothing_no_devices))
            devices.forEach { device ->
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        deviceAddress = device.address
                        deviceName = device.name ?: device.address
                        choosing = false
                        backend.connect(device)
                    },
                ) {
                    Text("${device.name ?: device.address}\n${device.address}")
                }
            }
        }
        OutlinedButton(
            modifier = Modifier.padding(top = 8.dp),
            enabled = state.connected || state.connecting,
            onClick = backend::disconnect,
        ) {
            Text(stringResource(R.string.nothing_disconnect))
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.nothing_section_commands),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.nothing_test_hint))
        Spacer(Modifier.height(8.dp))
        (statusCommands + ancCommands + audioCommands + latencyCommands).forEach { command ->
            OutlinedButton(
                enabled = state.connected,
                onClick = { backend.send(command.code, command.payload) },
            ) {
                Text(stringResource(command.label))
            }
        }
        Spacer(Modifier.height(16.dp))
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
