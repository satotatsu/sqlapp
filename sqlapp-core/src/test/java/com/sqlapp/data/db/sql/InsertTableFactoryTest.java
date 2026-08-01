/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

public class InsertTableFactoryTest extends AbstractStandardFactoryTest {
	SqlFactory<Table> operationfactory;

	@BeforeEach
	public void before() {
		operationfactory = sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.INSERT);
		operationfactory.getTableOptions().setCreatedAtColumn(c -> "colD".equalsIgnoreCase(c.getName()));
	}

	@Test
	public void testGetDdlTable() {
		String sql = """
				INSERT INTO "tableA"
				(
					  "colA"
					, "colB"
					, "colC"
					, "colD"
				)
				VALUES
				(
					  /*colA*/0
					, /*colB*/0
					, /*colC*/'0'
					, CURRENT_TIMESTAMP
				)""";
		final Table table = createTable();
		final List<SqlOperation> list = operationfactory.createSql(table);
		final SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		assertEquals(sql, commandText.getSqlText());
	}

	@Test
	public void testLegacyAddInsertIntoTableOverrideRemainsExtensionPoint() {
		LegacyInsertFactory factory = new LegacyInsertFactory();
		factory.setSqlFactoryRegistry(sqlFactoryRegistry);

		factory.createSql(createTable());

		assertTrue(factory.legacyOverrideCalled);
	}

	@Test
	public void testGetInsertRowsSql() {
		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.INSERT_ROWS);
		factory.getTableOptions().setCreatedAtColumn(c -> "colD".equalsIgnoreCase(c.getName()));
		String sql = """
				INSERT INTO "tableA"
				(
					  "colA"
					, "colB"
					, "colC"
					, "colD"
				)
				/*VALUES*/VALUES
				(
					  /*colA*/0
					, /*colB*/0
					, /*colC*/'0'
					, CURRENT_TIMESTAMP
				)/*END*/""";

		SqlOperation operation = CommonUtils.first(factory.createSql(createTable()));

		assertEquals(SqlType.INSERT_ROWS, operation.getSqlType());
		assertEquals(sql, operation.getSqlText());
	}

	@Test
	public void testInsertRowsBindsEveryRowInOneStatement() {
		Table table = createTable();
		for (int i = 0; i < 2; i++) {
			Row row = table.newRow();
			row.put("colA", i);
			row.put("colB", (long) i);
			row.put("colC", "row-" + i);
			table.getRows().add(row);
		}
		SqlParameterCollection parameters = CommonUtils
				.first(sqlFactoryRegistry.createSqlNodes(table, SqlType.INSERT_ROWS)).eval(table.getRows());

		assertEquals(8, parameters.getParameterSize());
		assertTrue(parameters.getSql().contains("SELECT ?,?,?,? FROM (VALUES(0))\nUNION ALL\n"
				+ "SELECT ?,?,?,? FROM (VALUES(0))"), parameters.getSql());
	}

	private static class LegacyInsertFactory extends InsertFactory {
		private boolean legacyOverrideCalled;

		@Override
		protected List<Column> addInsertIntoTable(Table table, AbstractSqlBuilder<?> builder) {
			legacyOverrideCalled = true;
			return super.addInsertIntoTable(table, builder);
		}
	}

	private Table createTable() {
		final Table table = new Table("tableA");
		table.getColumns().add(new Column("colA").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("colB").setDataType(DataType.BIGINT).setCheck("colB>0"));
		table.getColumns().add(new Column("colC").setDataType(DataType.VARCHAR).setLength(10).setDefaultValue("'0'"));
		table.getColumns().add(new Column("colD").setDataType(DataType.TIMESTAMP));
		table.setPrimaryKey("PK_TABLEA", table.getColumns().get("colA"), table.getColumns().get("colB"));
		table.getConstraints().addUniqueConstraint("UK_tableA1", table.getColumns().get("colB"));
		table.getIndexes().add("IDX_tableA1", table.getColumns().get("colC")).getColumns().get(0).setOrder(Order.Desc);
		return table;
	}

}
