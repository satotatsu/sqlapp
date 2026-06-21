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

public class GenerateDataInsertCommandFKTest extends AbstractGeneratorCommandTest {

	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateDataConfigAndInsertCommand command = new GenerateDataConfigAndInsertCommand();
			command.setDataSource(ds);
			command.setOutputDirectory(testProjectDir);
			command.setIncludeTables("PRODUCTS", "PRODUCT_PRICES");
			command.setDmlBatchSize(1000);
			command.setQueryCommitInterval(4);
			command.setCloseDataSource(false);
			dropTables(command, "PRODUCTS", "PRODUCT_PRICES");
			String sql = this.getResource("create_table_products.sql");
			this.executeSql(command, sql);
			sql = this.getResource("create_table_product_prices.sql");
			this.executeSql(command, sql);
			// command.setConsoleOutputLevel(ConsoleOutputLevel.DEBUG);
			command.run();
			dropTables(command, "PRODUCT_PRICES", "PRODUCTS");
		} finally {
			ds.close();
		}
	}
}
