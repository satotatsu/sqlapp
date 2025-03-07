/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class HsqlCreate2_3_4TableFactoryTest extends AbstractHsql2_3_4SqlFactoryTest{

	SqlFactory<Table> sqlFactory;

	@BeforeEach
	public void before() {
		sqlFactory = sqlFactoryRegistry.getSqlFactory(
				new Table(), SqlType.CREATE);
	}
	

	@Test
	public void testCreateTest1() {
		Table table=new Table("tablea");
		Column column=table.newColumn();
		column.setName("id").setDataType(DataType.INT);
		column.setIdentity(true);
		column.setSequenceName("seq1");
		table.getColumns().add(column);
		//
		column=table.newColumn();
		column.setName("ts").setDataTypeName("timestamp");
		column.setOnUpdate("CURRENT_TIMESTAMP");
		table.getColumns().add(column);
		List<SqlOperation> operations=sqlFactory.createSql(table);
		SqlOperation commandText = CommonUtils.first(operations);
		System.out.println(operations);
		String expected = getResource("create_table2_3_4_1.sql");
		assertEquals(expected, commandText.getSqlText());
	}

}
