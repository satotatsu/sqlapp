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

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.jdbc.sql.BindParameterHolder;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlParser;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

public class SelectByRootRowsFactoryTest extends AbstractStandardFactoryTest {
	SqlFactory<TableRelation> operationfactory;

	@BeforeEach
	public void before() {
		operationfactory = sqlFactoryRegistry.getSqlFactory(new TableRelation(new Table()),
				SqlType.SELECT_BY_ROOT_ROWS);
	}

	@Test
	public void testGetDdlTable() throws IOException {
		Catalog catalog = SchemaUtils.readXml(new File("./src/test/resources/catalog.xml"));
		Schema schema = catalog.getSchemas().get(0);
		Table customersTable = schema.getTables().get("CUSTOMERS");
		customersTable.getRows().add(row -> {
			row.put("CUSTOMER_ID", 1);
		});
		customersTable.getRows().add(row -> {
			row.put("CUSTOMER_ID", 2);
		});
		customersTable.getRows().add(row -> {
			row.put("CUSTOMER_ID", 3);
		});
		Table ordersTable = schema.getTables().get("ORDERS");
		Table orderDetailsTable = schema.getTables().get("ORDER_DETAILS");
		List<Table> tables = CommonUtils.list();
		tables.add(customersTable);
		tables.add(ordersTable);
		tables.add(orderDetailsTable);
		TableRelationTreeHolder tableRelationTreeHolder = new TableRelationTreeHolder(tables);
		List<SqlOperation> list = operationfactory
				.createSql(tableRelationTreeHolder.getRelationTree().get(orderDetailsTable.getName()));
		SqlOperation sqlOperation = CommonUtils.first(list);
		System.out.println(list);
		String expected = """
				SELECT
					a.ORDER_DETAIL_ID
					, a.ORDER_ID
					, a.LINE_NO
					, a.PRODUCT_ID
					, a.QUANTITY
					, a.UNIT_PRICE
					, a.AMOUNT
				FROM PUBLIC.ORDER_DETAILS AS a
				INNER JOIN PUBLIC.ORDERS AS a1
				ON( a.ORDER_ID = a1.ORDER_ID )
				INNER JOIN PUBLIC.CUSTOMERS AS a2
				ON( a1.CUSTOMER_ID = a2.CUSTOMER_ID )
				WHERE 1=0
				/*PARENT_ROWS_EQUALS(ROOT,a2.)*/
									""";
		assertEquals(expected.trim(), sqlOperation.getSqlText().trim());
		SqlNode sqlNode = SqlParser.getInstance().parse(dialect, sqlOperation);
		SqlParameterCollection sqlParameters = sqlNode.eval(tableRelationTreeHolder.getTableRelation(orderDetailsTable),
				customersTable.getRows());
		String expectedJdbc = """
				SELECT
					a.ORDER_DETAIL_ID
					, a.ORDER_ID
					, a.LINE_NO
					, a.PRODUCT_ID
					, a.QUANTITY
					, a.UNIT_PRICE
					, a.AMOUNT
				FROM PUBLIC.ORDER_DETAILS AS a
				INNER JOIN PUBLIC.ORDERS AS a1
				ON( a.ORDER_ID = a1.ORDER_ID )
				INNER JOIN PUBLIC.CUSTOMERS AS a2
				ON( a1.CUSTOMER_ID = a2.CUSTOMER_ID )
				WHERE 1=0
					 AND a2.CUSTOMER_ID IN ( ?, ?, ? )
									""";
		assertEquals(expectedJdbc.trim(), sqlParameters.getSql().trim());
		int i = 0;
		BindParameterHolder bindParameterHolder = sqlParameters.getBindParameters().get(0);
		assertEquals((long) i + 1, bindParameterHolder.getBindParameters().get(i++).getValue());
		assertEquals((long) i + 1, bindParameterHolder.getBindParameters().get(i++).getValue());
		assertEquals((long) i + 1, bindParameterHolder.getBindParameters().get(i++).getValue());
	}

}
