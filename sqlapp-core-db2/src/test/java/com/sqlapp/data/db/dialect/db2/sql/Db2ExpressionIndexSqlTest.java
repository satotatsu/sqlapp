/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class Db2ExpressionIndexSqlTest {
	private Table table() {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add("NAME", c -> c.setDataType(DataType.VARCHAR).setLength(30));
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		Index index = new Index("IX").setSchemaName("APP").setTableName("T");
		ReferenceColumn expression = new ReferenceColumn("SYS_EXPR").setOrder(Order.Desc);
		expression.getStatement().add("UPPER(\"NAME\")");
		index.getColumns().add(expression);
		index.getColumns().add("ID", c -> c.getSpecifics().put("DB2_COLORDER", "R"));
		table.getIndexes().add(index);
		return table;
	}

	private List<String> sql(Index index) {
		var registry = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		SqlFactory<Index> factory = registry.getSqlFactory(index, SqlType.CREATE);
		return factory.createSql(index).stream().map(o -> o.getSqlText()).toList();
	}

	@Test
	void xmlAndCloningRetainRawExpressionsRandomOrderAndPhysicalOptions() throws Exception {
		Table table = table();
		Index index = table.getIndexes().getFirst();
		index.getSpecifics().putAll(Map.of("INDEXTYPE", "REG", "PCTFREE", "10", "MINPCTUSED", "20", "REVERSE_SCANS",
				"N", "COMPRESSION", "Y", "NULLKEYS", "N", "FUNC_PATH", "\"SYSIBM\",\"APP\""));
		index.setCompression(true);
		index.setRemarks("index's comment");
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		assertEquals(table, restored);
		assertEquals(table, table.clone());
		var sql = sql(restored.getIndexes().getFirst());
		assertEquals("SET CURRENT PATH = \"SYSIBM\", \"APP\"", sql.getFirst());
		assertTrue(sql.get(1).contains("(UPPER(\"NAME\")) DESC"), sql.toString());
		assertTrue(sql.get(1).contains("ID RANDOM"), sql.toString());
		assertTrue(
				sql.get(1).contains("PCTFREE 10 MINPCTUSED 20 DISALLOW REVERSE SCANS COMPRESS YES EXCLUDE NULL KEYS"));
		assertEquals("COMMENT ON INDEX \"APP\".\"IX\" IS 'index''s comment'", sql.getLast());
		assertFalse(sql.get(1).contains("SYS_EXPR"));
	}

	@Test
	void includeExpressionsAndCompleteSpecializedDefinitionsAreSupported() {
		Index index = table().getIndexes().getFirst();
		index.setUnique(true);
		index.getColumns().get(1).getSpecifics().clear();
		ReferenceColumn included = new ReferenceColumn("INCLUDED_EXPR").setIncludedColumn(true);
		included.getStatement().add("LOWER(\"NAME\")");
		index.getColumns().add(included);
		assertTrue(sql(index).getFirst().contains("INCLUDE ((LOWER(\"NAME\"))"), sql(index).toString());
		index.getSpecifics().put("INDEXTYPE", "XVIP");
		assertThrows(IllegalArgumentException.class, () -> sql(index));
		index.getDefinition()
				.add("CREATE INDEX APP.IX ON APP.T (ID) GENERATE KEY USING XMLPATTERN '/a' AS SQL INTEGER");
		assertEquals(index.getDefinition(), sql(index));
	}

	@Test
	void unsupportedOrdersVirtualKeysAndMalformedOptionsFailInsteadOfBecomingOrdinaryColumns() {
		Index index = table().getIndexes().getFirst();
		index.getSpecifics().put("PCTFREE", "100");
		assertThrows(IllegalArgumentException.class, () -> sql(index));
		index.getSpecifics().clear();
		index.getColumns().getFirst().getSpecifics().put("DB2_VIRTUAL", "S");
		assertThrows(IllegalArgumentException.class, () -> sql(index));
		index.getColumns().getFirst().getSpecifics().clear();
		index.getColumns().getFirst().getStatement().add("SECOND EXPRESSION");
		assertThrows(IllegalArgumentException.class, () -> sql(index));
		SqlFactory<Index> old = DialectHolder.Db2_1050Dialect.createSqlFactoryRegistry().getSqlFactory(index,
				SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> old.createSql(index));
	}
}
