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

public class GenerateDataConfigCommandUKTest extends AbstractGeneratorCommandTest {

	private TableGeneratorConfigFactory factory = new TableGeneratorConfigFactory();

	@Test
	public void testExcel() throws ParseException, IOException, SQLException {
		testFile(ConfigFileType.EXCEL);
	}

	private String insertSql = """
			INSERT INTO ORDER_DETAILS
			(
				  ORDER_ID
				, LINE_NO
				, PRODUCT_ID
				, QUANTITY
				, UNIT_PRICE
				, AMOUNT
			)
			SELECT
				  /*ORDER_ID*/0
				, /*LINE_NO*/0
				, /*PRODUCT_ID*/0
				, /*QUANTITY*/0
				, /*UNIT_PRICE*/0
				, /*AMOUNT*/0
			FROM (VALUES(0))
			WHERE NOT EXISTS
			(
				SELECT 1
				FROM ORDER_DETAILS
				WHERE 1=1
					AND ORDER_ID = /*ORDER_ID*/0
					AND LINE_NO = /*LINE_NO*/0
			)
			""";

	private void testFile(ConfigFileType fileType) {
		test(cmd -> {
			cmd.setFileType(fileType);
		});
		testCUSTOMERS(fileType);
		testORDERS(fileType);
		testORDER_DETAILS(fileType);
	}

	private void testCUSTOMERS(ConfigFileType fileType) {
		File file = new File(testProjectDir, "CUSTOMERS." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorConfig config = factory.fromFile(file);
		assertEquals("CUSTOMERS", config.getName());
		assertEquals(5, config.getColumns().size());
		assertEquals(1, config.getQueries().size());
		assertEquals("iterator(100)", config.getDataSourceExpression());
		assertEquals("[\"_index\":value]", config.getColumnMappingExpression());
		String strartValueSql = """
				SELECT
					COALESCE( MAX( CUSTOMER_ID ), 0 ) AS CUSTOMER_ID
				FROM CUSTOMERS""";
		assertEquals(strartValueSql, config.getStartValueSql());
		String insertSql = """
				INSERT INTO CUSTOMERS
				(
					  CUSTOMER_CODE
					, CUSTOMER_NAME
					, CLOSING_DAY
					, CREATED_AT
				)
				SELECT
					  /*CUSTOMER_CODE*/''
					, /*CUSTOMER_NAME*/''
					, /*CLOSING_DAY*/0
					, /*CREATED_AT*/CURRENT_TIMESTAMP
				FROM (VALUES(0))
				WHERE NOT EXISTS
				(
					SELECT 1
					FROM CUSTOMERS
					WHERE 1=1
						AND CUSTOMER_CODE = /*CUSTOMER_CODE*/''
				)
					""";
		assertEquals(insertSql, config.getInsertSql());
		assertEquals(1, config.getQueries().size());
		//
		ColumnGeneratorConfig colConfig = config.getColumns().get("CUSTOMER_ID");
		assertEquals("CUSTOMER_ID", colConfig.getName());
		assertEquals("_previous.CUSTOMER_ID + 1", colConfig.getNextValue());
		assertNull(colConfig.getLookupGroup());
		assertEquals("1", colConfig.getMinValue());
		assertEquals("9223372036854775807", colConfig.getMaxValue());
		//
		colConfig = config.getColumns().get("CUSTOMER_CODE");
		assertEquals("CUSTOMER_CODE", colConfig.getName());
		assertEquals("nextAlphaNumeric( 20 )", colConfig.getNextValue());
		assertEquals("nextAlphaNumeric( 20 )", colConfig.getMinValue());
		assertNull(colConfig.getMaxValue());
		assertNull(colConfig.getLookupGroup());
		//
		colConfig = config.getColumns().get("CUSTOMER_NAME");
		assertEquals("CUSTOMER_NAME", colConfig.getName());
		assertEquals("nextAlphaNumeric( 200 )", colConfig.getNextValue());
		assertEquals("nextAlphaNumeric( 200 )", colConfig.getMinValue());
		assertNull(colConfig.getMaxValue());
		assertNull(colConfig.getLookupGroup());
		//
		colConfig = config.getColumns().get("CLOSING_DAY");
		assertEquals("CLOSING_DAY", colConfig.getName());
		assertEquals("_previous.CLOSING_DAY + 1", colConfig.getNextValue());
		assertEquals("1", colConfig.getMinValue());
		assertEquals("2147483647", colConfig.getMaxValue());
		assertNull(colConfig.getLookupGroup());
		//
		colConfig = config.getColumns().get("CREATED_AT");
		assertEquals("CREATED_AT", colConfig.getName());
		assertEquals("addMilliSeconds(_previous.CREATED_AT,1)", colConfig.getNextValue());
		assertTrue(colConfig.getMinValue().startsWith("LocalDateTime.of("));
		assertEquals("addMonths(_min.CREATED_AT,1)", colConfig.getMaxValue());
		assertNull(colConfig.getLookupGroup());
	}

	private void testORDER_DETAILS(ConfigFileType fileType) {
		File file = new File(testProjectDir, "ORDER_DETAILS." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorConfig config = factory.fromFile(file);
		assertEquals("ORDER_DETAILS", config.getName());
		assertEquals(7, config.getColumns().size());
		assertEquals(3, config.getQueries().size());
		assertEquals("iterator(100)", config.getDataSourceExpression());
		assertEquals("[\"_index\":value]", config.getColumnMappingExpression());
		String startValueSqlBalance = """
				SELECT
					COALESCE( MAX( ORDER_DETAIL_ID ), 0 ) AS ORDER_DETAIL_ID
				FROM ORDER_DETAILS""";
		assertEquals(startValueSqlBalance, config.getStartValueSql());
		assertEquals(insertSql, config.getInsertSql());
		assertEquals(3, config.getQueries().size());
		//
		ColumnGeneratorConfig colConfig = config.getColumns().get("ORDER_DETAIL_ID");
		assertEquals("ORDER_DETAIL_ID", colConfig.getName());
		assertEquals("_previous.ORDER_DETAIL_ID + 1", colConfig.getNextValue());
		assertNull(colConfig.getLookupGroup());
		assertEquals("1", colConfig.getMinValue());
		assertEquals("9223372036854775807", colConfig.getMaxValue());
		//
		colConfig = config.getColumns().get("ORDER_ID");
		assertEquals("ORDER_ID", colConfig.getName());
		assertEquals("_previous.ORDER_ID", colConfig.getNextValue());
		assertEquals("FK_ORDER_DETAILS_ORDER", colConfig.getLookupGroup());
		assertNull(colConfig.getMinValue());
		assertNull(colConfig.getMaxValue());
		//
		colConfig = config.getColumns().get("ORDER_ID");
		assertEquals("ORDER_ID", colConfig.getName());
		assertEquals("_previous.ORDER_ID", colConfig.getNextValue());
		assertEquals("FK_ORDER_DETAILS_ORDER", colConfig.getLookupGroup());
		assertNull(colConfig.getMinValue());
		assertNull(colConfig.getMaxValue());
		//
		colConfig = config.getColumns().get("LINE_NO");
		assertEquals("LINE_NO", colConfig.getName());
		assertEquals("_previous.LINE_NO + 1", colConfig.getNextValue());
		assertEquals("1", colConfig.getMinValue());
		assertEquals("2147483647", colConfig.getMaxValue());
		//
		colConfig = config.getColumns().get("PRODUCT_ID");
		assertEquals("PRODUCT_ID", colConfig.getName());
		assertEquals("_previous.PRODUCT_ID", colConfig.getNextValue());
		assertEquals("FK_ORDER_DETAILS_PRODUCT", colConfig.getLookupGroup());
		assertNull(colConfig.getMinValue());
		assertNull(colConfig.getMaxValue());
		QueryGeneratorConfig queryGeneratorConfig = config.getQueries().get(colConfig.getLookupGroup());
		String productSql = """
				SELECT
					PRODUCT_ID
				FROM PUBLIC.PRODUCTS""";
		assertEquals(productSql, queryGeneratorConfig.getSelectSql());
		//
		colConfig = config.getColumns().get("QUANTITY");
		assertEquals("QUANTITY", colConfig.getName());
		assertEquals("_previous.QUANTITY + 1", colConfig.getNextValue());
		assertEquals("1", colConfig.getMinValue());
		assertEquals("10000000000000", colConfig.getMaxValue());
		assertNull(colConfig.getLookupGroup());
		//
		colConfig = config.getColumns().get("UNIT_PRICE");
		assertEquals("UNIT_PRICE", colConfig.getName());
		assertEquals("_previous.UNIT_PRICE + 1", colConfig.getNextValue());
		assertEquals("1", colConfig.getMinValue());
		assertEquals("10000000000000", colConfig.getMaxValue());
		assertNull(colConfig.getLookupGroup());
	}

	private void testORDERS(ConfigFileType fileType) {
		File file = new File(testProjectDir, "ORDERS." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorConfig config = factory.fromFile(file);
		assertEquals("ORDERS", config.getName());
		assertEquals(5, config.getColumns().size());
		assertEquals(2, config.getQueries().size());
		assertEquals("iterator(100)", config.getDataSourceExpression());
		assertEquals("[\"_index\":value]", config.getColumnMappingExpression());
		String strartValueSql = """
				SELECT
					COALESCE( MAX( ORDER_ID ), 0 ) AS ORDER_ID
				FROM ORDERS""";
		assertEquals(strartValueSql, config.getStartValueSql());
		String insertSql = """
				INSERT INTO ORDERS
				(
					  ORDER_NO
					, CUSTOMER_ID
					, ORDER_DATE
					, ORDER_STATUS
				)
				SELECT
					  /*ORDER_NO*/''
					, /*CUSTOMER_ID*/0
					, /*ORDER_DATE*/CURRENT_DATE
					, /*ORDER_STATUS*/''
				FROM (VALUES(0))
				WHERE NOT EXISTS
				(
					SELECT 1
					FROM ORDERS
					WHERE 1=1
						AND ORDER_NO = /*ORDER_NO*/''
				)
				""";
		assertEquals(insertSql, config.getInsertSql());
		assertEquals(2, config.getQueries().size());
		//
		ColumnGeneratorConfig colConfig = config.getColumns().get("ORDER_ID");
		assertEquals("ORDER_ID", colConfig.getName());
		assertEquals("_previous.ORDER_ID + 1", colConfig.getNextValue());
		assertNull(colConfig.getLookupGroup());
		assertEquals("1", colConfig.getMinValue());
		assertEquals("9223372036854775807", colConfig.getMaxValue());
		//
		colConfig = config.getColumns().get("CUSTOMER_ID");
		assertEquals("CUSTOMER_ID", colConfig.getName());
		assertEquals("_previous.CUSTOMER_ID", colConfig.getNextValue());
		assertEquals("FK_ORDERS_CUSTOMER", colConfig.getLookupGroup());
		assertNull(colConfig.getMinValue());
		assertNull(colConfig.getMaxValue());
		QueryGeneratorConfig queryGeneratorConfig = config.getQueries().get(colConfig.getLookupGroup());
		String productSqlBalance = """
				SELECT
					CUSTOMER_ID
				FROM PUBLIC.CUSTOMERS""";
		assertEquals(productSqlBalance, queryGeneratorConfig.getSelectSql());
		//
		colConfig = config.getColumns().get("ORDER_DATE");
		assertEquals("ORDER_DATE", colConfig.getName());
		assertEquals("addDays(_previous.ORDER_DATE,1)", colConfig.getNextValue());
		assertTrue(colConfig.getMinValue().startsWith("LocalDate.of("));
		assertEquals("addMonths(_min.ORDER_DATE,1)", colConfig.getMaxValue());
		assertNull(colConfig.getLookupGroup());
		//
		colConfig = config.getColumns().get("ORDER_STATUS");
		assertEquals("ORDER_STATUS", colConfig.getName());
		assertEquals("nextAlphaNumeric( 20 )", colConfig.getNextValue());
		assertEquals("nextAlphaNumeric( 20 )", colConfig.getMinValue());
		assertNull(colConfig.getMaxValue());
		assertNull(colConfig.getLookupGroup());
	}

	private void test(Consumer<GenerateDataConfigCommand> cons) {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateDataConfigCommand command = new GenerateDataConfigCommand();
			command.setDataSource(ds);
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
