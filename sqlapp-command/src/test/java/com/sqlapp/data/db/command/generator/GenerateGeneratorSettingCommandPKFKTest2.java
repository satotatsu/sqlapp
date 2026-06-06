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
import static org.junit.jupiter.api.Assertions.assertNull;
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

public class GenerateGeneratorSettingCommandPKFKTest2 extends AbstractGeneratorCommandTest {

	private TableGeneratorSettingFactory factory = new TableGeneratorSettingFactory();

	@Test
	public void testExcel() throws ParseException, IOException, SQLException {
		testFile(GeneratorSettingFileType.EXCEL2007);
	}

	private String startValueSqlBalance = """
			SELECT
				COALESCE( MAX( WAREHOUSE_ID ), 0 ) AS WAREHOUSE_ID
				, COALESCE( MAX( PRODUCT_ID ), 0 ) AS PRODUCT_ID
			FROM INVENTORY_BALANCES""";
	private String insertSql = """
			INSERT INTO INVENTORY_BALANCES
			(
				  WAREHOUSE_ID
				, PRODUCT_ID
				, QUANTITY
			)
			SELECT
				  /*WAREHOUSE_ID*/0
				, /*PRODUCT_ID*/0
				, /*QUANTITY*/0
			FROM (VALUES(0))
			WHERE
			NOT EXISTS (
				SELECT 1
				FROM INVENTORY_BALANCES
				WHERE 1=1
				AND WAREHOUSE_ID = /*WAREHOUSE_ID*/0
				AND PRODUCT_ID = /*PRODUCT_ID*/0
			)
			""";

	private String warehousSqlBalance = """
			SELECT
				WAREHOUSE_ID
			FROM PUBLIC.WAREHOUSES""";
	private String productSqlBalance = """
			SELECT
				PRODUCT_ID
			FROM PUBLIC.PRODUCTS""";

	private void testFile(GeneratorSettingFileType fileType) {
		test(cmd -> {
			cmd.setFileType(fileType);
		});
		testPRODUCTFile(fileType);
		testINVENTORY_BALANCESFile(fileType);
	}

	private void testINVENTORY_BALANCESFile(GeneratorSettingFileType fileType) {
		File file = new File(testProjectDir, "INVENTORY_BALANCES." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorSetting setting = factory.fromFile(file);
		assertEquals("INVENTORY_BALANCES", setting.getName());
		assertEquals(3, setting.getColumns().size());
		assertEquals(3, setting.getQuerys().size());
		assertEquals(100, setting.getNumberOfRows());
		assertEquals(startValueSqlBalance, setting.getStartValueSql());
		assertEquals(insertSql, setting.getInsertSql());
		assertEquals(3, setting.getQuerys().size());
		//
		ColumnGeneratorSetting colSetting = setting.getColumns().get("WAREHOUSE_ID");
		assertEquals("WAREHOUSE_ID", colSetting.getName());
		assertEquals("_previous.WAREHOUSE_ID", colSetting.getNextValue());
		assertEquals("FK_INVENTORY_BALANCES_WAREHOUSE", colSetting.getGenerationGroup());
		assertNull(colSetting.getMinValue());
		assertNull(colSetting.getMaxValue());
		QueryGeneratorSetting queryGeneratorSetting = setting.getQuerys().get(colSetting.getGenerationGroup());
		assertEquals(warehousSqlBalance, queryGeneratorSetting.getSelectSql());
		//
		colSetting = setting.getColumns().get("PRODUCT_ID");
		assertEquals("PRODUCT_ID", colSetting.getName());
		assertEquals("_previous.PRODUCT_ID", colSetting.getNextValue());
		assertEquals("FK_INVENTORY_BALANCES_PRODUCTS", colSetting.getGenerationGroup());
		assertNull(colSetting.getMinValue());
		assertNull(colSetting.getMaxValue());
		queryGeneratorSetting = setting.getQuerys().get(colSetting.getGenerationGroup());
		assertEquals(productSqlBalance, queryGeneratorSetting.getSelectSql());
		//
		colSetting = setting.getColumns().get("QUANTITY");
		assertEquals("QUANTITY", colSetting.getName());
		assertEquals("_previous.QUANTITY + 1", colSetting.getNextValue());
		assertEquals("1", colSetting.getMinValue());
		assertEquals("10000000000000", colSetting.getMaxValue());
		assertNull(colSetting.getGenerationGroup());
	}

	private String startValueSqlProduct = """
			SELECT
				COALESCE( MAX( PRODUCT_ID ), 0 ) AS PRODUCT_ID
			FROM PRODUCTS""";
	private String insertSqlProduct = """
			INSERT INTO PRODUCTS
			(
				  PRODUCT_CODE
				, PRODUCT_NAME
				, CATEGORY_ID
				, ACTIVE
			)
			VALUES
			(
				  /*PRODUCT_CODE*/''
				, /*PRODUCT_NAME*/''
				, /*CATEGORY_ID*/0
				, /*ACTIVE*/TRUE
			)
			""";

	private void testPRODUCTFile(GeneratorSettingFileType fileType) {
		File file = new File(testProjectDir, "PRODUCTS." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorSetting setting = factory.fromFile(file);
		assertEquals("PRODUCTS", setting.getName());
		assertEquals(5, setting.getColumns().size());
		assertEquals(1, setting.getQuerys().size());
		assertEquals(100, setting.getNumberOfRows());
		assertEquals(startValueSqlProduct, setting.getStartValueSql());
		assertEquals(insertSqlProduct, setting.getInsertSql());
		assertEquals(1, setting.getQuerys().size());
		//
		ColumnGeneratorSetting colSetting = setting.getColumns().get("PRODUCT_ID");
		assertEquals("PRODUCT_ID", colSetting.getName());
		assertEquals("_previous.PRODUCT_ID + 1", colSetting.getNextValue());
		assertNull(colSetting.getGenerationGroup());
		assertEquals("1", colSetting.getMinValue());
		assertEquals("9223372036854775807", colSetting.getMaxValue());
		assertNull(colSetting.getGenerationGroup());
		//
		colSetting = setting.getColumns().get("PRODUCT_CODE");
		assertEquals("PRODUCT_CODE", colSetting.getName());
		assertEquals("nextAlphaNumeric( 30 )", colSetting.getNextValue());
		assertEquals("nextAlphaNumeric( 30 )", colSetting.getMinValue());
		assertNull(colSetting.getMaxValue());
		assertNull(colSetting.getGenerationGroup());
	}

	private void test(Consumer<GenerateGeneratorSettingCommand> cons) {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateGeneratorSettingCommand command = new GenerateGeneratorSettingCommand();
			command.setDataSource(ds);
			// command.setOutputDirectory(new File("./"));
			command.setIncludeTables("INVENTORY_BALANCES", "PRODUCTS", "WAREHOUSES");
			command.setCloseDataSource(false);
			command.setOutputDirectory(testProjectDir);
			dropTables(command, "INVENTORY_BALANCES", "PRODUCTS", "WAREHOUSES");
			String sql = this.getResource("create_table_products.sql");
			this.executeSql(command, sql);
			sql = this.getResource("create_table_warehouses.sql");
			this.executeSql(command, sql);
			sql = this.getResource("create_table_inventory_balances.sql");
			this.executeSql(command, sql);
			command.run();
			dropTables(command, "INVENTORY_BALANCES", "PRODUCTS", "WAREHOUSES");
			File file = new File(testProjectDir,
					"INVENTORY_BALANCES." + command.getFileType().getWorkbookFileType().getFileExtension());
			assertTrue(file.exists());
		} finally {
			ds.close();
		}
	}
}
