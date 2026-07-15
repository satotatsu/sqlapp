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
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StringUtils;

public class UpdateFactoryTest2 extends AbstractStandardFactoryTest {
	SqlFactory<Table> operationfactory;

	@BeforeEach
	public void before() {
		operationfactory = sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.UPDATE);
		TableOptions tableOptions = new TableOptions();
		tableOptions.setParameterExpression((c, def) -> "${" + StringUtils.snakeToCamel(c.getName()) + "}");
		tableOptions.setWithCoalesceAtUpdate(true);
		operationfactory.setTableOptions(tableOptions);

	}

	@Test
	public void testGetDdlTable() {
		final Table table = new Table("tableA");
		table.getColumns().add(new Column("col_a").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("col_b").setDataType(DataType.BIGINT));
		table.getColumns().add(new Column("col_c").setDataType(DataType.VARCHAR).setLength(10).setDefaultValue("'0'"));
		table.getColumns().add(new Column("lock_version").setDataType(DataType.BIGINT));
		table.setPrimaryKey("PK_TABLEA", table.getColumns().get("col_a"), table.getColumns().get("col_b"));
		table.getConstraints().addUniqueConstraint("UK_tableA1", table.getColumns().get("col_b"));
		table.getIndexes().add("IDX_tableA1", table.getColumns().get("col_c")).getColumns().get(0).setOrder(Order.Desc);
		final List<SqlOperation> list = operationfactory.createSql(table);
		final SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		final String expected = """
				UPDATE "tableA"
				SET
				"col_c" = ${colC}
				, "lock_version" = COALESCE( "lock_version", 0 ) + 1
				WHERE 1=1
					AND "col_a" = ${colA}
					AND "col_b" = ${colB}
					AND "lock_version" = COALESCE( ${lockVersion}, "lock_version", 0 )
						""";
		assertEquals(expected.trim(), commandText.getSqlText().trim());
	}

}
