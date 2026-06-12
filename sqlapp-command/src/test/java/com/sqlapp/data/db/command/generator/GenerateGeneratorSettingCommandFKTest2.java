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

import com.sqlapp.data.db.command.generator.factory.TableGeneratorSettingFactory;
import com.sqlapp.data.db.command.generator.setting.ColumnGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.QueryGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.TableGeneratorSetting;
import com.zaxxer.hikari.HikariDataSource;

public class GenerateGeneratorSettingCommandFKTest2 extends AbstractGeneratorCommandTest {

	private TableGeneratorSettingFactory factory = new TableGeneratorSettingFactory();

	@Test
	public void testExcel() throws ParseException, IOException, SQLException {
		testFile(GeneratorSettingFileType.EXCEL);
	}

	private String resultSql = """
			SELECT
				ID
			FROM PUBLIC.PRODUCTS""";

	private void testFile(GeneratorSettingFileType fileType) {
		test(cmd -> {
			cmd.setFileType(fileType);
		});
		File file = new File(testProjectDir, "PRODUCT_PRICES." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorSetting setting = factory.fromFile(file);
		assertEquals("PRODUCT_PRICES", setting.getName());
		assertEquals(5, setting.getColumns().size());
		assertEquals(2, setting.getQuerys().size());
		//
		ColumnGeneratorSetting colSetting = setting.getColumns().get("PRODUCT_ID");
		assertEquals("PRODUCT_ID", colSetting.getName());
		assertNotNull(colSetting.getGenerationGroup());
		assertTrue(setting.getQuerys().size() > 1);
		final QueryGeneratorSetting queryGeneratorSetting = setting.getQuerys().get(colSetting.getGenerationGroup());
		assertEquals(resultSql, queryGeneratorSetting.getSelectSql());
		String columnMappingExpressionj = """
				[
					["PRODUCT_ID":ID]
				]""";
		assertEquals(columnMappingExpressionj, queryGeneratorSetting.getColumnMappingExpression());
	}

	private void test(Consumer<GenerateGeneratorSettingCommand> cons) {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateGeneratorSettingCommand command = new GenerateGeneratorSettingCommand();
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
