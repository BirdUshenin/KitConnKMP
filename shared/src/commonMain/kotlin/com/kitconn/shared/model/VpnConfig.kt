package com.kitconn.shared.model

import kotlinx.serialization.Serializable

/** Сервер из `GET api/v1/configs`. */
@Serializable
data class VpnConfig(
    val version: Int,
    val country: String,
    val name: String,
    val subtitle: String,
    val config: String,
) {
    val displayName: String get() = name.ifEmpty { country }
}

@Serializable
data class VpnConfigsResponse(val configs: List<VpnConfig>)
