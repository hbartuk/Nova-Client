package com.radiantbyte.novaclient.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonParser
import com.radiantbyte.novaclient.application.AppContext
import com.radiantbyte.novaclient.service.RealmsManager
import com.radiantbyte.novarelay.util.AuthUtils
import com.radiantbyte.novarelay.util.refresh
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.raphimc.minecraftauth.MinecraftAuth
import net.raphimc.minecraftauth.step.bedrock.session.StepFullBedrockSession.FullBedrockSession
import net.raphimc.minecraftauth.util.MicrosoftConstants
import java.io.File
import java.util.concurrent.TimeUnit

object AccountManager {

    private val coroutineScope =
        CoroutineScope(Dispatchers.IO + CoroutineName("AccountManagerCoroutine"))

    private val _accounts: MutableList<FullBedrockSession> = mutableStateListOf()
    val accounts: List<FullBedrockSession> get() = _accounts

    var selectedAccount: FullBedrockSession? by mutableStateOf(null)
        private set

    private val TOKEN_REFRESH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(30)
    private val TOKEN_REFRESH_THRESHOLD_MS = TimeUnit.HOURS.toMillis(2)

    // ✅ Рабочая версия билдера для Xbox авторизации под твою библиотеку
    private val FIXED_BEDROCK_DEVICE_CODE_LOGIN =
        MinecraftAuth.builder()
            .withClientId(MicrosoftConstants.BEDROCK_ANDROID_TITLE_ID)
            .withScope(MicrosoftConstants.SCOPE_TITLE_AUTH)
            .deviceCode()
            .withDeviceToken("Android")
            .sisuTitleAuthentication(MicrosoftConstants.BEDROCK_XSTS_RELYING_PARTY)
            .buildMinecraftBedrockChainStep(true, true)

    init {
        val fetchedAccounts = fetchAccounts()
        _accounts.addAll(fetchedAccounts)
        selectedAccount = fetchSelectedAccount()
        RealmsManager.updateSession(selectedAccount)
        startTokenRefreshScheduler()
    }

    fun addAccount(fullBedrockSession: FullBedrockSession) {
        val existingAccount =
            _accounts.find { it.mcChain.displayName == fullBedrockSession.mcChain.displayName }
        if (existingAccount != null) _accounts.remove(existingAccount)

        _accounts.add(fullBedrockSession)

        coroutineScope.launch {
            val file = File(AppContext.instance.cacheDir, "accounts")
            file.mkdirs()

            try {
                val json = if (fullBedrockSession.realmsXsts != null) {
                    RealmsAuthFlow.BEDROCK_DEVICE_CODE_LOGIN_WITH_REALMS.toJson(fullBedrockSession)
                } else {
                    FIXED_BEDROCK_DEVICE_CODE_LOGIN.toJson(fullBedrockSession)
                }
                file.resolve("${fullBedrockSession.mcChain.displayName}.json")
                    .writeText(AuthUtils.gson.toJson(json))
                println("✅ Saved account ${fullBedrockSession.mcChain.displayName} - Realms: ${fullBedrockSession.realmsXsts != null}")
            } catch (e: Exception) {
                println("⚠️ Failed to save account: ${e.message}")
            }
        }
    }

    fun removeAccount(fullBedrockSession: FullBedrockSession) {
        _accounts.remove(fullBedrockSession)
        coroutineScope.launch {
            val file = File(AppContext.instance.cacheDir, "accounts")
            file.mkdirs()
            file.resolve("${fullBedrockSession.mcChain.displayName}.json").delete()
        }
    }

    fun selectAccount(fullBedrockSession: FullBedrockSession?) {
        selectedAccount = fullBedrockSession
        RealmsManager.updateSession(fullBedrockSession)
        coroutineScope.launch {
            val file = File(AppContext.instance.cacheDir, "accounts")
            file.mkdirs()
            val selected = file.resolve("selectedAccount")
            if (fullBedrockSession != null) selected.writeText(fullBedrockSession.mcChain.displayName)
            else selected.delete()
        }
    }

    private fun fetchAccounts(): List<FullBedrockSession> {
        val dir = File(AppContext.instance.cacheDir, "accounts")
        dir.mkdirs()

        val list = ArrayList<FullBedrockSession>()
        val files = dir.listFiles() ?: return list

        for (child in files) {
            if (child.isFile && child.extension == "json") {
                runCatching {
                    val json = JsonParser.parseString(child.readText()).asJsonObject
                    val account = try {
                        RealmsAuthFlow.BEDROCK_DEVICE_CODE_LOGIN_WITH_REALMS.fromJson(json)
                    } catch (e: Exception) {
                        FIXED_BEDROCK_DEVICE_CODE_LOGIN.fromJson(json)
                    }
                    list.add(account)
                    println("✅ Loaded account ${account.mcChain.displayName}")
                }.onFailure {
                    println("⚠️ Failed to load account ${child.name}: ${it.message}")
                }
            }
        }
        return list
    }

    private fun fetchSelectedAccount(): FullBedrockSession? {
        val dir = File(AppContext.instance.cacheDir, "accounts")
        val selected = dir.resolve("selectedAccount")
        if (!selected.exists()) return null
        val name = selected.readText()
        return _accounts.find { it.mcChain.displayName == name }
    }

    private fun startTokenRefreshScheduler() {
        coroutineScope.launch {
            while (true) {
                try {
                    refreshExpiredTokens()
                } catch (e: Exception) {
                    println("⚠️ Error during token refresh: ${e.message}")
                }
                delay(TOKEN_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun refreshExpiredTokens() {
        val toRefresh = _accounts.filter { shouldRefreshToken(it) }
        if (toRefresh.isEmpty()) return

        println("🔄 Refreshing ${toRefresh.size} accounts...")

        toRefresh.forEach { acc ->
            try {
                val httpClient = MinecraftAuth.createHttpClient().apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                val refreshed = try {
                    if (acc.realmsXsts != null) {
                        RealmsAuthFlow.BEDROCK_DEVICE_CODE_LOGIN_WITH_REALMS.refresh(httpClient, acc)
                    } else acc.refresh()
                } catch (e: Exception) {
                    println("⚠️ Realms refresh failed, trying fallback: ${e.message}")
                    acc.refresh()
                }

                val idx = _accounts.indexOf(acc)
                if (idx >= 0) _accounts[idx] = refreshed

                if (selectedAccount == acc) {
                    selectedAccount = refreshed
                    RealmsManager.updateSession(refreshed)
                }

                saveAccountToDisk(refreshed)
                println("✅ Token refreshed for ${refreshed.mcChain.displayName}")
            } catch (e: Exception) {
                println("❌ Failed to refresh ${acc.mcChain.displayName}: ${e.message}")
            }
        }
    }

    private fun shouldRefreshToken(account: FullBedrockSession): Boolean {
        val now = System.currentTimeMillis()
        val msaExp = account.mcChain.xblXsts.initialXblSession.msaToken.expireTimeMs
        val xblExp = account.mcChain.xblXsts.expireTimeMs
        val playFabExp = account.playFabToken.expireTimeMs
        return (msaExp - now < TOKEN_REFRESH_THRESHOLD_MS
                || xblExp - now < TOKEN_REFRESH_THRESHOLD_MS
                || playFabExp - now < TOKEN_REFRESH_THRESHOLD_MS)
    }

    private fun saveAccountToDisk(account: FullBedrockSession) {
        val file = File(AppContext.instance.cacheDir, "accounts")
        file.mkdirs()
        try {
            val json = if (account.realmsXsts != null) {
                RealmsAuthFlow.BEDROCK_DEVICE_CODE_LOGIN_WITH_REALMS.toJson(account)
            } else {
                FIXED_BEDROCK_DEVICE_CODE_LOGIN.toJson(account)
            }
            file.resolve("${account.mcChain.displayName}.json")
                .writeText(AuthUtils.gson.toJson(json))
        } catch (e: Exception) {
            println("⚠️ Save failed: ${e.message}")
        }
    }
}
