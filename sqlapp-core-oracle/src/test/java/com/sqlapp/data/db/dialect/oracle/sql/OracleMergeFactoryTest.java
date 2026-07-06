/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.ParseException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.resolver.OracleDialectResolver;
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
public class OracleMergeFactoryTest extends AbstractOracleSqlFactoryTest {
	SqlFactory<Table> sqlFactory;

	@BeforeEach
	public void before() {
		sqlFactory = this.sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.MERGE);
	}

	@Test
	public void testMergeTable1() throws ParseException {
		final Table table1 = getTable1("tableA");
		sqlFactory.getTableOptions().setWithCoalesceAtUpdate(true);
		final List<SqlOperation> operations = sqlFactory.createSql(table1);
		final SqlOperation operation = CommonUtils.first(operations);
		final String expected = """
				MERGE INTO "tableA" AS _target_
				USING (
					SELECT
					  /*cola*/0 AS "cola"
					, /*colb*/'' AS "colb"
					, CURRENT_TIMESTAMP AS "created_at"
					, COALESCE(/*updated_at*/CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) AS "updated_at"
					, 0 AS "lock_version"
					FROM DUAL
				) _source_
				ON (
					_target_."cola" = _source_."cola"
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
						, "created_at"
						, "updated_at"
						, "lock_version"
					)
					VALUES
					(
						_source_."cola"
						, _source_."colb"
						, _source_."created_at"
						, _source_."updated_at"
						, _source_."lock_version"
					)
				""";
		assertEquals(expected.trim(), operation.getSqlText().trim());
	}

	@Test
	public void testMergeTable2() throws ParseException {
		final Table table1 = getTable1("tableA");
		sqlFactory.getTableOptions().setWithCoalesceAtUpdate(false);
		final List<SqlOperation> operations = sqlFactory.createSql(table1);
		final SqlOperation operation = CommonUtils.first(operations);
		final String expected = """
				MERGE INTO "tableA" AS _target_
				USING (
					SELECT
					  /*cola*/0 AS "cola"
					, /*colb*/'' AS "colb"
					, CURRENT_TIMESTAMP AS "created_at"
					, CURRENT_TIMESTAMP AS "updated_at"
					, 0 AS "lock_version"
					FROM DUAL
				) _source_
				ON (
					_target_."cola" = _source_."cola"
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
						, "created_at"
						, "updated_at"
						, "lock_version"
					)
					VALUES
					(
						_source_."cola"
						, _source_."colb"
						, _source_."created_at"
						, _source_."updated_at"
						, _source_."lock_version"
					)
					""";
		assertEquals(expected.trim(), operation.getSqlText().trim());
	}

	@Test
	public void testMergeTable3() throws ParseException {
		final Table table1 = getTable1("tableA");
		table1.getColumns().get(0).setIdentity(true);
		sqlFactory.getTableOptions().setWithCoalesceAtUpdate(false);
		final List<SqlOperation> operations = sqlFactory.createSql(table1);
		final SqlOperation operation = CommonUtils.first(operations);
		final String expected = """
				MERGE INTO "tableA" AS _target_
				USING (
					SELECT
					  /*cola*/0 AS "cola"
					, /*colb*/'' AS "colb"
					, CURRENT_TIMESTAMP AS "created_at"
					, CURRENT_TIMESTAMP AS "updated_at"
					, 0 AS "lock_version"
					FROM DUAL
				) _source_
				ON (
					_target_."cola" = _source_."cola"
				)
				WHEN MATCHED
					THEN UPDATE
						SET _target_."colb" = _source_."colb"
						, _target_."updated_at" = _source_."updated_at"
						, _target_."lock_version" = _source_."lock_version"
				WHEN NOT MATCHED
					THEN INSERT
					(
						"colb"
						, "created_at"
						, "updated_at"
						, "lock_version"
					)
					VALUES
					(
						_source_."colb"
						, _source_."created_at"
						, _source_."updated_at"
						, _source_."lock_version"
					)
					""";
		assertEquals(expected.trim(), operation.getSqlText().trim());
	}

	@Test
	public void testMergOracle23() throws ParseException {
		final Table table1 = getTable1("tableA");
		table1.getColumns().get(0).setIdentity(true);
		OracleDialectResolver oracleDialectResolver = new OracleDialectResolver();
		Dialect dialect = oracleDialectResolver.getDialect(23, 0);
		sqlFactory.setDialect(dialect);
		sqlFactory.getTableOptions().setWithCoalesceAtUpdate(false);
		final List<SqlOperation> operations = sqlFactory.createSql(table1);
		final SqlOperation operation = CommonUtils.first(operations);
		final String expected = """
				MERGE INTO "tableA" AS _target_
				USING (
					VALUES (
						  /*cola*/0
						, /*colb*/''
						, CURRENT_TIMESTAMP
						, CURRENT_TIMESTAMP
						, 0
					)
				) _source_ ( "cola", "colb", "created_at", "updated_at", "lock_version" )
				ON (
					_target_."cola" = _source_."cola"
				)
				WHEN MATCHED
					THEN UPDATE
						SET _target_."colb" = _source_."colb"
						, _target_."updated_at" = _source_."updated_at"
						, _target_."lock_version" = _source_."lock_version"
				WHEN NOT MATCHED
					THEN INSERT
					(
						"colb"
						, "created_at"
						, "updated_at"
						, "lock_version"
					)
					VALUES
					(
						_source_."colb"
						, _source_."created_at"
						, _source_."updated_at"
						, _source_."lock_version"
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
		column = new Column("created_at").setDataType(DataType.TIMESTAMP);
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
