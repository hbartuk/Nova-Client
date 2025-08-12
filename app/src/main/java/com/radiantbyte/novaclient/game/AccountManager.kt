package com.radiantbyte.novaclient.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonParser
import com.radiantbyte.novaclient.application.AppContext
import com.radiantbyte.novarelay.util.AuthUtils
import com.radiantbyte.novarelay.util.refresh
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.raphimc.minecraftauth.MinecraftAuth
import net.raphimc.minecraftauth.step.bedrock.session.StepFullBedrockSession.FullBedrockSession
import java.io.File
import java.util.concurrent.TimeUnit

object AccountManager {

    private val coroutineScope =
        CoroutineScope(Dispatchers.IO + CoroutineName("AccountManagerCoroutine"))

    private val _accounts: MutableList<FullBedrockSession> = mutableStateListOf()

    val accounts: List<FullBedrockSession>
        get() = _accounts

    var selectedAccount: FullBedrockSession? by mutableStateOf(null)
        private set

    private val TOKEN_REFRESH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(30)

    private val TOKEN_REFRESH_THRESHOLD_MS = TimeUnit.HOURS.toMillis(2)

    init {
        val fetchedAccounts = fetchAccounts()

        _accounts.addAll(fetchedAccounts)
        selectedAccount = fetchSelectedAccount()

        startTokenRefreshScheduler()
    }

    fun addAccount(fullBedrockSession: FullBedrockSession) {
        _accounts.add(fullBedrockSession)

        coroutineScope.launch {
            val file = File(AppContext.instance.cacheDir, "accounts")
            file.mkdirs()

            val json = MinecraftAuth.BEDROCK_DEVICE_CODE_LOGIN
                .toJson(fullBedrockSession)
            file.resolve("${fullBedrockSession.mcChain.displayName}.json")
                .writeText(AuthUtils.gson.toJson(json))
        }
    }

    fun removeAccount(fullBedrockSession: FullBedrockSession) {
        _accounts.remove(fullBedrockSession)

        coroutineScope.launch {
            val file = File(AppContext.instance.cacheDir, "accounts")
            file.mkdirs()

            file.resolve("${fullBedrockSession.mcChain.displayName}.json")
                .delete()
        }
    }

    fun selectAccount(fullBedrockSession: FullBedrockSession?) {
        selectedAccount = fullBedrockSession

        coroutineScope.launch {
            val file = File(AppContext.instance.cacheDir, "accounts")
            file.mkdirs()

            runCatching {
                val selectedAccount = file.resolve("selectedAccount")
                if (fullBedrockSession != null) {
                    selectedAccount.writeText(fullBedrockSession.mcChain.displayName)
                } else {
                    selectedAccount.delete()
                }
            }
        }
    }

    private fun fetchAccounts(): List<FullBedrockSession> {
        val file = File(AppContext.instance.cacheDir, "accounts")
        file.mkdirs()

        val accounts = ArrayList<FullBedrockSession>()
        val listFiles = file.listFiles() ?: emptyArray()
        for (child in listFiles) {
            runCatching {
                if (child.isFile && child.extension == "json") {
                    val account = MinecraftAuth.BEDROCK_DEVICE_CODE_LOGIN
                        .fromJson(JsonParser.parseString(child.readText()).asJsonObject)
                    accounts.add(account)
                }
            }
        }

        return accounts
    }

    private fun fetchSelectedAccount(): FullBedrockSession? {
        val file = File(AppContext.instance.cacheDir, "accounts")
        file.mkdirs()

        val selectedAccount = file.resolve("selectedAccount")
        if (!selectedAccount.exists() || selectedAccount.isDirectory) {
            return null
        }

        val displayName = selectedAccount.readText()
        return accounts.find { it.mcChain.displayName == displayName }
    }

    private fun startTokenRefreshScheduler() {
        coroutineScope.launch {
            while (true) {
                try {
                    refreshExpiredTokens()
                } catch (e: Exception) {
                    println("Error during token refresh: ${e.message}")
                    e.printStackTrace()
                }

                delay(TOKEN_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun refreshExpiredTokens() {
        if (_accounts.isEmpty()) {
            return
        }

        val accountsToRefresh = _accounts.filter { account ->
            shouldRefreshToken(account)
        }

        if (accountsToRefresh.isNotEmpty()) {
            println("Found ${accountsToRefresh.size} accounts that need token refresh")
        }

        accountsToRefresh.forEach { account ->
            try {
                println("Refreshing token for account: ${account.mcChain.displayName}")
                val refreshedAccount = account.refresh()

                val index = _accounts.indexOf(account)
                if (index >= 0) {
                    _accounts[index] = refreshedAccount

                    if (selectedAccount == account) {
                        selectedAccount = refreshedAccount
                    }

                    saveAccountToDisk(refreshedAccount)
                }

                println("Successfully refreshed token for: ${refreshedAccount.mcChain.displayName}")
            } catch (e: Exception) {
                println("Failed to refresh token for ${account.mcChain.displayName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun shouldRefreshToken(account: FullBedrockSession): Boolean {
        val currentTime = System.currentTimeMillis()

        val msaToken = account.mcChain.xblXsts.initialXblSession.msaToken
        if (msaToken.expireTimeMs - currentTime < TOKEN_REFRESH_THRESHOLD_MS) {
            return true
        }

        val xblExpireTime = account.mcChain.xblXsts.expireTimeMs
        if (xblExpireTime - currentTime < TOKEN_REFRESH_THRESHOLD_MS) {
            return true
        }

        val playFabExpireTime = account.playFabToken.expireTimeMs
        if (playFabExpireTime - currentTime < TOKEN_REFRESH_THRESHOLD_MS) {
            return true
        }

        return false
    }

    private fun saveAccountToDisk(account: FullBedrockSession) {
        val file = File(AppContext.instance.cacheDir, "accounts")
        file.mkdirs()

        val json = MinecraftAuth.BEDROCK_DEVICE_CODE_LOGIN.toJson(account)
        file.resolve("${account.mcChain.displayName}.json")
            .writeText(AuthUtils.gson.toJson(json))
    }
}