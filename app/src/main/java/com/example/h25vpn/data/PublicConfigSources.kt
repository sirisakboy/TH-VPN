package com.example.h25vpn.data

/** Public subscriptions are untrusted community data, not carrier endorsements. */
object PublicConfigSources {
    data class Source(
        val id: String,
        val title: String,
        val repository: String,
        val path: String,
        val country: String? = null,
        val countryCode: String? = null
    ) {
        val pageUrl get() = "https://github.com/$repository"
        val primaryUrl get() = "https://raw.githubusercontent.com/$repository/main/$path"
        val mirrorUrl get() = "https://cdn.jsdelivr.net/gh/$repository@main/$path"
    }

    val all = listOf(
        Source("spirit-th", "SoliSpirit • Thailand", "SoliSpirit/v2ray-configs", "Countries/Thailand.txt", "Thailand", "th"),
        Source("spirit-sg", "SoliSpirit • Singapore", "SoliSpirit/v2ray-configs", "Countries/Singapore.txt", "Singapore", "sg"),
        Source("spirit-jp", "SoliSpirit • Japan", "SoliSpirit/v2ray-configs", "Countries/Japan.txt", "Japan", "jp"),
        Source("spirit-hk", "SoliSpirit • Hong Kong", "SoliSpirit/v2ray-configs", "Countries/Hong_Kong.txt", "Hong Kong", "hk"),
        Source("solvpn-th", "SolVPN • Thailand", "SoliSpirit/SolVPN", "Countrys/Thailand.txt", "Thailand", "th"),
        Source("solvpn-sg", "SolVPN • Singapore", "SoliSpirit/SolVPN", "Countrys/Singapore.txt", "Singapore", "sg"),
        // These regional files can overlap; do not infer physical location from the path.
        Source("forge-sg", "ConfigForge • SG feed", "ShatakVPN/ConfigForge-V2Ray", "configs/sg/all.txt"),
        Source("forge-jp", "ConfigForge • JP feed", "ShatakVPN/ConfigForge-V2Ray", "configs/jp/all.txt"),
        Source("forge-hk", "ConfigForge • HK feed", "ShatakVPN/ConfigForge-V2Ray", "configs/hk/all.txt")
    )
}
