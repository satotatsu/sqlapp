/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.generator;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

public class GenerateDataInsertCommandUKTest extends AbstractGeneratorCommandTest {

	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		testRun(1);
		testRun(10);
		// testRun(100);
	}

	public void testRun(int batchSize) throws ParseException, IOException, SQLException {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateDataInsertCommand command = new GenerateDataInsertCommand();
			command.setDataSource(ds);
			command.setIncludeTables("PRODUCTS", "CUSTOMERS", "ORDERS", "ORDER_DETAILS");
			command.setDmlBatchSize(batchSize);
			command.setQueryCommitInterval(batchSize);
			command.setDirectory(new File("./src/test/resources/com/sqlapp/data/db/command/generator/test5"));
			command.setCloseDataSource(false);
			dropTables(command, "ORDER_DETAILS", "ORDERS", "CUSTOMERS", "PRODUCTS");
			String sql = this.getResource("create_table_products.sql");
			this.executeSql(command, sql);
			sql = this.getResource("create_table_customers.sql");
			this.executeSql(command, sql);
			sql = this.getResource("create_table_orders.sql");
			this.executeSql(command, sql);
			sql = this.getResource("create_table_order_details.sql");
			this.executeSql(command, sql);
			command.run();
			command.run();
			dropTables(command, "ORDER_DETAILS", "ORDERS", "CUSTOMERS", "PRODUCTS");
		} finally {
			ds.close();
		}
	}

}
