package com.netguardian.app.core

/**
 * iptables规则数据类
 * 描述一条iptables规则的具体参数
 */
data class IptablesRule(
    /** 目标UID（按用户过滤） */
    val uid: Int,
    /** 应用包名（仅用于日志和记录） */
    val packageName: String,
    /** 是否屏蔽移动网络 */
    val blockMobile: Boolean = false,
    /** 是否屏蔽WiFi网络 */
    val blockWifi: Boolean = false,
    /** 是否添加规则（true=添加/A，false=删除/D） */
    val isAdd: Boolean = true,
    /** 是否包含IPv6规则 */
    val includeIpv6: Boolean = true
) {
    /**
     * 生成iptables命令列表
     * @return 需要执行的shell命令列表
     */
    fun toCommands(): List<String> {
        val commands = mutableListOf<String>()
        val action = if (isAdd) "A" else "D"

        if (blockMobile) {
            // IPv4
            commands.add(
                "iptables -$action OUTPUT -m owner --uid-owner $uid -o rmnet+ -j REJECT"
            )
            // IPv6
            if (includeIpv6) {
                commands.add(
                    "ip6tables -$action OUTPUT -m owner --uid-owner $uid -o rmnet+ -j REJECT"
                )
            }
        }

        if (blockWifi) {
            // IPv4
            commands.add(
                "iptables -$action OUTPUT -m owner --uid-owner $uid -o wlan+ -j REJECT"
            )
            // IPv6
            if (includeIpv6) {
                commands.add(
                    "ip6tables -$action OUTPUT -m owner --uid-owner $uid -o wlan+ -j REJECT"
                )
            }
        }

        return commands
    }
}