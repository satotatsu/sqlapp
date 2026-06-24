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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.dataconfig.ConfigFileType;
import com.sqlapp.data.db.command.generator.config.ColumnGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.QueryGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorConfigFactory;
import com.zaxxer.hikari.HikariDataSource;

public class GenerateDataConfigCommandFKTest2 extends AbstractGeneratorCommandTest {

	private TableGeneratorConfigFactory factory = new TableGeneratorConfigFactory();

	@Test
	public void testExcel() throws ParseException, IOException, SQLException {
		testFile(ConfigFileType.EXCEL);
	}

	private String resultSql = """
			SELECT
				ID
			FROM PUBLIC.PRODUCTS""";

	private void testFile(ConfigFileType fileType) {
		test(cmd -> {
			cmd.setFileType(fileType);
		});
		File file = new File(testProjectDir, "PRODUCT_PRICES." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorConfig config = factory.fromFile(file);
		assertEquals("PRODUCT_PRICES", config.getName());
		assertEquals(5, config.getColumns().size());
		assertEquals(2, config.getQueries().size());
		//
		ColumnGeneratorConfig colConfig = config.getColumns().get("PRODUCT_ID");
		assertEquals("PRODUCT_ID", colConfig.getName());
		assertNotNull(colConfig.getLookupGroup());
		assertTrue(config.getQueries().size() > 1);
		final QueryGeneratorConfig queryGeneratorConfig = config.getQueries().get(colConfig.getLookupGroup());
		assertEquals(resultSql, queryGeneratorConfig.getSelectSql());
		String columnMappingExpressionj = """
				[
					["PRODUCT_ID":ID]
				]""";
		assertEquals(columnMappingExpressionj, queryGeneratorConfig.getColumnMappingExpression());
	}

	private void test(Consumer<GenerateDataConfigCommand> cons) {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateDataConfigCommand command = new GenerateDataConfigCommand();
			command.setDataSource(ds);
			// command.setOutputDirectory(new File("./"));
			command.setIncludeTables("PRODUCTS", "PRODUCT_PRICES");
			command.setCloseDataSource(false);
			command.setOutputDirectory(testProjectDir);
			dropTables(command, "PRODUCTS", "PRODUCT_PRICES");
			String sql = this.getResource("create_table_products2.sql");
			this.executeSql(command, sql);
			sql = this.getResource("create_table_product_prices2.sql");
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
