/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

class GenerateDataConfigAndInsertCommandTest extends AbstractGeneratorCommandTest {

	@Test
	void test() {
		test(command -> {

		});
	}

	private void test(Consumer<GenerateDataConfigAndInsertCommand> cons) {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateDataConfigAndInsertCommand command = new GenerateDataConfigAndInsertCommand();
			command.setDataSource(ds);
			// command.setOutputDirectory(new File("./"));
			command.setIncludeTables("PRODUCTS", "PRODUCT_PRICES");
			command.setCloseDataSource(false);
			command.setOutputDirectory(testProjectDir);
			dropTables(command, "PRODUCTS", "PRODUCT_PRICES");
			String sql = this.getResource("create_table_products.sql");
			this.executeSql(command, sql);
			sql = this.getResource("create_table_product_prices.sql");
			this.executeSql(command, sql);
			cons.accept(command);
			command.run();
			dropTables(command, "PRODUCT_PRICES", "PRODUCTS");
			File file = new File(testProjectDir,
					"PRODUCT_PRICES." + command.getFileType().getWorkbookFileType().getFileExtension());
			assertTrue(file.exists());
		} finally {
			ds.close();
		}
	}

}
