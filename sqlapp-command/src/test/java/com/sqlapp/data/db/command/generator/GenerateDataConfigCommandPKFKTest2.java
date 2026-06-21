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

import com.sqlapp.data.db.command.dataconfig.ConfigFileType;
import com.sqlapp.data.db.command.generator.config.ColumnGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.QueryGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorConfigFactory;
import com.zaxxer.hikari.HikariDataSource;

public class GenerateDataConfigCommandPKFKTest2 extends AbstractGeneratorCommandTest {

	private TableGeneratorConfigFactory factory = new TableGeneratorConfigFactory();

	@Test
	public void testExcel() throws ParseException, IOException, SQLException {
		testFile(ConfigFileType.EXCEL);
	}

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
			WHERE NOT EXISTS
			(
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

	private void testFile(ConfigFileType fileType) {
		test(cmd -> {
			cmd.setFileType(fileType);
		});
		testPRODUCTFile(fileType);
		testINVENTORY_BALANCESFile(fileType);
	}

	private void testINVENTORY_BALANCESFile(ConfigFileType fileType) {
		File file = new File(testProjectDir, "INVENTORY_BALANCES." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorConfig config = factory.fromFile(file);
		assertEquals("INVENTORY_BALANCES", config.getName());
		assertEquals(3, config.getColumns().size());
		assertEquals(3, config.getQueries().size());
		assertEquals("iterator(100)", config.getDataSourceExpression());
		assertEquals("[\"_index\":value]", config.getColumnMappingExpression());
		String startValueSqlBalance = """
				SELECT
					COALESCE( MAX( WAREHOUSE_ID ), 0 ) AS WAREHOUSE_ID
					, COALESCE( MAX( PRODUCT_ID ), 0 ) AS PRODUCT_ID
				FROM INVENTORY_BALANCES""";
		assertEquals(startValueSqlBalance, config.getStartValueSql());
		assertEquals(insertSql, config.getInsertSql());
		assertEquals(3, config.getQueries().size());
		//
		ColumnGeneratorConfig colConfig = config.getColumns().get("WAREHOUSE_ID");
		assertEquals("WAREHOUSE_ID", colConfig.getName());
		assertEquals("_previous.WAREHOUSE_ID", colConfig.getNextValue());
		assertEquals("FK_INVENTORY_BALANCES_WAREHOUSE", colConfig.getLookupGroup());
		assertNull(colConfig.getMinValue());
		assertNull(colConfig.getMaxValue());
		QueryGeneratorConfig queryGeneratorConfig = config.getQueries().get(colConfig.getLookupGroup());
		assertEquals(warehousSqlBalance, queryGeneratorConfig.getSelectSql());
		//
		colConfig = config.getColumns().get("PRODUCT_ID");
		assertEquals("PRODUCT_ID", colConfig.getName());
		assertEquals("_previous.PRODUCT_ID", colConfig.getNextValue());
		assertEquals("FK_INVENTORY_BALANCES_PRODUCTS", colConfig.getLookupGroup());
		assertNull(colConfig.getMinValue());
		assertNull(colConfig.getMaxValue());
		queryGeneratorConfig = config.getQueries().get(colConfig.getLookupGroup());
		assertEquals(productSqlBalance, queryGeneratorConfig.getSelectSql());
		//
		colConfig = config.getColumns().get("QUANTITY");
		assertEquals("QUANTITY", colConfig.getName());
		assertEquals("_previous.QUANTITY + 1", colConfig.getNextValue());
		assertEquals("1", colConfig.getMinValue());
		assertEquals("10000000000000", colConfig.getMaxValue());
		assertNull(colConfig.getLookupGroup());
	}

	private String insertSqlProduct = """
			INSERT INTO PRODUCTS
			(
				  PRODUCT_CODE
				, PRODUCT_NAME
				, CATEGORY_ID
				, ACTIVE
			)
			SELECT
				  /*PRODUCT_CODE*/''
				, /*PRODUCT_NAME*/''
				, /*CATEGORY_ID*/0
				, /*ACTIVE*/TRUE
			FROM (VALUES(0))
			WHERE NOT EXISTS
			(
				SELECT 1
				FROM PRODUCTS
				WHERE 1=1
					AND PRODUCT_CODE = /*PRODUCT_CODE*/''
			)
			""";

	private void testPRODUCTFile(ConfigFileType fileType) {
		File file = new File(testProjectDir, "PRODUCTS." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorConfig config = factory.fromFile(file);
		assertEquals("PRODUCTS", config.getName());
		assertEquals(5, config.getColumns().size());
		assertEquals(1, config.getQueries().size());
		assertEquals("iterator(100)", config.getDataSourceExpression());
		assertEquals("[\"_index\":value]", config.getColumnMappingExpression());
		String startValueSqlProduct = """
				SELECT
					COALESCE( MAX( PRODUCT_ID ), 0 ) AS PRODUCT_ID
				FROM PRODUCTS""";
		assertEquals(startValueSqlProduct, config.getStartValueSql());
		assertEquals(insertSqlProduct, config.getInsertSql());
		assertEquals(1, config.getQueries().size());
		//
		ColumnGeneratorConfig colConfig = config.getColumns().get("PRODUCT_ID");
		assertEquals("PRODUCT_ID", colConfig.getName());
		assertEquals("_previous.PRODUCT_ID + 1", colConfig.getNextValue());
		assertNull(colConfig.getLookupGroup());
		assertEquals("1", colConfig.getMinValue());
		assertEquals("9223372036854775807", colConfig.getMaxValue());
		assertNull(colConfig.getLookupGroup());
		//
		colConfig = config.getColumns().get("PRODUCT_CODE");
		assertEquals("PRODUCT_CODE", colConfig.getName());
		assertEquals("nextAlphaNumeric( 30 )", colConfig.getNextValue());
		assertEquals("nextAlphaNumeric( 30 )", colConfig.getMinValue());
		assertNull(colConfig.getMaxValue());
		assertNull(colConfig.getLookupGroup());
	}

	private void test(Consumer<GenerateDataConfigCommand> cons) {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateDataConfigCommand command = new GenerateDataConfigCommand();
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
