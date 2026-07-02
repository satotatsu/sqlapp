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
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class CreateTemporaryTableFactoryTest extends AbstractStandardFactoryTest {
	SqlFactory<Table> operationfactory;

	@BeforeEach
	public void before() {
		sqlFactoryRegistry.registerSqlFactory(Table.class, SqlType.CREATE_TEMPORARY, CreateTemporaryTableFactory.class);
		operationfactory = sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.CREATE_TEMPORARY);
	}

	@Test
	public void testGetDdlTable() {
		Table table = createTable();
		List<SqlOperation> list = operationfactory.createSql(table);
		SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		String expected = """
				DECLARE LOCAL TEMPORARY TABLE "tableB"
				(
					  "colA" INT NOT NULL
					, "colB" BIGINT NOT NULL CHECK (colB>0)
					, "colC" VARCHAR(10) DEFAULT '0' NOT NULL
					, CONSTRAINT PK_TABLEA PRIMARY KEY ( "colA", "colB" )
				) ON COMMIT PRESERVE ROWS""";
		assertEquals(expected, commandText.getSqlText());
		operationfactory.getTableOptions().setTempTableOnCommitPreserveRows(false);
		list = operationfactory.createSql(table);
		commandText = CommonUtils.first(list);
		expected = """
				DECLARE LOCAL TEMPORARY TABLE "tableB"
				(
					  "colA" INT NOT NULL
					, "colB" BIGINT NOT NULL CHECK (colB>0)
					, "colC" VARCHAR(10) DEFAULT '0' NOT NULL
					, CONSTRAINT PK_TABLEA PRIMARY KEY ( "colA", "colB" )
				)""";
		assertEquals(expected, commandText.getSqlText());
	}

	protected Table createTable() {
		Table table = new Table("tableB");
		table.getColumns().add(new Column("colA").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("colB").setDataType(DataType.BIGINT).setCheck("colB>0"));
		table.getColumns().add(
				new Column("colC").setDataType(DataType.VARCHAR).setLength(10).setDefaultValue("'0'").setNotNull(true));
		table.setPrimaryKey("PK_TABLEA", table.getColumns().get("colA"), table.getColumns().get("colB"));
		return table;
	}

}
