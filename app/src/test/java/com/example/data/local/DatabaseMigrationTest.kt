package com.example.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DatabaseMigrationTest {

    @Test
    fun `migration 3 to 4 updates default port 8080 to 3030 and clears apiKey`() {
        val executedSql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        MIGRATION_3_4.migrate(db)

        assertEquals(2, executedSql.size)
        assertTrue(executedSql[0].contains("UPDATE gateway_settings SET phoneServerPort = 3030 WHERE phoneServerPort = 8080"))
        assertTrue(executedSql[1].contains("UPDATE gateway_settings SET phoneServerApiKey = ''"))
    }

    @Test
    fun `gateway settings default port is 3030`() {
        val settings = GatewaySettings()
        assertEquals(3030, settings.phoneServerPort)
        assertEquals("", settings.phoneServerApiKey)
        assertEquals("", settings.serverUrl)
    }
}
