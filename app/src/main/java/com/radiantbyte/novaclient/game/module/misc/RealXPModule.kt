package com.radiantbyte.novaclient.game.module.misc

import com.radiantbyte.novaclient.game.InterceptablePacket
import com.radiantbyte.novaclient.game.Module
import com.radiantbyte.novaclient.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

class RealXPModule : Module("real_xp", ModuleCategory.Misc) {

    private val intervalValue by intValue("interval", 1000, 100..5000)
    private val xpAmountValue by intValue("xp_amount", 10, 1..1000)
    private val debugMode by boolValue("debug_mode", false)

    private var lastXPTime = 0L

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) {
            return
        }

        val packet = interceptablePacket.packet
        if (packet is PlayerAuthInputPacket) {
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastXPTime >= intervalValue) {
                lastXPTime = currentTime
                giveXP()
            }
        }
    }

    private fun giveXP() {
        try {
            sendEntityEvent()
            if (debugMode) {
                println("Nova XP: Sent XP using Entity Event method")
            }
        } catch (e: Exception) {
            if (debugMode) {
                println("Nova XP Error: ${e.message}")
            }
        }
    }

    private fun sendEntityEvent() {
        val entityEventPacket = EntityEventPacket().apply {
            runtimeEntityId = session.localPlayer.runtimeEntityId
            type = EntityEventType.PLAYER_ADD_XP_LEVELS
            data = xpAmountValue
        }
        session.serverBound(entityEventPacket)
    }
}
