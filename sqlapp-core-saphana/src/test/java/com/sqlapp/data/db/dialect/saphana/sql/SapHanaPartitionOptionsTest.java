/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SapHanaPartitionOptionsTest {
	private Table table() {
		var table = new Table("T");
		table.getColumns().add("A", c -> c.setDataType(DataType.INT).setNullable(false));
		table.toPartitioning().getPartitioning().setPartitioningType(PartitioningType.Range);
		table.getPartitioning().getPartitioningColumns().add("A");
		table.getPartitioning().getPartitions().add(new Partition("P").setLowValue("0").setHighValue("10"));
		table.getPartitioning().getPartitions().add(new Partition("OTHER"));
		return table;
	}

	private String sql(Table table) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).getFirst()
				.getSqlText();
	}

	@Test
	void preservesDynamicThresholdAndInsertAcrossXml() throws Exception {
		var table = table();
		table.getPartitioning().getPartitions().getFirst().getSpecifics().put("INSERT", "FALSE");
		var other = table.getPartitioning().getPartitions().getLast();
		other.getSpecifics().put("IS_DYNAMIC", "TRUE");
		other.getSpecifics().put("INSERT", "TRUE");
		other.getStatistics().put("DYNAMIC_RANGE_THRESHOLD", 5000000000L);
		String ddl = sql(table);
		assertTrue(ddl.contains("10 INSERT OFF"), ddl);
		assertTrue(ddl.contains("OTHERS DYNAMIC THRESHOLD 5000000000 INSERT ON"), ddl);
		var restored = (Table) SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		assertEquals(ddl, sql(restored));
		other.getSpecifics().put("IS_DYNAMIC", "FALSE");
		assertFalse(sql(table).contains(" DYNAMIC"));
	}

	@Test
	void rejectsInvalidPoliciesAndConflictingInterval() {
		var table = table();
		var other = table.getPartitioning().getPartitions().getLast();
		other.getSpecifics().put("IS_DYNAMIC", "TRUE");
		other.getStatistics().put("DYNAMIC_RANGE_THRESHOLD", 0);
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		other.getStatistics().put("DYNAMIC_RANGE_THRESHOLD", 10);
		table.getColumns().getFirst().setNullable(true);
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		table.getColumns().getFirst().setNullable(false);
		other.setLowValue("10").setHighValue("20");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		other.setLowValue((String) null).setHighValue((String) null);
		other.getSpecifics().put("DYNAMIC_RANGE_INTERVAL", "2 YEAR");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		other.getSpecifics().remove("DYNAMIC_RANGE_INTERVAL");
		other.getSpecifics().put("INSERT", "maybe");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
	}

	@Test
	void rendersValidatedIntervalsForIntegerAndCalendarKeys() {
		var table = table();
		var other = table.getPartitioning().getPartitions().getLast();
		other.getSpecifics().put("IS_DYNAMIC", "TRUE");
		other.getSpecifics().put("DYNAMIC_RANGE_INTERVAL", "10");
		assertTrue(sql(table).contains("OTHERS DYNAMIC INTERVAL 10"));
		table.getColumns().getFirst().setDataType(DataType.TIMESTAMP);
		for (String function : java.util.List.of("YEAR", "MONTH", "HOUR")) {
			table.getPartitioning().getPartitioningColumns().clear();
			table.getPartitioning().getPartitioningColumns().add(function + "(A)");
			table.getPartitioning().getPartitions().getFirst()
					.setLowValue(function.equals("HOUR") ? "2024-01-01 00" : "2024")
					.setHighValue(function.equals("HOUR") ? "2024-01-02 00" : "2025");
			other.getSpecifics().put("DYNAMIC_RANGE_INTERVAL", "2 " + function);
			assertTrue(sql(table).contains("OTHERS DYNAMIC INTERVAL 2 " + function));
		}
		for (String interval : java.util.List.of("0 HOUR", "-1 HOUR", "2 YEAR", "2 HOUR); DROP TABLE T",
				"999999999999999999999999 HOUR")) {
			other.getSpecifics().put("DYNAMIC_RANGE_INTERVAL", interval);
			assertThrows(IllegalArgumentException.class, () -> sql(table), interval);
		}
	}

	@Test
	void preservesPerParentOverridesInHashRange() {
		var table = table();
		var p = table.getPartitioning();
		p.setPartitioningType(PartitioningType.Hash).setPartitionSize(2).setSubPartitioningType(PartitioningType.Range);
		p.getSubPartitioningColumns().add("A");
		for (var parent : p.getPartitions()) {
			parent.setLowValue((String) null).setHighValue((String) null);
			parent.getSubPartitions().add(new SubPartition("S").setLowValue("0").setHighValue("10"));
			parent.getSubPartitions().add(new SubPartition("O"));
		}
		p.getPartitions().getFirst().getSubPartitions().getFirst().getSpecifics().put("INSERT", "OFF");
		String ddl = sql(table);
		assertTrue(ddl.contains("SUBPARTITION BY RANGE"), ddl);
		assertEquals(1, ddl.split("INSERT OFF", -1).length - 1, ddl);
		p.getPartitions().getFirst().getSpecifics().put("INSERT", "OFF");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
	}

	@Test
	void preservesMemoryAndQuotedGroupPropertiesAndRejectsUnmodeledPlacement() {
		var table = table();
		var range = table.getPartitioning().getPartitions().getFirst();
		range.getSpecifics().put("PERSISTENT_MEMORY", "FALSE");
		range.getSpecifics().put("GROUP_NAME", "a'b");
		range.getSpecifics().put("GROUP_TYPE", "T");
		range.getSpecifics().put("SUB_TYPE", "S");
		String ddl = sql(table);
		assertTrue(ddl.contains("PERSISTENT MEMORY OFF GROUP NAME 'a''b' GROUP TYPE 'T' GROUP SUBTYPE 'S'"), ddl);
		for (String key : java.util.List.of("STORAGE_TYPE", "NUMA_NODE_INDEXES", "DYNAMIC_DISTANCE_INTERVAL",
				"DYNAMIC_DISTANCE_LOAD_UNIT", "DYNAMIC_RANGE_DIRECTION", "IS_MOVABLE")) {
			range.getSpecifics().put(key, "unsupported");
			assertThrows(IllegalArgumentException.class, () -> sql(table), key);
			range.getSpecifics().remove(key);
		}
		range.getSpecifics().put("STORAGE_TYPE", "DEFAULT");
		range.getSpecifics().put("IS_MOVABLE", "DEFAULT");
		range.getSpecifics().put("DYNAMIC_RANGE_DIRECTION", "INCREASING");
		assertDoesNotThrow(() -> sql(table));
	}

	@Test
	void preservesIntegerAndCalendarAgingWithStrictUnits() throws Exception {
		var table = table();
		var other = table.getPartitioning().getPartitions().getLast();
		other.getSpecifics().put("IS_DYNAMIC", "TRUE");
		other.getStatistics().put("DYNAMIC_RANGE_THRESHOLD", 1000000);
		other.getSpecifics().put("DYNAMIC_DISTANCE_INTERVAL", "20");
		assertTrue(sql(table).contains("DYNAMIC THRESHOLD 1000000 DISTANCE 20 PAGE LOADABLE"));
		assertEquals(sql(table), sql((Table) SchemaUtils.readXml(new java.io.StringReader(table.asXml()))));
		other.getSpecifics().put("DYNAMIC_DISTANCE_LOAD_UNIT", "COLUMN");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		other.getSpecifics().put("DYNAMIC_DISTANCE_LOAD_UNIT", "PAGE");
		table.getColumns().getFirst().setDataType(DataType.TIMESTAMP);
		table.getPartitioning().getPartitioningColumns().clear();
		table.getPartitioning().getPartitioningColumns().add("YEAR(A)");
		other.getStatistics().clear();
		other.getSpecifics().put("DYNAMIC_RANGE_INTERVAL", "2 YEAR");
		other.getSpecifics().put("DYNAMIC_DISTANCE_INTERVAL", "3 YEAR");
		assertTrue(sql(table).contains("DYNAMIC INTERVAL 2 YEAR DISTANCE 3 YEAR PAGE LOADABLE"));
		for (String value : java.util.List.of("0 YEAR", "2 MONTH", "1 YEAR); DROP TABLE T",
				"999999999999999999999999 YEAR")) {
			other.getSpecifics().put("DYNAMIC_DISTANCE_INTERVAL", value);
			assertThrows(IllegalArgumentException.class, () -> sql(table));
		}
	}

	@Test
	void rejectsInactiveAgingAndHonorsNearestLoadUnitOverride() {
		var table = table();
		var other = table.getPartitioning().getPartitions().getLast();
		other.getSpecifics().put("DYNAMIC_DISTANCE_INTERVAL", "10");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		other.getSpecifics().put("IS_DYNAMIC", "TRUE");
		table.getSpecifics().put("LOAD_UNIT", "COLUMN");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		other.getSpecifics().put("LOAD_UNIT", "PAGE");
		assertDoesNotThrow(() -> sql(table));
		other.getSpecifics().put("LOAD_UNIT", "COLUMN");
		table.getSpecifics().remove("LOAD_UNIT");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		other.getSpecifics().remove("LOAD_UNIT");
		other.getSpecifics().remove("DYNAMIC_DISTANCE_INTERVAL");
		other.getSpecifics().put("DYNAMIC_DISTANCE_LOAD_UNIT", "PAGE");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
	}

	@Test
	void preservesNumaRangesAndRejectsMalformedOrUnsupportedHashPlacement() {
		var table = table();
		var first = table.getPartitioning().getPartitions().getFirst();
		first.getSpecifics().put("NUMA_NODE_INDEXES", "0, 1 to 3, 6");
		first.getSpecifics().put("PERSISTENT_MEMORY", "OFF");
		assertTrue(sql(table).contains("PERSISTENT MEMORY OFF NUMA NODE ('0', '1' TO '3', '6')"));
		first.getSpecifics().put("NUMA_NODE_INDEXES", "0TO1,3");
		assertTrue(sql(table).contains("NUMA NODE ('0' TO '1', '3')"));
		for (String value : java.util.List.of("-1", "3 TO 1", "0,", "1); DROP TABLE T", "999999999999999")) {
			first.getSpecifics().put("NUMA_NODE_INDEXES", value);
			assertThrows(IllegalArgumentException.class, () -> sql(table));
		}
		first.getSpecifics().put("NUMA_NODE_INDEXES", "0");
		first.getSpecifics().put("INSERT", "OFF");
		assertTrue(sql(table).contains("INSERT OFF PERSISTENT MEMORY OFF NUMA NODE ('0')"));
		first.getSpecifics().remove("INSERT");
		table.getPartitioning().setPartitioningType(PartitioningType.Hash).setPartitionSize(2);
		assertThrows(IllegalArgumentException.class, () -> sql(table));
	}

	@Test
	void recreatesCompleteSingleLevelHashNumaPlacementAfterCreate() {
		var table = table();
		table.getPartitioning().setPartitioningType(PartitioningType.Hash).setPartitionSize(2);
		for (var bucket : table.getPartitioning().getPartitions()) {
			bucket.setLowValue((String) null).setHighValue((String) null);
			bucket.getSpecifics().put("NUMA_NODE_INDEXES", "0");
		}
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var operations = registry.createSql(table, SqlType.CREATE);
		assertEquals(3, operations.size());
		assertTrue(operations.get(1).getSqlText().contains("ALTER PARTITION 1 NUMA NODE ('0')"));
		assertTrue(operations.get(2).getSqlText().contains("ALTER PARTITION 2 NUMA NODE ('0')"));
		table.getPartitioning().getPartitions().removeLast();
		// The shared model derives HASH count from a nonempty bucket list.
		assertEquals(2, registry.createSql(table, SqlType.CREATE).size());
		table.getPartitioning().getPartitions().getFirst().setLowValue("0");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}
}
