/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class SqlServer2008CreateTableFactoryTest extends
AbstractSqlServer11SqlFactoryTest {
	SqlFactory<Table> operationfactory;

	@BeforeEach
	public void before() {
		operationfactory = sqlFactoryRegistry.getSqlFactory(
				new Table(), State.Added);
	}

	@Test
	public void testGetDdlTable() {
		final Table table0 = createTable();
		final Table table = createTable1();
		table.getConstraints().addForeignKeyConstraint("FK1", table.getColumns().get("colA"), table0.getColumns().get("colA")).setUpdateRule(CascadeRule.Restrict).setDeleteRule(CascadeRule.Cascade);
		final List<SqlOperation> list = operationfactory.createSql(table);
		final SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		final String expected = getResource("create_table1.sql");
		assertEquals(expected, commandText.getSqlText());
		System.out.println(list);
		final String expected1 = getResource("create_index_with_table1.sql");
		final SqlOperation commandText1 = list.get(1);
		assertEquals(expected1, commandText1.getSqlText());
		System.out.println(list);
	}

	protected Table createTable(){
		final Table table = new Table("tableB");
		table.getColumns().add(
				new Column("colA").setDataType(DataType.INT).setNotNull(true));
		table.getColumns()
				.add(new Column("colB").setDataType(DataType.BIGINT).setCheck(
						"colB>0"));
		table.getColumns().add(
				new Column("colC").setDataType(DataType.VARCHAR).setLength(10)
						.setDefaultValue("'0'").setNotNull(true));
		table.setPrimaryKey("PK_TABLEA", table.getColumns().get("colA"), table
				.getColumns().get("colB"));
		return table;
	}
	
	protected Table createTable1(){
		final Table table = new Table("tableA");
		table.setCompression(true);
		table.setCompressionType("PAGE");
		table.getColumns().add(c->{
			c.setName("colA");
			c.setDataType(DataType.INT).setNotNull(true).setIdentity(true);
		});
		table.getColumns()
				.add(new Column("colB").setDataType(DataType.BIGINT).setCheck(
						"colB>0"));
		table.getColumns().add(
				new Column("colC").setDataType(DataType.VARCHAR).setLength(10)
						.setDefaultValue("'0'").setNotNull(true));
		table.getColumns().add(
				new Column("colD").setLength(Integer.MAX_VALUE).setDataType(DataType.VARCHAR));
		table.setPrimaryKey("PK_TABLEA", table.getColumns().get("colA"), table
				.getColumns().get("colB"));
		table.getConstraints().addUniqueConstraint("UK_tableA1",
				table.getColumns().get("colB"));
		table.getIndexes().add("IDX_tableA1", table.getColumns().get("colC"))
				.getColumns().get(0).setOrder(Order.Desc);
		table.getIndexes().get("IDX_tableA1").toPartitioning(p->{
			p.setPartitionSchemeName("PF_SCHEME");
			p.getPartitioningColumns().add(c->{
				c.setName("colC");
			});
		});
		table.toPartitioning(p->{
			p.setPartitionSchemeName("PF_SCHEME");
			p.getPartitioningColumns().add(c->{
				c.setName("colA");
			});
		});
		return table;
	}

}
