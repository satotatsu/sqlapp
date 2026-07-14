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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;

public class MergeFactoryTest extends AbstractStandardFactoryTest {
	SqlFactory<Table> sqlFactory;

	@BeforeEach
	public void before() {
		sqlFactory = sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.MERGE);
		TableOptions tableOptions = new TableOptions();
		tableOptions.setWithCoalesceAtUpdate(true);
		sqlFactory.setTableOptions(tableOptions);
	}

	@Test
	public void testGetDdlTable() {
		Table table = createTable();
		List<SqlOperation> list = sqlFactory.createSql(table);
		System.out.println(list);
		int i = 0;
		SqlOperation operation = list.get(i++);
		String expected = """
				MERGE INTO "tableA" AS _target_
				USING (
					SELECT
					  /*colA*/0
					, /*colB*/0
					, /*colC*/'0'
					, /*colD*/''
					, 0
					FROM (VALUES(0))
				)
				AS _source_ ( "colA", "colB", "colC", "colD", "lock_version" )
				ON (
					_target_."colC" = _source_."colC"
				)
				WHEN MATCHED
					THEN UPDATE
						SET _target_."colD" = _source_."colD"
						, _target_."lock_version" = _source_."lock_version"
				WHEN NOT MATCHED
					THEN INSERT
					(
						"colA"
						, "colB"
						, "colC"
						, "colD"
						, "lock_version"
					)
					VALUES
					(
						_source_."colA"
						, _source_."colB"
						, COALESCE( _source_."colC", '0' )
						, COALESCE( _source_."colD", '' )
						, _source_."lock_version"
					)
						""";
		assertEquals(expected.trim(), operation.getSqlText().trim());
	}

	@Test
	public void testGetDdlTableSysDummy() {
		Table table = createTable();
		Dialect dialect = new Dialect(() -> null) {
			@Override
			public boolean supportsValues() {
				return true;
			}
		};
		sqlFactory.setDialect(dialect);
		List<SqlOperation> list = sqlFactory.createSql(table);
		System.out.println(list);
		int i = 0;
		SqlOperation operation = list.get(i++);
		String expected = """
				MERGE INTO "tableA" AS _target_
				USING (
					VALUES (
						  /*colA*/0
						, /*colB*/0
						, /*colC*/'0'
						, /*colD*/''
						, 0
					)
				)
				AS _source_ ( "colA", "colB", "colC", "colD", "lock_version" )
				ON (
					_target_."colC" = _source_."colC"
				)
				WHEN MATCHED
					THEN UPDATE
						SET _target_."colD" = _source_."colD"
						, _target_."lock_version" = _source_."lock_version"
				WHEN NOT MATCHED
					THEN INSERT
					(
						"colA"
						, "colB"
						, "colC"
						, "colD"
						, "lock_version"
					)
					VALUES
					(
						_source_."colA"
						, _source_."colB"
						, COALESCE( _source_."colC", '0' )
						, COALESCE( _source_."colD", '' )
						, _source_."lock_version"
					)
					""";
		assertEquals(expected.trim(), operation.getSqlText().trim());
	}

	private Table createTable() {
		Table table = new Table("tableA");
		table.getColumns().add(new Column("colA").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("colB").setDataType(DataType.BIGINT));
		table.getColumns().add(new Column("colC").setDataType(DataType.VARCHAR).setLength(10).setDefaultValue("'0'"));
		table.getColumns().add(new Column("colD").setDataType(DataType.VARCHAR).setLength(12).setDefaultValue("''"));
		table.getColumns().add(new Column("lock_version").setDataType(DataType.BIGINT));
		table.setPrimaryKey("PK_TABLEA", table.getColumns().get("colA"), table.getColumns().get("colB"));
		table.getConstraints().addUniqueConstraint("UK_tableA1", table.getColumns().get("colC"));
		table.getIndexes().add("IDX_tableA1", table.getColumns().get("colC")).getColumns().get(0).setOrder(Order.Desc);
		return table;

	}

}
