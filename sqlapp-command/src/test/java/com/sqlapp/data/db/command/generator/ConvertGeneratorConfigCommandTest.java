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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.dataconfig.ConfigFileType;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorConfigFactory;
import com.zaxxer.hikari.HikariDataSource;

public class ConvertGeneratorConfigCommandTest extends AbstractGeneratorCommandTest {

	TableGeneratorConfigFactory factory = new TableGeneratorConfigFactory();

	@Test
	public void testExcelToJson() throws ParseException, IOException, SQLException {
		initialize(ConfigFileType.EXCEL, testProjectDir);
		ConvertGeneratorConfigCommand command = new ConvertGeneratorConfigCommand();
		command.setDirectory(testProjectDir);
		ConfigFileType fonvertFileType = ConfigFileType.JSON;
		command.setFileType(fonvertFileType);
		command.setRemoveOriginalFile(true);
		command.run();
		checkFiles(ConfigFileType.JSON, testProjectDir);
	}

	@Test
	public void testExcelToYaml() throws ParseException, IOException, SQLException {
		File directory = new File(testProjectDir, "/excel");
		File outputDirectory = new File(testProjectDir, "/yaml");
		initialize(ConfigFileType.EXCEL, directory);
		ConvertGeneratorConfigCommand command = new ConvertGeneratorConfigCommand();
		command.setDirectory(directory);
		command.setOutputDirectory(outputDirectory);
		ConfigFileType fonvertFileType = ConfigFileType.YAML;
		command.setFileType(fonvertFileType);
		command.setRemoveOriginalFile(false);
		command.run();
		checkFiles(ConfigFileType.EXCEL, directory);
		checkFiles(ConfigFileType.YAML, outputDirectory);
	}

	private void checkFiles(ConfigFileType fileType, File directory) {
		File[] files = directory.listFiles();
		int i = 0;
		for (File file : files) {
			TableGeneratorConfig config = factory.fromFile(file);
			assertEquals(fileType, config.getFileType());
			if ("PRODUCTS".equals(config.getName())) {
				i++;
			}
			if ("CUSTOMERS".equals(config.getName())) {
				i++;
			}
			if ("ORDERS".equals(config.getName())) {
				i++;
			}
			if ("ORDER_DETAILS".equals(config.getName())) {
				i++;
			}
		}
		assertEquals(4, i);
	}

	private void initialize(ConfigFileType fileType, File outputDir) {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateDataConfigCommand command = new GenerateDataConfigCommand();
			command.setDataSource(ds);
			command.setFileType(fileType);
			// command.setOutputDirectory(new File("./"));
			command.setIncludeTables("PRODUCTS", "CUSTOMERS", "ORDERS", "ORDER_DETAILS");
			command.setCloseDataSource(false);
			command.setOutputDirectory(outputDir);
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
			dropTables(command, "ORDER_DETAILS", "ORDERS", "CUSTOMERS", "PRODUCTS");
			File file = new File(outputDir,
					"ORDER_DETAILS." + command.getFileType().getWorkbookFileType().getFileExtension());
			assertTrue(file.exists());
		} finally {
			ds.close();
		}
	}
}
