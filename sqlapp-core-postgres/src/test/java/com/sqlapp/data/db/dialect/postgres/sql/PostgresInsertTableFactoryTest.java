/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.ParseException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のAlterコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class PostgresInsertTableFactoryTest extends AbstractPostgresSqlFactoryTest {
	SqlFactory<Table> sqlFactory;

	@BeforeEach
	public void before() {
		sqlFactory = this.sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.INSERT);
		sqlFactory.getTableOptions().setDmlBatchSize(10);
	}

	@Test
	public void testInsertRow() throws ParseException {
		Table table1 = getTable1("tableA");
		List<SqlOperation> operations = sqlFactory.createSql(table1);
		SqlOperation operation = CommonUtils.first(operations);
		String expected = """
				INSERT INTO "tableA"
				(
					  cola
					, colb
					, colc
					, cold
					, cole
					, colf
				)
				VALUES
				(
					  /*cola*/0
					, /*colb*/''
					, /*colc*/CURRENT_TIMESTAMP
					, /*cold*/1
					, CAST( /*cole*/'' AS JSON )
					, CAST( /*colf*/'' AS JSONB )
				)""";
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testByDefaultIdentityUsesDefaultWhenValuesAreMissing() {
		Table table = createIdentityTable(IdentityGenerationType.ByDefault);
		Row row = addRow(table);
		row.put("txt", "generated");

		String sql = CommonUtils.first(sqlFactory.createSql(table)).getSqlText();

		assertTrue(sql.contains("id"));
		assertTrue(sql.contains("default"));
	}

	@Test
	public void testByDefaultIdentityUsesExplicitValues() {
		Table table = createIdentityTable(IdentityGenerationType.ByDefault);
		Row row = addRow(table);
		row.put("id", 100L);
		row.put("txt", "explicit");

		String sql = CommonUtils.first(sqlFactory.createSql(table)).getSqlText();

		assertTrue(sql.contains("/*id*/0"));
	}

	@Test
	public void testByDefaultIdentityRejectsMixedValues() {
		Table table = createIdentityTable(IdentityGenerationType.ByDefault);
		addRow(table).put("txt", "generated");
		Row explicit = addRow(table);
		explicit.put("id", 100L);
		explicit.put("txt", "explicit");

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> sqlFactory.createSql(table));

		assertTrue(exception.getMessage().contains("cannot mix"));
	}

	@Test
	public void testAlwaysIdentityRejectsExplicitValues() {
		Table table = createIdentityTable(IdentityGenerationType.Always);
		Row row = addRow(table);
		row.put("id", 100L);
		row.put("txt", "explicit");

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> sqlFactory.createSql(table));

		assertTrue(exception.getMessage().contains("GENERATED ALWAYS"));
	}

	private Table createIdentityTable(IdentityGenerationType generationType) {
		Table table = getTable("identity_table");
		table.getColumns().add(new Column("id").setDataType(DataType.BIGINT)
				.setIdentity(true).setIdentityGenerationType(generationType));
		table.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR).setLength(50));
		table.setPrimaryKey(table.getColumns().get("id"));
		return table;
	}

	private Row addRow(Table table) {
		Row row = table.newRow();
		table.getRows().add(row);
		return row;
	}

	private Table getTable1(String tableName) throws ParseException {
		Table table = getTable(tableName);
		table.getColumns().add(c -> {
			c.setName("cola");
			c.setDataType(DataType.INT);
			c.setDisplayName("カラムA");
		});
		Column column = new Column("colb").setDataType(DataType.VARCHAR).setLength(50);
		table.getColumns().add(column);
		column = new Column("colc").setDataType(DataType.DATETIME);
		table.getColumns().add(column);
		column = new Column("cold").setDataType(DataType.INT).setNotNull(true).setDefaultValue("1");
		table.getColumns().add(column);
		table.getColumns().add(c -> {
			c.setName("cole");
			c.setDataType(DataType.JSON);
			c.setDisplayName("カラムE");
		});
		table.getColumns().add(c -> {
			c.setName("colf");
			c.setDataType(DataType.JSONB);
			c.setDisplayName("カラムF");
		});
		table.setPrimaryKey(table.getColumns().get("cola"));
		return table;
	}

	private Table getTable(String tableName) {
		Table table = new Table(tableName);
		table.setDialect(dialect);
		return table;
	}
}
