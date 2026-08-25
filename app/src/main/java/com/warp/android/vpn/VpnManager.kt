package com.warp.android.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.warp.android.data.WarpAccountCredentials
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetAddresses
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VpnManager(private val context: Context) {

    private val backend: Backend by lazy {
        GoBackend(context)
    }

    private val warpTunnel = WarpTunnel()

    private val _tunnelState = MutableStateFlow<Tunnel.State>(Tunnel.State.DOWN)
    val tunnelState: StateFlow<Tunnel.State> = _tunnelState

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = backend.getState(warpTunnel)
                _tunnelState.value = state
            } catch (_: Exception) {
                _tunnelState.value = Tunnel.State.DOWN
            }
        }
    }

    fun prepareVpnIntent(): Intent? {
        return VpnService.prepare(context)
    }

    fun generateKeyPair(): KeyPair {
        return KeyPair()
    }

    suspend fun buildConfig(credentials: WarpAccountCredentials): Config = withContext(Dispatchers.IO) {
        val interfaceBuilder = Interface.Builder()

        // Interface configuration
        val keyPair = KeyPair(com.wireguard.crypto.Key.fromBase64(credentials.privateKey))
        interfaceBuilder.setKeyPair(keyPair)

        if (credentials.ipv4.isNotEmpty()) {
            val v4Address = if (credentials.ipv4.contains("/")) credentials.ipv4 else "${credentials.ipv4}/32"
            interfaceBuilder.addAddress(InetNetwork.parse(v4Address))
        }

        if (!credentials.ipv6.isNullOrEmpty()) {
            val v6Address = if (credentials.ipv6.contains("/")) credentials.ipv6 else "${credentials.ipv6}/128"
            interfaceBuilder.addAddress(InetNetwork.parse(v6Address))
        }

        interfaceBuilder.addDnsServer(InetAddresses.parse("1.1.1.1"))
        interfaceBuilder.addDnsServer(InetAddresses.parse("1.0.0.1"))
        interfaceBuilder.setMtu(1280)

        // Peer configuration
        val peerBuilder = Peer.Builder()
        val peerPublicKey = com.wireguard.crypto.Key.fromBase64(credentials.peerPublicKey)
        peerBuilder.setPublicKey(peerPublicKey)

        peerBuilder.addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
        peerBuilder.addAllowedIp(InetNetwork.parse("::/0"))

        peerBuilder.setEndpoint(InetEndpoint.parse(credentials.endpointHost))
        peerBuilder.setPersistentKeepalive(25)

        val configBuilder = Config.Builder()
        configBuilder.setInterface(interfaceBuilder.build())
        configBuilder.addPeer(peerBuilder.build())

        configBuilder.build()
    }

    suspend fun startTunnel(credentials: WarpAccountCredentials): Tunnel.State = withContext(Dispatchers.IO) {
        val config = buildConfig(credentials)
        val newState = backend.setState(warpTunnel, Tunnel.State.UP, config)
        _tunnelState.value = newState
        newState
    }

    suspend fun stopTunnel(): Tunnel.State = withContext(Dispatchers.IO) {
        val newState = backend.setState(warpTunnel, Tunnel.State.DOWN, null)
        _tunnelState.value = newState
        newState
    }

    suspend fun updateTunnelConfig(credentials: WarpAccountCredentials) = withContext(Dispatchers.IO) {
        val currentState = backend.getState(warpTunnel)
        if (currentState == Tunnel.State.UP) {
            val config = buildConfig(credentials)
            backend.setState(warpTunnel, Tunnel.State.UP, config)
        }
    }

    suspend fun getTunnelState(): Tunnel.State = withContext(Dispatchers.IO) {
        val state = backend.getState(warpTunnel)
        _tunnelState.value = state
        state
    }

    private class WarpTunnel : Tunnel {
        override fun getName(): String = "WARP"

        override fun onStateChange(newState: Tunnel.State) {
            // Tunnel state changed event callback
        }
    }
}
