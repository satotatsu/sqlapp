/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.PublicSynonym;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.SqlParser;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SapHanaDialectFeatureTest {
	private final List<Dialect> dialects = List.of(DialectHolder.defaultDialect, DialectHolder.cloudDialect);

	@Test
	void reportsHanaCapabilities() {
		for (Dialect dialect : dialects) {
			assertTrue(dialect.supportsColumnFormula());
			assertEquals(65535, dialect.getMaxStatementParameterCount());
			assertTrue(dialect.supportsSequence());
			assertEquals("SELECT SEQ1.NEXTVAL FROM DUMMY", dialect.getSequenceNextValString("SEQ1"));
			assertTrue(dialect.supportsLimit());
			assertTrue(dialect.supportsLimitOffset());
			assertTrue(dialect.supportsWith());
			assertTrue(dialect.supportsSchema());
			assertFalse(dialect.supportsCatalog());
			for (CascadeRule rule : CascadeRule.values()) {
				assertTrue(dialect.supportsRuleOnDelete(rule), rule.toString());
				assertTrue(dialect.supportsRuleOnUpdate(rule), rule.toString());
			}
			assertEquals("CURRENT_DATE", dialect.getCurrentDateFunction());
			assertEquals("CURRENT_TIMESTAMP", dialect.getCurrentDateTimeFunction());
		}
	}

	@Test
	void usesCurrentDateTimeFunctionsAsTypeDefaults() {
		for (Dialect dialect : dialects) {
			assertEquals("CURRENT_DATE", dialect.getDbDataTypes().getDbType(DataType.DATE).getDefaultValueLiteral());
			assertEquals("CURRENT_TIMESTAMP",
					dialect.getDbDataTypes().getDbType(DataType.DATETIME).getDefaultValueLiteral());
			assertEquals("CURRENT_TIMESTAMP",
					dialect.getDbDataTypes().getDbType(DataType.TIMESTAMP).getDefaultValueLiteral());
			assertEquals("CURRENT_TIME", dialect.getDbDataTypes().getDbType(DataType.TIME).getDefaultValueLiteral());
		}
	}

	@Test
	void skipsGeneratedColumnsInDml() {
		for (Dialect dialect : dialects) {
			Table table = new Table("ORDERS").setDialect(dialect);
			table.getColumns().add("ID", c -> c.setDataType(DataType.INT).setNotNull(true));
			table.getColumns().add("PRICE", c -> c.setDataType(DataType.INT));
			table.getColumns().add("TOTAL", c -> c.setDataType(DataType.INT).setFormula("PRICE * 2"));
			table.setPrimaryKey(table.getColumns().get("ID"));
			var registry = dialect.createSqlFactoryRegistry();
			String insert = registry.createSql(table, SqlType.INSERT).getFirst().getSqlText();
			assertFalse(insert.contains("TOTAL"), insert);
			assertFalse(insert.contains("PRICE * 2"), insert);
			String update = registry.createSql(table, SqlType.UPDATE).getFirst().getSqlText();
			assertFalse(update.contains("TOTAL"), update);
			String merge = registry.createSql(table, SqlType.MERGE).getFirst().getSqlText();
			assertFalse(merge.contains("PRICE * 2"), merge);
			assertFalse(merge.contains("AS TOTAL"), merge);
			assertTrue(merge.contains("/*ID*/0 AS ID"), merge);
			assertFalse(merge.contains("_source_ ("), merge);
			assertFalse(merge.contains("_target_.TOTAL"), merge);
			String ddl = registry.createSql(table, SqlType.CREATE).getFirst().getSqlText();
			assertTrue(ddl.contains("GENERATED ALWAYS AS (PRICE * 2)"), ddl);
		}
	}

	@Test
	void pagesSelectForAppWithLimitOffset() {
		for (Dialect dialect : dialects) {
			Table table = new Table("ITEMS").setDialect(dialect);
			table.getColumns().add("ID", c -> c.setDataType(DataType.INT).setNotNull(true));
			table.setPrimaryKey(table.getColumns().get("ID"));
			String sql = dialect.createSqlFactoryRegistry().createSql(table, SqlType.SELECT_FOR_APP).getFirst()
					.getSqlText();
			assertFalse(sql.contains("FETCH FIRST"), sql);
			assertEquals("LIMIT ? OFFSET ?", paging(dialect, sql, 10, 20));
			assertEquals("LIMIT ?", paging(dialect, sql, 10, null));
			assertEquals("LIMIT " + Integer.MAX_VALUE + " OFFSET ?", paging(dialect, sql, null, 20));
			assertEquals("", paging(dialect, sql, null, null));
			ParametersContext count = context(10, 20);
			count.put(ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME, true);
			String countSql = eval(dialect, sql, count);
			assertTrue(countSql.toLowerCase().contains("count(*)"), countSql);
			assertFalse(countSql.contains("LIMIT"), countSql);
			assertFalse(countSql.contains("OFFSET"), countSql);
		}
	}

	private String paging(Dialect dialect, String sql, Integer rows, Integer offset) {
		String rendered = eval(dialect, sql, context(rows, offset)).replaceAll("\\s+", " ").trim();
		int index = rendered.indexOf("LIMIT");
		return index < 0 ? "" : rendered.substring(index);
	}

	private ParametersContext context(Integer rows, Integer offset) {
		ParametersContext context = new ParametersContext();
		context.put(ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME, false);
		if (rows != null) {
			context.put(ParameterDefinition.ROW_KEY_PARANETER_NAME, rows);
		}
		if (offset != null) {
			context.put(ParameterDefinition.OFFSET_KEY_PARANETER_NAME, offset);
		}
		return context;
	}

	private String eval(Dialect dialect, String sql, ParametersContext context) {
		SqlNode node = SqlParser.getInstance().parse(dialect, sql);
		return node.eval(context).getSql();
	}

	@Test
	void createsAndDropsPublicSynonyms() {
		for (Dialect dialect : dialects) {
			var registry = dialect.createSqlFactoryRegistry();
			PublicSynonym synonym = new PublicSynonym("ITEM_ALIAS").setObjectSchemaName("Odd Schema")
					.setObjectName("ITEMS");
			assertEquals("CREATE PUBLIC SYNONYM ITEM_ALIAS FOR \"Odd Schema\".ITEMS",
					registry.createSql(synonym, SqlType.CREATE).getFirst().getSqlText());
			assertEquals("DROP PUBLIC SYNONYM ITEM_ALIAS",
					registry.createSql(synonym, SqlType.DROP).getFirst().getSqlText());
			synonym.setDbLinkName("DB2");
			assertEquals("CREATE PUBLIC SYNONYM ITEM_ALIAS FOR DB2.\"Odd Schema\".ITEMS",
					registry.createSql(synonym, SqlType.CREATE).getFirst().getSqlText());
			synonym.setObjectSchemaName(null);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(synonym, SqlType.CREATE));
			synonym.setDbLinkName(null).setObjectName(null);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(synonym, SqlType.CREATE));
		}
	}

	@Test
	void omitsSystemGeneratedConstraintNames() {
		for (Dialect dialect : dialects) {
			Table table = new Table("ITEMS").setDialect(dialect);
			table.getColumns().add("ID", c -> c.setDataType(DataType.INT).setNotNull(true));
			table.getColumns().add("CODE", c -> c.setDataType(DataType.INT));
			table.getConstraints().addUniqueConstraint("_SYS_TREE_CS_#158276_#0_#P0", true,
					table.getColumns().get("ID"));
			table.getConstraints().addUniqueConstraint("ITEMS_CODE_UK", false, table.getColumns().get("CODE"));
			String sql = dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).getFirst().getSqlText();
			assertFalse(sql.contains("_SYS_"), sql);
			assertTrue(sql.contains(", PRIMARY KEY ( ID )"), sql);
			assertTrue(sql.contains(", CONSTRAINT ITEMS_CODE_UK UNIQUE ( CODE )"), sql);
		}
	}

	@Test
	void columnWithoutFormulaIsStillInserted() {
		Table table = new Table("T").setDialect(DialectHolder.defaultDialect);
		table.getColumns().add(new Column("A").setDataType(DataType.INT));
		String insert = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.INSERT)
				.getFirst().getSqlText();
		assertTrue(insert.contains("A"), insert);
	}
}
