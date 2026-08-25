package com.warp.android

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.warp.android.data.WarpAccountCredentials
import com.warp.android.data.WarpApi
import com.warp.android.vpn.VpnManager
import com.warp.android.worker.WarpRenewalWorker
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class MainViewModel(
    private val warpApi: WarpApi,
    private val vpnManager: VpnManager
) : ViewModel() {

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status

    private val _remainingTimeText = MutableStateFlow("Renewal Not Scheduled")
    val remainingTimeText: StateFlow<String> = _remainingTimeText

    private val _credentials = MutableStateFlow<WarpAccountCredentials?>(null)
    val credentials: StateFlow<WarpAccountCredentials?> = _credentials

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        val savedCreds = warpApi.getSavedCredentials()
        _credentials.value = savedCreds

        viewModelScope.launch {
            vpnManager.tunnelState.collect { state ->
                _status.value = when (state) {
                    Tunnel.State.UP -> ConnectionStatus.CONNECTED
                    Tunnel.State.DOWN -> ConnectionStatus.DISCONNECTED
                    else -> ConnectionStatus.CONNECTING
                }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                updateRemainingTime()
                delay(1000)
            }
        }
    }

    private fun updateRemainingTime() {
        val creds = _credentials.value
        if (creds == null) {
            _remainingTimeText.value = "No Active WARP Profile"
            return
        }

        val now = System.currentTimeMillis()
        val targetTime = creds.expirationTimestampEpochMs - TimeUnit.DAYS.toMillis(2)
        val diff = targetTime - now

        if (diff <= 0) {
            _remainingTimeText.value = "Renewal Due Soon"
        } else {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(diff) % 60
            _remainingTimeText.value = String.format("Next Background Renewal in %02dh %02dm %02ds", hours, minutes, seconds)
        }
    }

    fun checkVpnPermissionAndToggle(
        context: android.content.Context,
        onPermissionNeeded: (Intent) -> Unit
    ) {
        val intent = vpnManager.prepareVpnIntent()
        if (intent != null) {
            onPermissionNeeded(intent)
        } else {
            toggleWarpConnection(context)
        }
    }

    fun toggleWarpConnection(context: android.content.Context) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (_status.value) {
                    ConnectionStatus.CONNECTED, ConnectionStatus.CONNECTING -> {
                        vpnManager.stopTunnel()
                    }
                    ConnectionStatus.DISCONNECTED -> {
                        var creds = warpApi.getSavedCredentials()
                        if (creds == null) {
                            val keyPair = vpnManager.generateKeyPair()
                            val priv = keyPair.privateKey.toBase64()
                            val pub = keyPair.publicKey.toBase64()
                            creds = warpApi.registerOrRenewKey(priv, pub)
                            _credentials.value = creds
                            WarpRenewalWorker.scheduleNextRenewal(context, creds.expirationTimestampEpochMs)
                        }
                        vpnManager.startTunnel(creds)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

class MainViewModelFactory(
    private val warpApi: WarpApi,
    private val vpnManager: VpnManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(warpApi, vpnManager) as T
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleWarpConnection(this)
        } else {
            Toast.makeText(this, "VPN Permission is required to enable WARP", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val warpApi = WarpApi(applicationContext)
        val vpnManager = VpnManager(applicationContext)
        val factory = MainViewModelFactory(warpApi, vpnManager)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WarpScreen(
                        viewModel = viewModel,
                        onRequestVpnPermission = { intent ->
                            vpnPermissionLauncher.launch(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WarpScreen(
    viewModel: MainViewModel,
    onRequestVpnPermission: (Intent) -> Unit
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsState()
    val remainingTimeText by viewModel.remainingTimeText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val credentials by viewModel.credentials.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 32.dp)
        ) {
            Text(
                text = "1-Click WARP",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cloudflare WireGuard Tunnel",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val statusText = when (status) {
                ConnectionStatus.CONNECTED -> "CONNECTED"
                ConnectionStatus.CONNECTING -> "CONNECTING..."
                ConnectionStatus.DISCONNECTED -> "DISCONNECTED"
            }

            val statusColor = when (status) {
                ConnectionStatus.CONNECTED -> Color(0xFF4CAF50)
                ConnectionStatus.CONNECTING -> Color(0xFFFF9800)
                ConnectionStatus.DISCONNECTED -> Color(0xFFF44336)
            }

            Text(
                text = statusText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.checkVpnPermissionAndToggle(context, onRequestVpnPermission)
                },
                modifier = Modifier.size(200.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (status == ConnectionStatus.CONNECTED) Color(0xFF388E3C) else MaterialTheme.colorScheme.primary
                ),
                enabled = !isLoading
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Text(
                            text = if (status == ConnectionStatus.CONNECTED) "Disable WARP" else "Enable WARP",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Automated Key Renewal",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = remainingTimeText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (credentials != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Endpoint: ${credentials?.endpointHost}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
