package com.kitconn.android.vpn

import com.kitconn.shared.vpn.VpnState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Общее состояние между VpnService и контроллером: оба живут в одном процессе. */
object VpnBridge {
    val state = MutableStateFlow(VpnState.DISCONNECTED)
    val errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
}
