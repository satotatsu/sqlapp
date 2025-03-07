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
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.SeparatedStringBuilder;

public class AlterTableFactoryTest extends AbstractStandardFactoryTest {
	SqlFactory<Table> operationfactory;

	@BeforeEach
	public void before() {
		operationfactory = sqlFactoryRegistry.getSqlFactory(
				new Table(), State.Modified);
	}

	@Test
	public void testGetDdlTable() {
		Schema schema1=new Schema("schemaA");
		Schema schema2=new Schema("schemaA");
		Table table1 = getTable1();
		Table table2 = getTable2();
		schema1.getTables().add(table1);
		schema2.getTables().add(table2);
		DbObjectDifference dbDiffrence=table1.diff(table2);
		System.out.println(dbDiffrence);
		List<SqlOperation> list = operationfactory.createDiffSql(dbDiffrence);
		System.out.println(list);
		SeparatedStringBuilder builder=new SeparatedStringBuilder(";\n");
		builder.add(list);
		String expected = FileUtils.getResource(this, "alter_table1.sql");
		assertEquals(expected, builder.toString());
	}

	private Table getTable(){
		Table table = new Table("tableA");
		table.getColumns().add(
				new Column("colA").setDataType(DataType.INT).setNotNull(true));
		table.getColumns()
				.add(new Column("colB").setDataType(DataType.BIGINT).setCheck(
						"colB>0"));
		table.getColumns().add(
				new Column("colC").setDataType(DataType.VARCHAR).setLength(10)
						.setDefaultValue("'0'").setNotNull(true));
		return table;
	}
	
	private Table getTable1(){
		Table table = getTable();
		table.setPrimaryKey("PK_TABLEA", table.getColumns().get("colA"), table
				.getColumns().get("colB"));
		return table;
	}

	private Table getTable2(){
		Table table = getTable();
		table.getColumns().add(
				new Column("colD").setDataType(DataType.VARCHAR).setLength(30).setNotNull(true));
		table.setPrimaryKey("PK_TABLEA2", table.getColumns().get("colA"), table
				.getColumns().get("colB"));
		return table;
	}

}
