/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のスキーマ選択コマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class MySqlSetPathToSchemaSqlFactoryTest extends AbstractMySqlSqlFactoryTest {
	MySqlSetSearchPathToSchemaFactory operation;

	@BeforeEach
	public void before() {
		operation = new MySqlSetSearchPathToSchemaFactory();
		operation.setDialect(this.dialect);
	}

	@Test
	public void testGetDdlTableTable1() {
		Table table1 = getTable("tableA");
		table1.setSchemaName("sc1");
		List<SqlOperation> list = operation.createSql(table1);
		SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		String[] expected = new String[] { "USE sc1" };
		System.out.println(list);
		String[] result = commandText.getSqlText().split("\n");
		for (int i = 0; i < result.length; i++) {
			assertEquals(expected[i], result[i]);
		}
	}

	private Table getTable(String tableName) {
		Table table = new Table(tableName);
		table.getSpecifics().put("ENGINE", "innodb");
		return table;
	}
}
