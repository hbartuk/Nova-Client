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
    // Список устройств, под которые мы можем маскироваться.
    // Коды DeviceOS и другие параметры взяты из спецификации протокола.
    private enum class Device(
        val displayName: String,
        val model: String,
        val os: Int,
        val inputMode: InputMode,
        val uiProfile: Int // 0 = Classic UI (ПК), 1 = Pocket UI (Мобильные)
    ) {
        ANDROID("Android", "Samsung SM-G998U1", 1, InputMode.TOUCH, 1),
        IOS("iOS", "iPhone14,2", 2, InputMode.TOUCH, 1),
        WINDOWS("Windows", "Dell XPS 15", 7, InputMode.MOUSE, 0), // Используем MOUSE
        LINUX("Linux", "Custom PC", 7, InputMode.MOUSE, 0),       // Используем MOUSE
        NINTENDO("Nintendo Switch", "Nintendo Switch", 11, InputMode.GAMEPAD, 0),
        XBOX("Xbox", "Xbox Series X", 12, InputMode.GAMEPAD, 0),
        PLAYSTATION("PlayStation", "PlayStation 5", 13, InputMode.GAMEPAD, 0)
    }

    // Настройка, которая будет доступна в GUI клиента для выбора устройства
    private var deviceTypeIndex by intValue("Device", 2, 0..Device.values().size - 1) // По умолчанию: Windows

    private val gson = Gson()
    private var spoofedDeviceId: String? = null

    // Вычисляемое свойство для получения выбранного объекта Device
    private val selectedDevice: Device
        get() = Device.values()[deviceTypeIndex.coerceIn(0, Device.values().size - 1)]

    override fun onEnabled() {
        super.onEnabled()
        spoofedDeviceId = generateDeviceId()
        runOnSession {
            it.displayClientMessage("§a[DeviceSpoof] Включен. Устройство: §b${selectedDevice.displayName}")
        }
    }

    // Главный метод, который вызывается для каждого пакета
    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) return

        when (val packet = interceptablePacket.packet) {
            // Нас интересуют только эти два типа пакетов, которые всегда являются исходящими
            is LoginPacket -> handleLoginPacket(packet)
            is PlayerAuthInputPacket -> handlePlayerAuthInputPacket(packet)
        }
    }

    private fun handleLoginPacket(packet: LoginPacket) {
        try {
            // 1. Читаем оригинальный JWT из пакета
            val originalJwt = packet.clientJwt
            if (originalJwt.isNullOrBlank()) return

            val parts = originalJwt.split('.')
            if (parts.size < 2) return

            // 2. Декодируем и парсим Payload
            val payload = String(Base64.getDecoder().decode(parts[1]), Charsets.UTF_8)
            val body = JsonParser.parseString(payload).asJsonObject
            val device = selectedDevice

            // 3. Модифицируем Payload
            body.addProperty("DeviceModel", device.model)
            body.addProperty("DeviceOS", device.os)
            // УЛУЧШЕНИЕ: Используем .ordinal для получения числового кода enum.
            // Это более надежно, чем предполагать порядок.
            body.addProperty("CurrentInputMode", device.inputMode.ordinal)
            body.addProperty("DefaultInputMode", device.inputMode.ordinal)
            body.addProperty("UIProfile", device.uiProfile)
            body.addProperty("ClientRandomId", Random.nextLong())
            body.addProperty("DeviceId", spoofedDeviceId ?: generateDeviceId())
            body.addProperty("SelfSignedId", UUID.randomUUID().toString())
            body.remove("PlatformOnlineId")
            body.remove("PlatformOfflineId")

            // 4. Собираем новый JWT с невалидной подписью
            val newPayload = gson.toJson(body)
            val encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(newPayload.toByteArray(Charsets.UTF_8))
            val modifiedJwt = "${parts[0]}.$encodedPayload.${parts.getOrNull(2) ?: ""}"

            // 5. Используем рефлексию для записи нового JWT в приватное поле
            setPrivateField(packet, "clientJwt", modifiedJwt)

        } catch (e: Exception) {
            runOnSession { it.displayClientMessage("§c[DeviceSpoof] Критическая ошибка рефлексии: ${e.message}") }
            e.printStackTrace()
        }
    }

    private fun handlePlayerAuthInputPacket(packet: PlayerAuthInputPacket) {
        // Поддерживаем консистентность, меняя InputMode в пакетах движения
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
        runOnSession { it.displayClientMessage("§c[DeviceSpoof] Выключен.") }
    }

    /**
     * Вспомогательная функция для установки значения приватного поля с помощью рефлексии.
     */
    private fun setPrivateField(obj: Any, fieldName: String, value: Any?) {
        try {
            val field: Field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true // "Взламываем замок"
            field.set(obj, value)      // Устанавливаем значение
        } catch (e: Exception) {
            System.err.println("Reflection Error: Could not set field '$fieldName' on ${obj.javaClass.name}")
            throw e
        }
    }
}
