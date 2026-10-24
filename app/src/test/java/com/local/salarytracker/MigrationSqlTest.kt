package com.local.salarytracker

import com.local.salarytracker.data.MigrationSql
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** Runs the real v1 -> v2 migration SQL on a real SQLite and compares the result with Room's exported v2 schema. */
class MigrationSqlTest {
    private lateinit var db: Connection
    private val schemaDir = File("schemas/com.local.salarytracker.data.AppDatabase")

    @Before fun open() { db = DriverManager.getConnection("jdbc:sqlite::memory:") }
    @After fun close() { db.close() }

    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }

    /** createSql strings of one entity (table + indices) from an exported schema file. */
    private fun createSql(version: Int, table: String): List<String> {
        val text = File(schemaDir, "$version.json").readText()
        val start = text.indexOf("\"tableName\": \"$table\"")
        require(start >= 0) { "table $table not in $version.json" }
        val end = text.indexOf("\"tableName\":", start + 10).let { if (it < 0) text.length else it }
        val chunk = text.substring(start, end)
        return Regex("\"createSql\": \"(.*)\"").findAll(chunk).map { it.groupValues[1].replace("\${TABLE_NAME}", table) }.toList()
    }

    private fun createV1() {
        listOf("txn", "category_rule", "settings").forEach { t -> createSql(1, t).forEach(::exec) }
    }

    private data class Col(val name: String, val type: String, val notNull: Boolean, val default: String?)

    private fun columns(table: String): List<Col> = db.createStatement().use { st ->
        st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
            buildList { while (rs.next()) add(Col(rs.getString("name"), rs.getString("type"), rs.getInt("notnull") == 1, rs.getString("dflt_value"))) }
        }
    }

    private fun seedV1() {
        exec("INSERT INTO txn (amountPaise,type,timestamp,category,source,excluded,bodyHash,rawText,createdAt) VALUES (25000,'DEBIT',1000,'FOOD','SMS',0,'h1','raw one',1111)")
        exec("INSERT INTO txn (amountPaise,type,timestamp,category,source,excluded,bodyHash,rawText,createdAt) VALUES (9900,'DEBIT',2000,'TRAVEL','NOTIFICATION',0,'h2','raw two',2222)")
        exec("INSERT INTO txn (amountPaise,type,timestamp,category,source,excluded,createdAt) VALUES (500,'DEBIT',3000,'OTHER','MANUAL',0,3333)")
        exec("INSERT INTO category_rule (keyword,category) VALUES ('swiggy','FOOD')")
    }

    @Test fun migrationKeepsDataAndBackfillsUuids() {
        createV1()
        seedV1()
        MigrationSql.V1_TO_V2.forEach(::exec)

        val ids = mutableListOf<String>()
        db.createStatement().use { st ->
            st.executeQuery("SELECT remoteId, updatedAt, deleted, dirty, rawText, createdAt FROM txn ORDER BY id").use { rs ->
                while (rs.next()) {
                    ids += rs.getString("remoteId")
                    assertEquals(rs.getLong("createdAt"), rs.getLong("updatedAt"))
                    assertEquals(0, rs.getInt("deleted"))
                    assertEquals(1, rs.getInt("dirty"))
                }
            }
        }
        assertEquals(3, ids.size)
        assertEquals("all remote ids are unique", 3, ids.toSet().size)
        val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        ids.forEach { assertTrue("not a v4 uuid: $it", uuid.matches(it)) }

        db.createStatement().use { st ->
            st.executeQuery("SELECT rawText FROM txn WHERE bodyHash='h1'").use { rs -> rs.next(); assertEquals("raw one", rs.getString(1)) }
            st.executeQuery("SELECT updatedAt, deleted, dirty FROM category_rule").use { rs ->
                rs.next()
                assertTrue(rs.getLong("updatedAt") > 1_600_000_000_000L)
                assertEquals(0, rs.getInt("deleted"))
                assertEquals(1, rs.getInt("dirty"))
            }
        }
    }

    @Test fun remoteIdIsUnique() {
        createV1()
        seedV1()
        MigrationSql.V1_TO_V2.forEach(::exec)
        val first = db.createStatement().use { st -> st.executeQuery("SELECT remoteId FROM txn LIMIT 1").use { it.next(); it.getString(1) } }
        val err = runCatching {
            exec("INSERT INTO txn (amountPaise,type,timestamp,category,source,excluded,createdAt,remoteId) VALUES (1,'DEBIT',1,'OTHER','MANUAL',0,1,'$first')")
        }
        assertTrue("duplicate remoteId must be rejected", err.isFailure)
    }

    @Test fun migratedSchemaMatchesRoomsExportedV2Schema() {
        createV1()
        MigrationSql.V1_TO_V2.forEach(::exec)
        val migrated = mapOf("txn" to columns("txn"), "category_rule" to columns("category_rule"))

        db.close()
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        listOf("txn", "category_rule").forEach { t -> createSql(2, t).forEach(::exec) }
        val fresh = mapOf("txn" to columns("txn"), "category_rule" to columns("category_rule"))

        for (t in migrated.keys) {
            val a = migrated.getValue(t).sortedBy { it.name }
            val b = fresh.getValue(t).sortedBy { it.name }
            assertEquals("columns of $t", b, a)
        }
    }

    @Test fun uuidExpressionGivesDistinctValuesPerRow() {
        exec("CREATE TABLE t (id INTEGER PRIMARY KEY, u TEXT)")
        repeat(200) { exec("INSERT INTO t (id) VALUES ($it)") }
        exec("UPDATE t SET u = ${MigrationSql.UUID_EXPR}")
        val n = db.createStatement().use { st -> st.executeQuery("SELECT COUNT(DISTINCT u) FROM t").use { it.next(); it.getInt(1) } }
        assertEquals(200, n)
    }

    // ------------------------------------------------------------ v2 -> v3 (Lending)
    private fun createV2() {
        listOf("txn", "category_rule", "settings").forEach { t -> createSql(2, t).forEach(::exec) }
    }

    private fun count(table: String): Int =
        db.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM `$table`").use { it.next(); it.getInt(1) } }

    @Test fun v2DataSurvivesTheLendingMigration() {
        createV2()
        exec("INSERT INTO txn (amountPaise,type,timestamp,category,source,excluded,bodyHash,rawText,createdAt,remoteId,updatedAt,deleted,dirty) VALUES (25000,'DEBIT',1000,'FOOD','SMS',0,'h1','raw one',1111,'r-1',1111,0,0)")
        exec("INSERT INTO txn (amountPaise,type,timestamp,category,source,excluded,createdAt,remoteId,updatedAt,deleted,dirty) VALUES (500,'CREDIT',3000,'OTHER','MANUAL',0,3333,'r-2',3333,1,1)")
        exec("INSERT INTO category_rule (keyword,category,updatedAt,deleted,dirty) VALUES ('swiggy','FOOD',5,0,0)")

        MigrationSql.V2_TO_V3.forEach(::exec)

        assertEquals(2, count("txn"))
        assertEquals(1, count("category_rule"))
        assertEquals(0, count("person"))
        assertEquals(0, count("loan_entry"))
        db.createStatement().use { st ->
            st.executeQuery("SELECT remoteId, rawText, dirty FROM txn WHERE bodyHash='h1'").use { rs ->
                rs.next()
                assertEquals("r-1", rs.getString("remoteId"))
                assertEquals("raw one", rs.getString("rawText"))
                assertEquals(0, rs.getInt("dirty"))
            }
            st.executeQuery("SELECT deleted, dirty FROM txn WHERE remoteId='r-2'").use { rs ->
                rs.next()
                assertEquals(1, rs.getInt("deleted"))
                assertEquals(1, rs.getInt("dirty"))
            }
        }
        // the new tables are usable
        exec("INSERT INTO person (remoteId,name,createdAt,updatedAt,deleted,dirty) VALUES ('p-1','Ramesh',1,1,0,1)")
        exec("INSERT INTO loan_entry (remoteId,personRemoteId,kind,amountPaise,method,occurredAt,updatedAt,deleted,dirty) VALUES ('e-1','p-1','GAVE',100,'CASH',1,1,0,1)")
        assertEquals(1, count("person"))
        assertEquals(1, count("loan_entry"))
    }

    @Test fun lendingMigrationIsRepeatableAndKeepsUniqueRemoteIds() {
        createV2()
        MigrationSql.V2_TO_V3.forEach(::exec)
        MigrationSql.V2_TO_V3.forEach(::exec) // IF NOT EXISTS: running twice must not fail
        exec("INSERT INTO person (remoteId,name,createdAt,updatedAt,deleted,dirty) VALUES ('p-1','A',1,1,0,1)")
        val dup = runCatching { exec("INSERT INTO person (remoteId,name,createdAt,updatedAt,deleted,dirty) VALUES ('p-1','B',1,1,0,1)") }
        assertTrue("duplicate person remoteId must be rejected", dup.isFailure)
    }

    @Test fun migratedLendingTablesMatchRoomsExportedV3Schema() {
        createV2()
        MigrationSql.V2_TO_V3.forEach(::exec)
        val tables = listOf("txn", "category_rule", "settings", "person", "loan_entry")
        val migrated = tables.associateWith { columns(it) }
        val migratedIndexes = indexNames()

        db.close()
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        tables.forEach { t -> createSql(3, t).forEach(::exec) }
        val fresh = tables.associateWith { columns(it) }

        for (t in tables) {
            assertEquals("columns of $t", fresh.getValue(t).sortedBy { it.name }, migrated.getValue(t).sortedBy { it.name })
        }
        assertEquals("indices", indexNames(), migratedIndexes)
        assertTrue(indexNames().containsAll(listOf("index_person_remoteId", "index_loan_entry_remoteId", "index_loan_entry_personRemoteId")))
    }

    private fun indexNames(): List<String> = db.createStatement().use { st ->
        st.executeQuery("SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'index_%' ORDER BY name").use { rs ->
            buildList { while (rs.next()) add(rs.getString(1)) }
        }
    }
}
