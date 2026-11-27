/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class Db2RangePartitionSqlTest {
	private Table table() {
		var table = new Table("T").setSchemaName("APP");
		table.getColumns().add("K.Name", c -> c.setDataType(DataType.INT));
		var p = new Partitioning(PartitioningType.Range);
		table.setPartitioning(p);
		p.getPartitioningColumns().add(table.getColumns().getFirst(), NullsOrder.NullsFirst);
		p.getPartitions().add(new Partition("P").setLowValue("1").setHighValue("10"));
		return table;
	}

	private String sql(Table table) {
		var registry = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setCreateIfNotExists(false);
		SqlFactory<Table> factory = registry.getSqlFactory(table, SqlType.CREATE);
		return factory.createSql(table).getFirst().getSqlText();
	}

	@Test
	void allFiniteBoundaryCombinationsPreserveExactMembershipAfterXml() throws Exception {
		for (boolean low : List.of(false, true))
			for (boolean high : List.of(false, true)) {
				var table = table();
				var p = table.getPartitioning().getPartitions().getFirst();
				p.setLowValueInclusive(low);
				p.setHighValueInclusive(high);
				p.setTableSpaceName("Data.Space");
				p.setIndexTableSpaceName("Index.Space");
				p.setLobTableSpaceName("Lob.Space");
				Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
				String sql = sql(restored);
				assertTrue(sql.contains("\"K.Name\" NULLS FIRST"), sql);
				assertTrue(sql.contains("STARTING FROM (1)" + (low ? " ENDING" : " EXCLUSIVE ENDING")), sql);
				assertTrue(sql.contains("ENDING AT (10)" + (high ? " IN" : " EXCLUSIVE IN")), sql);
				assertTrue(sql.contains("INDEX IN \"Index.Space\" LONG IN \"Lob.Space\""), sql);
				assertEquals(table, table.clone());
			}
	}

	@Test
	void sqlExpressionsAndNullsLastAreEmittedAsSyntaxAndInfiniteBoundsHaveNoExclusiveClause() {
		var table = table();
		var key = table.getPartitioning().getPartitioningColumns().getFirst();
		key.getStatement().add("YEAR(\"CREATED\")");
		key.setNullsOrder(NullsOrder.NullsLast);
		var p = table.getPartitioning().getPartitions().getFirst();
		p.setLowValue("MINVALUE");
		p.setHighValue("MAXVALUE");
		p.setLowValueInclusive(false);
		p.setHighValueInclusive(false);
		String sql = sql(table);
		assertTrue(sql.contains("YEAR(\"CREATED\") NULLS LAST"), sql);
		assertFalse(sql.contains("EXCLUSIVE"), sql);
	}

	@Test
	void nonRangeSubpartitioningMissingKeysAndBoundsFailClearly() {
		var table = table();
		var p = table.getPartitioning();
		p.setPartitioningType(PartitioningType.Hash);
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		p.setPartitioningType(PartitioningType.Range);
		p.setSubPartitioningType(PartitioningType.Range);
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		p.setSubPartitioningType((PartitioningType) null);
		p.getPartitioningColumns().getFirst().setOrder(Order.Desc);
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		p.getPartitioningColumns().getFirst().setOrder((Order) null);
		p.getPartitions().getFirst().setHighValue((String) null);
		assertThrows(IllegalArgumentException.class, () -> sql(table));
	}

	@Test
	void unsupportedVersionsAndPerPartitionIndexSpaceAreRejected() {
		var table = table();
		var p = table.getPartitioning();
		SqlFactory<Partitioning> legacy = DialectHolder.defaultDialect.createSqlFactoryRegistry().getSqlFactory(p,
				SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> legacy.createSql(p));
		p.getPartitions().getFirst().setIndexTableSpaceName("IDX");
		SqlFactory<Partitioning> old = DialectHolder.Db2_950Dialect.createSqlFactoryRegistry().getSqlFactory(p,
				SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> old.createSql(p));
	}

	@Test
	void completeTableDefinitionsAreAuthoritativeForUnsupportedOrganizations() throws Exception {
		var table = table();
		table.getPartitioning().setPartitioningType(PartitioningType.Hash);
		String ddl = "CREATE TABLE APP.T (\"K.Name\" INT) DISTRIBUTE BY HASH (\"K.Name\")";
		table.getDefinition().add(ddl);
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		assertEquals(ddl, sql(restored));
		assertEquals(table.getDefinition(), restored.getDefinition());
	}
}
