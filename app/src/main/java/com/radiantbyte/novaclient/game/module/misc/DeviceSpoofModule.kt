package com.radiantbyte.novaclient.game.module.misc

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.radiantbyte.novaclient.game.InterceptablePacket
import com.radiantbyte.novaclient.game.Module
import com.radiantbyte.novaclient.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.data.InputMode
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import java.lang.reflect.Field
import java.util.*
import kotlin.random.Random

class DeviceSpoofModule : Module(
    name = "DeviceSpoof",
    category = ModuleCategory.Misc
) {
    private enum class Device(
        val displayName: String,
        val model: String,
        val os: Int,
        val inputMode: InputMode,
        val uiProfile: Int
    ) {
        ANDROID("Android", "Samsung SM-G998U1", 1, InputMode.TOUCH, 1),
        IOS("iOS", "iPhone14,2", 2, InputMode.TOUCH, 1),
        WINDOWS("Windows", "Dell XPS 15", 7, InputMode.MOUSE, 0),
        LINUX("Linux", "Custom PC", 7, InputMode.MOUSE, 0),
        NINTENDO("Nintendo Switch", "Nintendo Switch", 11, InputMode.GAMEPAD, 0),
        XBOX("Xbox", "Xbox Series X", 12, InputMode.GAMEPAD, 0),
        PLAYSTATION("PlayStation", "PlayStation 5", 13, InputMode.GAMEPAD, 0)
    }

    private var deviceTypeIndex by intValue("Device", 2, 0..Device.values().size - 1)
    private val gson = Gson()
    private var spoofedDeviceId: String? = null

    private val selectedDevice: Device
        get() = Device.values()[deviceTypeIndex.coerceIn(0, Device.values().size - 1)]

    override fun onEnabled() {
        super.onEnabled()
        spoofedDeviceId = generateDeviceId()
        if (isSessionCreated) {
            session.displayClientMessage("§a[DeviceSpoof] Включен. Устройство: §b${selectedDevice.displayName}")
        }
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) return

        when (val packet = interceptablePacket.packet) {
            is LoginPacket -> handleLoginPacket(packet)
            is PlayerAuthInputPacket -> handlePlayerAuthInputPacket(packet)
        }
    }

    private fun handleLoginPacket(packet: LoginPacket) {
        try {
            val originalJwt = packet.clientJwt ?: return
            val parts = originalJwt.split('.')
            if (parts.size < 2) return

            val payload = String(Base64.getDecoder().decode(parts[1]), Charsets.UTF_8)
            val body = JsonParser.parseString(payload).asJsonObject
            val device = selectedDevice

            // Модифицируем JSON-поля, маскируя устройство
            body.addProperty("DeviceModel", device.model)
            body.addProperty("DeviceOS", device.os)
            body.addProperty("CurrentInputMode", device.inputMode.ordinal)
            body.addProperty("DefaultInputMode", device.inputMode.ordinal)
            body.addProperty("UIProfile", device.uiProfile)
            body.addProperty("ClientRandomId", Random.nextLong())
            body.addProperty("DeviceId", spoofedDeviceId ?: generateDeviceId())
            body.addProperty("SelfSignedId", UUID.randomUUID().toString())
            body.remove("PlatformOnlineId")
            body.remove("PlatformOfflineId")

            val newPayload = gson.toJson(body)
            val encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(newPayload.toByteArray(Charsets.UTF_8))
            val modifiedJwt = "${parts[0]}.$encodedPayload.${parts.getOrNull(2) ?: ""}"

            setPrivateField(packet, "clientJwt", modifiedJwt)

        } catch (e: Exception) {
            if (isSessionCreated) {
                session.displayClientMessage("§c[DeviceSpoof] Ошибка рефлексии: ${e.message}")
            }
            e.printStackTrace()
        }
    }

    private fun handlePlayerAuthInputPacket(packet: PlayerAuthInputPacket) {
        packet.inputMode = selectedDevice.inputMode
    }

    private fun generateDeviceId(): String {
        return when (selectedDevice) {
            Device.ANDROID -> Random.nextBytes(8).joinToString("") { "%02x".format(it) }
            Device.IOS -> UUID.randomUUID().toString().replace("-", "").uppercase()
            Device.WINDOWS, Device.LINUX -> "{${UUID.randomUUID()}}"
            else -> UUID.randomUUID().toString()
        }
    }

    override fun onDisabled() {
        super.onDisabled()
        spoofedDeviceId = null
        if (isSessionCreated) {
            session.displayClientMessage("§c[DeviceSpoof] Выключен.")
        }
    }

    private fun setPrivateField(obj: Any, fieldName: String, value: Any?) {
        val field: Field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(obj, value)
    }
}
