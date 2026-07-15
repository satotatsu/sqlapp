/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.ParseException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DateUtils;

/**
 * MySQL用のAlterコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class Hsql2MergeRowsFactoryTest extends AbstractHsqlSqlFactoryTest {
	SqlFactory<Table> sqlFactory;

	@BeforeEach
	public void before() {
		sqlFactoryRegistry.registerSqlFactory(Table.class, SqlType.MERGE_ROWS, Hsql2MergeRowsFactory.class);
		sqlFactory = this.sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.MERGE_ROWS);
	}

	@Test
	public void testMergeTablePk() throws ParseException {
		final Table table1 = getTable1("tableA");
		sqlFactory.getTableOptions().setWithCoalesceAtUpdate(true);
		final List<SqlOperation> operations = sqlFactory.createSql(table1);
		final SqlOperation operation = CommonUtils.first(operations);
		final String expected = """
				SELECT
					"cola"
					, "colb"
					, "colc"
					, "created_at"
					, "updated_at"
					, "lock_version"
				FROM FINAL TABLE
				(
					MERGE INTO "tableA" AS _target_
					USING ( /*VALUES*/VALUES ( 0, '', '', LOCALTIMESTAMP, LOCALTIMESTAMP, 0 )/*END*/ ) AS _source_ ( "cola", "colb", "colc", "created_at", "updated_at", "lock_version" )
					ON (
						_target_."cola" = _source_."cola"
					)
					WHEN MATCHED
						THEN UPDATE
							SET _target_."colb" = _source_."colb"
							, _target_."colc" = _source_."colc"
							, _target_."updated_at" = _source_."updated_at"
							, _target_."lock_version" = _source_."lock_version"
					WHEN NOT MATCHED
						THEN INSERT
						(
							"cola"
							, "colb"
							, "colc"
							, "created_at"
							, "updated_at"
							, "lock_version"
						)
						VALUES
						(
							_source_."cola"
							, _source_."colb"
							, _source_."colc"
							, COALESCE( _source_."created_at", CURRENT_TIMESTAMP )
							, _source_."updated_at"
							, _source_."lock_version"
						)
				)
								""";
		assertEquals(expected.trim(), operation.getSqlText().trim());
	}

	@Test
	public void testMergeTableUk() throws ParseException {
		final Table table = getTable1("tableA");
		table.getConstraints().addUniqueConstraint("UK_" + table.getName(), table.getColumns().get("colc"));
		final List<SqlOperation> operations = sqlFactory.createSql(table);
		final SqlOperation operation = CommonUtils.first(operations);
		final String expected = """
				SELECT
					"cola"
					, "colb"
					, "colc"
					, "created_at"
					, "updated_at"
					, "lock_version"
				FROM FINAL TABLE
				(
					MERGE INTO "tableA" AS _target_
					USING ( /*VALUES*/VALUES ( 0, '', '', LOCALTIMESTAMP, LOCALTIMESTAMP, 0 )/*END*/ ) AS _source_ ( "cola", "colb", "colc", "created_at", "updated_at", "lock_version" )
					ON (
						_target_."colc" = _source_."colc"
					)
					WHEN MATCHED
						THEN UPDATE
							SET _target_."colb" = _source_."colb"
							, _target_."updated_at" = _source_."updated_at"
							, _target_."lock_version" = _source_."lock_version"
					WHEN NOT MATCHED
						THEN INSERT
						(
							"cola"
							, "colb"
							, "colc"
							, "created_at"
							, "updated_at"
							, "lock_version"
						)
						VALUES
						(
							_source_."cola"
							, _source_."colb"
							, _source_."colc"
							, COALESCE( _source_."created_at", CURRENT_TIMESTAMP )
							, _source_."updated_at"
							, _source_."lock_version"
						)
				)
						""";
		assertEquals(expected.trim(), operation.getSqlText().trim());
	}

	@Test
	public void testMergeTableIdentity() throws ParseException {
		final Table table = getTable1("tableA");
		table.getColumns().get("cola").setIdentity(true);
		table.getColumns().get(0).setIdentity(true);
		sqlFactory.getTableOptions().setWithCoalesceAtUpdate(false);
		sqlFactory.getTableOptions().setMergeTableWithDelete(true);
		final List<SqlOperation> operations = sqlFactory.createSql(table);
		final SqlOperation operation = CommonUtils.first(operations);
		final String expected = """
				SELECT
					"cola"
					, "colb"
					, "colc"
					, "created_at"
					, "updated_at"
					, "lock_version"
				FROM FINAL TABLE
				(
					MERGE INTO "tableA" AS _target_
					USING ( /*VALUES*/VALUES ( 0, '', '', LOCALTIMESTAMP, LOCALTIMESTAMP, 0 )/*END*/ ) AS _source_ ( "cola", "colb", "colc", "created_at", "updated_at", "lock_version" )
					ON (
						_target_."cola" = _source_."cola"
					)
					WHEN MATCHED
						THEN UPDATE
							SET _target_."colb" = _source_."colb"
							, _target_."colc" = _source_."colc"
							, _target_."updated_at" = _source_."updated_at"
							, _target_."lock_version" = _source_."lock_version"
					WHEN NOT MATCHED
						THEN INSERT
						(
							"colb"
							, "colc"
							, "created_at"
							, "updated_at"
							, "lock_version"
						)
						VALUES
						(
							_source_."colb"
							, _source_."colc"
							, COALESCE( _source_."created_at", CURRENT_TIMESTAMP )
							, _source_."updated_at"
							, _source_."lock_version"
						)
				)
					""";
		assertEquals(expected.trim(), operation.getSqlText().trim());
	}

	@Test
	public void testMergeTableRowNumber() throws ParseException {
		final Table table = getTable1("tableA");
		table.getRows().get(0).setRowId(0L);
		table.getColumns().get(0).setIdentity(true);
		sqlFactory.getTableOptions().setWithCoalesceAtUpdate(false);
		sqlFactory.getTableOptions().setMergeTableWithDelete(true);
		sqlFactory.getTableOptions().setInsertTableColumnValue(c -> {
			if ("created_at".equals(c.getName())) {
				return "/*insert_" + c.getName() + "*/";
			}
			return c.getName();
		});
		sqlFactory.getTableOptions().setUpdateTableColumnValue(c -> {
			if ("updated_at".equals(c.getName())) {
				return "/*update_" + c.getName() + "*/";
			}
			return c.getName();
		});
		final List<SqlOperation> operations = sqlFactory.createSql(table);
		final SqlOperation operation = CommonUtils.first(operations);
		final String expected = """
				SELECT
					"cola"
					, "colb"
					, "colc"
					, "created_at"
					, "updated_at"
					, "lock_version"
				FROM FINAL TABLE
				(
					MERGE INTO "tableA" AS _target_
					USING ( /*VALUES*/VALUES ( 0, '', '', LOCALTIMESTAMP, LOCALTIMESTAMP, 0 )/*END*/ ) AS _source_ ( "cola", "colb", "colc", "created_at", "updated_at", "lock_version" )
					ON (
						_target_."cola" = _source_."cola"
					)
					WHEN MATCHED
						THEN UPDATE
							SET _target_."colb" = _source_."colb"
							, _target_."colc" = _source_."colc"
							, _target_."updated_at" =/*update_updated_at*/
							, _target_."lock_version" = _source_."lock_version"
					WHEN NOT MATCHED
						THEN INSERT
						(
							"colb"
							, "colc"
							, "created_at"
							, "updated_at"
							, "lock_version"
						)
						VALUES
						(
							_source_."colb"
							, _source_."colc"
							, COALESCE(/*insert_created_at*/, CURRENT_TIMESTAMP )
							, _source_."updated_at"
							, _source_."lock_version"
						)
				)
				""";
		assertEquals(expected.trim(), operation.getSqlText().trim());
	}

	private Table getTable1(final String tableName) throws ParseException {
		final Table table = getTable(tableName);
		Column column = new Column("cola").setDataType(DataType.INT);
		table.getColumns().add(column);
		column = new Column("colb").setDataType(DataType.VARCHAR).setLength(50);
		table.getColumns().add(column);
		column = new Column("colc").setDataType(DataType.VARCHAR).setLength(50);
		table.getColumns().add(column);
		column = new Column("created_at").setDataType(DataType.TIMESTAMP);
		column.setDefaultValue("CURRENT_TIMESTAMP");
		table.getColumns().add(column);
		column = new Column("updated_at").setDataType(DataType.TIMESTAMP);
		table.getColumns().add(column);
		column = new Column("lock_version").setDataType(DataType.INT);
		table.getColumns().add(column);
		table.setPrimaryKey(table.getColumns().get("cola"));
		//
		final Row row = table.newRow();
		row.put("cola", 1);
		row.put("colb", "bvalue");
		row.put("colc", "cvalue");
		row.put("created_at", DateUtils.parse("2016-01-12 12:32:30", "yyyy-MM-dd HH:mm:ss"));
		row.put("updated_at", DateUtils.parse("2016-12-31 12:32:30", "yyyy-MM-dd HH:mm:ss"));
		table.getRows().add(row);
		return table;
	}

	private Table getTable(final String tableName) {
		final Table table = new Table(tableName);
		return table;
	}
}
