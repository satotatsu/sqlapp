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

import com.sqlapp.data.db.command.generator.factory.TableGeneratorSettingFactory;
import com.sqlapp.data.db.command.generator.setting.TableGeneratorSetting;
import com.zaxxer.hikari.HikariDataSource;

public class ConvertGeneratorSettingCommandTest extends AbstractGeneratorCommandTest {

	TableGeneratorSettingFactory factory = new TableGeneratorSettingFactory();

	@Test
	public void testExcel() throws ParseException, IOException, SQLException {
		initialize(GeneratorSettingFileType.EXCEL2007);
		ConvertGeneratorSettingCommand command = new ConvertGeneratorSettingCommand();
		command.setDirectory(testProjectDir);
		GeneratorSettingFileType fonvertFileType = GeneratorSettingFileType.JSON;
		command.setFileType(fonvertFileType);
		command.setRemoveOriginalFile(true);
		command.run();
		checkFiles(fonvertFileType);
	}

	private void checkFiles(GeneratorSettingFileType fileType) {
		File[] files = testProjectDir.listFiles();
		int i = 0;
		for (File file : files) {
			TableGeneratorSetting setting = factory.fromFile(file);
			assertEquals(fileType, setting.getFileType());
			if ("PRODUCTS".equals(setting.getName())) {
				i++;
			}
			if ("CUSTOMERS".equals(setting.getName())) {
				i++;
			}
			if ("ORDERS".equals(setting.getName())) {
				i++;
			}
			if ("ORDER_DETAILS".equals(setting.getName())) {
				i++;
			}
		}
		assertEquals(4, i);
	}

	private void initialize(GeneratorSettingFileType fileType) {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateGeneratorSettingCommand command = new GenerateGeneratorSettingCommand();
			command.setDataSource(ds);
			command.setFileType(fileType);
			// command.setOutputDirectory(new File("./"));
			command.setIncludeTables("PRODUCTS", "CUSTOMERS", "ORDERS", "ORDER_DETAILS");
			command.setCloseDataSource(false);
			command.setOutputDirectory(testProjectDir);
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
			File file = new File(testProjectDir,
					"ORDER_DETAILS." + command.getFileType().getWorkbookFileType().getFileExtension());
			assertTrue(file.exists());
		} finally {
			ds.close();
		}
	}
}
