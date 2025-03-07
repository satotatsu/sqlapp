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
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.StringUtils;

public class DeleteByPkTableFactoryTest2 extends AbstractStandardFactoryTest {
	SqlFactory<Table> operationfactory;

	@BeforeEach
	public void before() {
		final Dialect dialect = DialectResolver.getInstance().getDialect("Standard",
				0, 0);
		final SqlFactoryRegistry sqlFactoryRegistry = dialect
				.createSqlFactoryRegistry();
		operationfactory = sqlFactoryRegistry.getSqlFactory(
				new Table(), SqlType.DELETE_BY_PK);
		final Options option=new Options();
		option.getTableOptions().setParameterExpression((c,def)->"${"+StringUtils.snakeToCamel(c.getName())+"}");
		operationfactory.setOptions(option);
	}

	@Test
	public void testGetDdlTable() {
		final Table table = new Table("tableA");
		table.getColumns().add(
				new Column("col_a").setDataType(DataType.INT).setNotNull(true));
		table.getColumns()
				.add(new Column("col_b").setDataType(DataType.BIGINT).setCheck(
						"colB>0"));
		table.getColumns().add(
				new Column("col_c").setDataType(DataType.VARCHAR).setLength(10)
						.setDefaultValue("'0'"));
		table.getColumns()
		.add(new Column("lock_version").setDataType(DataType.BIGINT));
		table.setPrimaryKey("PK_TABLEA", table.getColumns().get("col_a"), table
				.getColumns().get("col_b"));
		table.getConstraints().addUniqueConstraint("UK_tableA1",
				table.getColumns().get("col_b"));
		table.getIndexes().add("IDX_tableA1", table.getColumns().get("col_c"))
				.getColumns().get(0).setOrder(Order.Desc);
		final List<SqlOperation> list = operationfactory.createSql(table);
		final SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		final String expected = FileUtils.getResource(this, "delete_by_pk_table2.sql");
		assertEquals(expected, commandText.getSqlText());
	}

}
