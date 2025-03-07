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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のAlterコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class OracleCreateTableFactoryTest extends AbstractOracleSqlFactoryTest {
	SqlFactory<Table> operation;

	@BeforeEach
	public void before() {
		operation = this.sqlFactoryRegistry.getSqlFactory(
				new Table(), State.Added);
	}

	@Test
	public void testCreateTable() {
		Table obj = getTable1("tableA");
		List<SqlOperation> list = operation.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_table1.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testCreateTable2() {
		Table obj = getTable1("tableA");
		obj.getColumns().get("colb").setCharacterSemantics(CharacterSemantics.Char);
		List<SqlOperation> list = operation.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_table2.sql");
		assertEquals(expected, operation.getSqlText());
	}

	private Table getTable(String tableName) {
		Table table = new Table(tableName);
		return table;
	}

	private Table getTable1(String tableName) {
		Table table = getTable(tableName);
		Column column = new Column("cola").setDataType(DataType.INT);
		table.getColumns().add(column);
		column = new Column("colb").setDataType(DataType.VARCHAR).setLength(50)
				.setCharacterSemantics(CharacterSemantics.Byte);
		table.getColumns().add(column);
		column = new Column("colc").setDataType(DataType.DATETIME);
		table.getColumns().add(column);
		//
		table.getSpecifics().put("PCT_FREE", 10);
		table.getSpecifics().put("INI_TRANS", 2);
		table.getSpecifics().put("MAX_TRANS", 255);
		table.getSpecifics().put("INITIAL_EXTENT", "1024M");
		table.getSpecifics().put("MIN_EXTENTS", 1);
		table.getSpecifics().put("MAX_EXTENTS", 2147483645);
		table.getSpecifics().put("FREELISTS", 1);
		table.getSpecifics().put("FREELIST_GROUPS", 1);
		table.getSpecifics().put("BUFFER_POOL", "DEFAULT");
		return table;
	}

}
