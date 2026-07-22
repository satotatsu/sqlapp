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

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlParser;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

public class DeleteByParentRowsFactoryTest extends AbstractStandardFactoryTest {
	SqlFactory<TableRelation> operationfactory;

	@BeforeEach
	public void before() {
		operationfactory = sqlFactoryRegistry.getSqlFactory(new TableRelation(new Table()),
				SqlType.DELETE_BY_ROOT_ROWS);
		Options option = new Options();
		operationfactory.setOptions(option);
	}

	@Test
	public void testGetDdlTable() {
		Table table = getTable();
		Table table2 = getTable2();
		this.createForeignKey(table, table2);
		List<Table> tables = CommonUtils.list();
		tables.add(table);
		tables.add(table2);
		TableRelationTreeHolder tableRelationTreeHolder = new TableRelationTreeHolder(tables);
		List<SqlOperation> list = operationfactory.createSql(tableRelationTreeHolder.getTableRelation(table));
		SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		String expected = """
				DELETE FROM "tabA"
				WHERE 1=1
				AND EXISTS (
					SELECT 1
					FROM "tabB"
					WHERE 1=1
						AND "tabA"."colAB" = "tabB"."colBB"
						AND "tabA"."colAD" = "tabB"."colBD"
						/*PARENT_ROWS_EQUALS(ROOT)*/
				)
				""";
		assertEquals(expected.trim(), commandText.getSqlText().trim());
		//
		SqlNode sqlNode = SqlParser.getInstance().parse(dialect, commandText);
		SqlParameterCollection sqlParameterCollection = sqlNode.eval(tableRelationTreeHolder.getTableRelation(table),
				table2.getRows());
		String expectedJdbc = """
				DELETE FROM "tabA"
				WHERE 1=1
				AND EXISTS (
					SELECT 1
					FROM "tabB"
					WHERE 1=1
						AND "tabA"."colAB" = "tabB"."colBB"
						AND "tabA"."colAD" = "tabB"."colBD"
					 AND (
						 ( "tabB"."colBA" = ? AND "tabB"."colBE" = ? )
						OR ( "tabB"."colBA" = ? AND "tabB"."colBE" = ? )
						OR ( "tabB"."colBA" = ? AND "tabB"."colBE" = ? )
					)
				)
				""";
		assertEquals(expectedJdbc.trim(), sqlParameterCollection.getSql());
	}

	@Test
	public void testGetDdlTableSupportsRowValueComparison() {
		Dialect dialect = new Dialect(() -> null) {
			@Override
			public boolean supportsRowValueComparison() {
				return true;
			}
		};
		Table table = getTable();
		Table table2 = getTable2();
		this.createForeignKey(table, table2);
		List<Table> tables = CommonUtils.list();
		tables.add(table);
		tables.add(table2);
		TableRelationTreeHolder tableRelationTreeHolder = new TableRelationTreeHolder(tables);
		List<SqlOperation> list = operationfactory.createSql(tableRelationTreeHolder.getTableRelation(table));
		SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		String expected = """
				DELETE FROM "tabA"
				WHERE 1=1
				AND EXISTS (
					SELECT 1
					FROM "tabB"
					WHERE 1=1
						AND "tabA"."colAB" = "tabB"."colBB"
						AND "tabA"."colAD" = "tabB"."colBD"
						/*PARENT_ROWS_EQUALS(ROOT)*/
				)
				""";
		assertEquals(expected.trim(), commandText.getSqlText().trim());
		//
		SqlNode sqlNode = SqlParser.getInstance().parse(dialect, commandText);
		SqlParameterCollection sqlParameterCollection = sqlNode.eval(tableRelationTreeHolder.getTableRelation(table),
				table2.getRows());
		String expectedJdbc = """
				DELETE FROM "tabA"
				WHERE 1=1
				AND EXISTS (
					SELECT 1
					FROM "tabB"
					WHERE 1=1
						AND "tabA"."colAB" = "tabB"."colBB"
						AND "tabA"."colAD" = "tabB"."colBD"
					 AND (
						 ( "tabB"."colBA", "tabB"."colBE" ) = ( ?, ? )
						OR ( "tabB"."colBA", "tabB"."colBE" ) = ( ?, ? )
						OR ( "tabB"."colBA", "tabB"."colBE" ) = ( ?, ? )
					)
				)
				""";
		assertEquals(expectedJdbc.trim(), sqlParameterCollection.getSql());
	}

	@Test
	public void testGetDdlTableSupportsRowValueComparisonIn() {
		Dialect dialect = new Dialect(() -> null) {
			@Override
			public boolean supportsRowValueComparisonIn() {
				return true;
			}
		};
		Table table = getTable();
		Table table2 = getTable2();
		this.createForeignKey(table, table2);
		List<Table> tables = CommonUtils.list();
		tables.add(table);
		tables.add(table2);
		TableRelationTreeHolder tableRelationTreeHolder = new TableRelationTreeHolder(tables);
		List<SqlOperation> list = operationfactory.createSql(tableRelationTreeHolder.getTableRelation(table));
		SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		String expected = """
				DELETE FROM "tabA"
				WHERE 1=1
				AND EXISTS (
					SELECT 1
					FROM "tabB"
					WHERE 1=1
						AND "tabA"."colAB" = "tabB"."colBB"
						AND "tabA"."colAD" = "tabB"."colBD"
						/*PARENT_ROWS_EQUALS(ROOT)*/
				)
				""";
		assertEquals(expected.trim(), commandText.getSqlText().trim());
		//
		SqlNode sqlNode = SqlParser.getInstance().parse(dialect, commandText);
		SqlParameterCollection sqlParameterCollection = sqlNode.eval(tableRelationTreeHolder.getTableRelation(table),
				table2.getRows());
		String expectedJdbc = """
				DELETE FROM "tabA"
				WHERE 1=1
				AND EXISTS (
					SELECT 1
					FROM "tabB"
					WHERE 1=1
						AND "tabA"."colAB" = "tabB"."colBB"
						AND "tabA"."colAD" = "tabB"."colBD"
					 AND ( "tabB"."colBA", "tabB"."colBE" ) IN ( ( ?, ? ), ( ?, ? ), ( ?, ? ) )
				)
				""";
		assertEquals(expectedJdbc.trim(), sqlParameterCollection.getSql());
	}

	private String createForeignKey(Table table, Table table2) {
		String fkName = "FK_" + table.getName();
		table.getConstraints().addForeignKeyConstraint(fkName, fk -> {
			fk.setColumns(table.getColumns().get("colAB"), table.getColumns().get("colAD"));
			fk.setRelatedColumns(table2.getColumns().get("colBB"), table2.getColumns().get("colBD"));
		});
		return fkName;
	}

	private Table getTable() {
		Table table = new Table("tabA");
		table.getColumns().add(c -> {
			c.setName("colAA");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("colAB");
			c.setDataType(DataType.VARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colAC");
			c.setDataType(DataType.DATETIME);
		});
		table.getColumns().add(c -> {
			c.setName("colAD");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("colAE");
			c.setDataType(DataType.INT);
		});
		table.setPrimaryKey(table.getColumns().get("colAA"), table.getColumns().get("colAB"));
		for (int i = 0; i < 3; i++) {
			Row row = table.newRow();
			row.put("colAA", i);
			row.put("colAB", "colAB" + i);
			row.put("colAC", LocalDateTime.now());
			row.put("colAD", i * 10);
			row.put("colAE", i * 3);
			table.getRows().add(row);
		}
		return table;
	}

	private Table getTable2() {
		Table table = new Table("tabB");
		table.getColumns().add(c -> {
			c.setName("colBA");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("colBB");
			c.setDataType(DataType.VARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colBC");
			c.setDataType(DataType.DATETIME);
		});
		table.getColumns().add(c -> {
			c.setName("colBD");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("colBE");
			c.setDataType(DataType.INT);
		});
		table.setPrimaryKey(table.getColumns().get("colBA"), table.getColumns().get("colBE"));
		for (int i = 0; i < 3; i++) {
			Row row = table.newRow();
			row.put("colBA", i);
			row.put("colBB", "colBB" + i);
			row.put("colBC", LocalDateTime.now());
			row.put("colBD", i * 10);
			row.put("colBE", i * 3);
			table.getRows().add(row);
		}
		return table;
	}

}
