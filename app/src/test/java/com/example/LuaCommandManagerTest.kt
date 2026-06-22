package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LuaCommandManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
         context = ApplicationProvider.getApplicationContext<Context>()
         val dir = LuaCommandManager.getCommandsDir(context)
         println("===> commands directory: ${dir.absolutePath}")
         println("===> directory exists: ${dir.exists()}")
         if (dir.exists()) {
             println("===> files in dir: ${dir.list()?.joinToString()}")
         }
         // Initialize and load default commands
         val loaded = LuaCommandManager.initializeAndLoad(context)
         println("===> loaded commands count: ${loaded.size}")
         println("===> loaded keys: ${LuaCommandManager.commands.keys.joinToString()}")
    }

    @Test
    fun testInitializationLoadsDefaultCommands() {
        val commands = LuaCommandManager.commands
        assertFalse("Commands should not be empty after initialization", commands.isEmpty())
        assertTrue("Should load ping command", commands.containsKey("ping"))
        assertTrue("Should load calc command", commands.containsKey("calc"))
        assertTrue("Should load coinflip command", commands.containsKey("coinflip"))
        assertTrue("Should load roll command", commands.containsKey("roll"))
        assertTrue("Should load crypto command", commands.containsKey("crypto"))
        assertTrue("Should load stat command", commands.containsKey("stat"))
    }

    @Test
    fun testExecutePingCommand() {
        val result = LuaCommandManager.executeCommand(
            commandName = "ping",
            username = "Alex",
            userId = "987654",
            guildId = "111",
            channelId = "222",
            options = emptyMap()
        )
        assertNotNull(result)
        assertTrue("Output should contain 'pong' (Russian 'Понг')", result.content.contains("Понг"))
        assertTrue("Output should mention user 'Alex'", result.content.contains("@Alex"))
        assertFalse(result.ephemeral)
    }

    @Test
    fun testExecuteCalculatorAddition() {
        val options = mapOf(
            "num1" to 15.0,
            "operator" to "+",
            "num2" to 27.0
        )
        val result = LuaCommandManager.executeCommand(
            commandName = "calc",
            username = "CalculatorUser",
            userId = "112233",
            guildId = "111",
            channelId = "222",
            options = options
        )
        assertNotNull(result)
        assertTrue("Result should show operation results", result.content.contains("42.0") || result.content.contains("42"))
    }

    @Test
    fun testExecuteCalculatorDivisionByZero() {
        val options = mapOf(
            "num1" to 5.0,
            "operator" to "/",
            "num2" to 0.0
        )
        val result = LuaCommandManager.executeCommand(
            commandName = "calc",
            username = "User",
            userId = "123",
            guildId = "111",
            channelId = "222",
            options = options
        )
        assertNotNull(result)
        assertTrue(result.content.contains("Деление на ноль невозможно"))
        assertTrue(result.ephemeral)
    }

    @Test
    fun testExecuteStatIncrementsCounterInAndroidPrefs() {
        // Run first execution
        val result1 = LuaCommandManager.executeCommand(
            commandName = "stat",
            username = "StatUser",
            userId = "user_999",
            guildId = "111",
            channelId = "222",
            options = emptyMap()
        )
        assertNotNull(result1)
        
        // Run second execution
        val result2 = LuaCommandManager.executeCommand(
            commandName = "stat",
            username = "StatUser",
            userId = "user_999",
            guildId = "111",
            channelId = "222",
            options = emptyMap()
        )
        assertNotNull(result2)
        
        // Assert that the counter is incremented and stored correctly
        assertNotNull(result2.embeds)
        val embedStr = result2.embeds.toString()
        assertTrue("Should contain '2' as the invocation count", embedStr.contains("2"))
    }
}
