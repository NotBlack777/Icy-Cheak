package com.icy.devcheckplus.model

data class InfoItem(
    val title: String,
    val value: String,
    val subtitle: String? = null,
    val requiresPrivilege: Boolean = false,
    val privilegeSource: String? = null
)

data class InfoSection(
    val title: String,
    val items: List<InfoItem>
)
