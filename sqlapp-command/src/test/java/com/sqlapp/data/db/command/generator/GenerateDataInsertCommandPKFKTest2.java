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

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

public class GenerateDataInsertCommandPKFKTest2 extends AbstractGeneratorCommandTest {

	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		testRun(1);
		testRun(10);
		// testRun(100);
	}

	public void testRun(int batchSize) throws ParseException, IOException, SQLException {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateGeneratorSettingAndInsertCommand command = new GenerateGeneratorSettingAndInsertCommand();
			command.setDataSource(ds);
			command.setIncludeTables("INVENTORY_BALANCES", "PRODUCTS", "WAREHOUSES");
			command.setDmlBatchSize(batchSize);
			command.setQueryCommitInterval(batchSize);
			command.setCloseDataSource(false);
			dropTables(command, "INVENTORY_BALANCES", "PRODUCTS", "WAREHOUSES");
			String sql = this.getResource("create_table_products.sql");
			this.executeSql(command, sql);
			sql = this.getResource("create_table_warehouses.sql");
			this.executeSql(command, sql);
			sql = this.getResource("create_table_inventory_balances.sql");
			this.executeSql(command, sql);
			command.run();
			command.run();
			dropTables(command, "INVENTORY_BALANCES", "PRODUCTS", "WAREHOUSES");
		} finally {
			ds.close();
		}
	}

}
