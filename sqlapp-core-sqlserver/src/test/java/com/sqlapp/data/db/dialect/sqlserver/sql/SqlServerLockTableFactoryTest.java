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

package com.sqlapp.data.db.dialect.sqlserver.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableLockMode;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

class SqlServerLockTableFactoryTest extends AbstractSqlServerSqlFactoryTest {
	SqlFactory<Table> operationfactory;

	@BeforeEach
	public void before() {
		operationfactory = sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.LOCK);
	}

	@Override
	protected int getMajorVersion() {
		return 14;
	}

	@Override
	protected int getMinorVersion() {
		return 0;
	}

	@Test
	void test() {
		final Table table = new Table("tableA");
		table.getColumns().add(new Column("colA").setDataType(DataType.INT).setNotNull(true));
		List<SqlOperation> list = operationfactory.createSql(table);
		SqlOperation commandText = CommonUtils.first(list);
		String expect = "SELECT * FROM tableA WITH ( TABLOCK, UPDLOCK )";
		assertEquals(expect, commandText.getSqlText());
		//
		operationfactory.getOptions().getTableOptions().setLockMode(t -> TableLockMode.SHARE);
		list = operationfactory.createSql(table);
		commandText = CommonUtils.first(list);
		expect = "SELECT * FROM tableA WITH ( TABLOCK, HOLDLOCK )";
		assertEquals(expect, commandText.getSqlText());
	}

}
