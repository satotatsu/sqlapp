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

public class GenerateGeneratorSettingCommandUKTest extends AbstractGeneratorCommandTest {

	private TableGeneratorSettingFactory factory = new TableGeneratorSettingFactory();

	@Test
	public void testExcel() throws ParseException, IOException, SQLException {
		testFile(GeneratorSettingFileType.EXCEL);
	}

	private String startValueSqlBalance = """
			SELECT
				COALESCE( MAX( ORDER_DETAIL_ID ), 0 ) AS ORDER_DETAIL_ID
			FROM ORDER_DETAILS""";
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

	private void testFile(GeneratorSettingFileType fileType) {
		test(cmd -> {
			cmd.setFileType(fileType);
		});
		testCUSTOMERS(fileType);
		testORDERS(fileType);
		testORDER_DETAILS(fileType);
	}

	private void testCUSTOMERS(GeneratorSettingFileType fileType) {
		File file = new File(testProjectDir, "CUSTOMERS." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorSetting setting = factory.fromFile(file);
		assertEquals("CUSTOMERS", setting.getName());
		assertEquals(5, setting.getColumns().size());
		assertEquals(1, setting.getQuerys().size());
		assertEquals("iterator(100)", setting.getDataSourceExpression());
		assertEquals("[\"_index\":value]", setting.getColumnMappingExpression());
		String strartValueSql = """
				SELECT
					COALESCE( MAX( CUSTOMER_ID ), 0 ) AS CUSTOMER_ID
				FROM CUSTOMERS""";
		assertEquals(strartValueSql, setting.getStartValueSql());
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
		assertEquals(insertSql, setting.getInsertSql());
		assertEquals(1, setting.getQuerys().size());
		//
		ColumnGeneratorSetting colSetting = setting.getColumns().get("CUSTOMER_ID");
		assertEquals("CUSTOMER_ID", colSetting.getName());
		assertEquals("_previous.CUSTOMER_ID + 1", colSetting.getNextValue());
		assertNull(colSetting.getGenerationGroup());
		assertEquals("1", colSetting.getMinValue());
		assertEquals("9223372036854775807", colSetting.getMaxValue());
		//
		colSetting = setting.getColumns().get("CUSTOMER_CODE");
		assertEquals("CUSTOMER_CODE", colSetting.getName());
		assertEquals("nextAlphaNumeric( 20 )", colSetting.getNextValue());
		assertEquals("nextAlphaNumeric( 20 )", colSetting.getMinValue());
		assertNull(colSetting.getMaxValue());
		assertNull(colSetting.getGenerationGroup());
		//
		colSetting = setting.getColumns().get("CUSTOMER_NAME");
		assertEquals("CUSTOMER_NAME", colSetting.getName());
		assertEquals("nextAlphaNumeric( 200 )", colSetting.getNextValue());
		assertEquals("nextAlphaNumeric( 200 )", colSetting.getMinValue());
		assertNull(colSetting.getMaxValue());
		assertNull(colSetting.getGenerationGroup());
		//
		colSetting = setting.getColumns().get("CLOSING_DAY");
		assertEquals("CLOSING_DAY", colSetting.getName());
		assertEquals("_previous.CLOSING_DAY + 1", colSetting.getNextValue());
		assertEquals("1", colSetting.getMinValue());
		assertEquals("2147483647", colSetting.getMaxValue());
		assertNull(colSetting.getGenerationGroup());
		//
		colSetting = setting.getColumns().get("CREATED_AT");
		assertEquals("CREATED_AT", colSetting.getName());
		assertEquals("addMilliSeconds(_previous.CREATED_AT,1)", colSetting.getNextValue());
		assertTrue(colSetting.getMinValue().startsWith("LocalDateTime.of("));
		assertEquals("addMonths(_min.CREATED_AT,1)", colSetting.getMaxValue());
		assertNull(colSetting.getGenerationGroup());
	}

	private void testORDER_DETAILS(GeneratorSettingFileType fileType) {
		File file = new File(testProjectDir, "ORDER_DETAILS." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorSetting setting = factory.fromFile(file);
		assertEquals("ORDER_DETAILS", setting.getName());
		assertEquals(7, setting.getColumns().size());
		assertEquals(3, setting.getQuerys().size());
		assertEquals("iterator(100)", setting.getDataSourceExpression());
		assertEquals("[\"_index\":value]", setting.getColumnMappingExpression());
		assertEquals(startValueSqlBalance, setting.getStartValueSql());
		assertEquals(insertSql, setting.getInsertSql());
		assertEquals(3, setting.getQuerys().size());
		//
		ColumnGeneratorSetting colSetting = setting.getColumns().get("ORDER_DETAIL_ID");
		assertEquals("ORDER_DETAIL_ID", colSetting.getName());
		assertEquals("_previous.ORDER_DETAIL_ID + 1", colSetting.getNextValue());
		assertNull(colSetting.getGenerationGroup());
		assertEquals("1", colSetting.getMinValue());
		assertEquals("9223372036854775807", colSetting.getMaxValue());
		//
		colSetting = setting.getColumns().get("ORDER_ID");
		assertEquals("ORDER_ID", colSetting.getName());
		assertEquals("_previous.ORDER_ID", colSetting.getNextValue());
		assertEquals("FK_ORDER_DETAILS_ORDER", colSetting.getGenerationGroup());
		assertNull(colSetting.getMinValue());
		assertNull(colSetting.getMaxValue());
		//
		colSetting = setting.getColumns().get("ORDER_ID");
		assertEquals("ORDER_ID", colSetting.getName());
		assertEquals("_previous.ORDER_ID", colSetting.getNextValue());
		assertEquals("FK_ORDER_DETAILS_ORDER", colSetting.getGenerationGroup());
		assertNull(colSetting.getMinValue());
		assertNull(colSetting.getMaxValue());
		//
		colSetting = setting.getColumns().get("LINE_NO");
		assertEquals("LINE_NO", colSetting.getName());
		assertEquals("_previous.LINE_NO + 1", colSetting.getNextValue());
		assertEquals("1", colSetting.getMinValue());
		assertEquals("2147483647", colSetting.getMaxValue());
		//
		colSetting = setting.getColumns().get("PRODUCT_ID");
		assertEquals("PRODUCT_ID", colSetting.getName());
		assertEquals("_previous.PRODUCT_ID", colSetting.getNextValue());
		assertEquals("FK_ORDER_DETAILS_PRODUCT", colSetting.getGenerationGroup());
		assertNull(colSetting.getMinValue());
		assertNull(colSetting.getMaxValue());
		QueryGeneratorSetting queryGeneratorSetting = setting.getQuerys().get(colSetting.getGenerationGroup());
		String productSql = """
				SELECT
					PRODUCT_ID
				FROM PUBLIC.PRODUCTS""";
		assertEquals(productSql, queryGeneratorSetting.getSelectSql());
		//
		colSetting = setting.getColumns().get("QUANTITY");
		assertEquals("QUANTITY", colSetting.getName());
		assertEquals("_previous.QUANTITY + 1", colSetting.getNextValue());
		assertEquals("1", colSetting.getMinValue());
		assertEquals("10000000000000", colSetting.getMaxValue());
		assertNull(colSetting.getGenerationGroup());
		//
		colSetting = setting.getColumns().get("UNIT_PRICE");
		assertEquals("UNIT_PRICE", colSetting.getName());
		assertEquals("_previous.UNIT_PRICE + 1", colSetting.getNextValue());
		assertEquals("1", colSetting.getMinValue());
		assertEquals("10000000000000", colSetting.getMaxValue());
		assertNull(colSetting.getGenerationGroup());
	}

	private void testORDERS(GeneratorSettingFileType fileType) {
		File file = new File(testProjectDir, "ORDERS." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorSetting setting = factory.fromFile(file);
		assertEquals("ORDERS", setting.getName());
		assertEquals(5, setting.getColumns().size());
		assertEquals(2, setting.getQuerys().size());
		assertEquals("iterator(100)", setting.getDataSourceExpression());
		assertEquals("[\"_index\":value]", setting.getColumnMappingExpression());
		String strartValueSql = """
				SELECT
					COALESCE( MAX( ORDER_ID ), 0 ) AS ORDER_ID
				FROM ORDERS""";
		assertEquals(strartValueSql, setting.getStartValueSql());
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
		assertEquals(insertSql, setting.getInsertSql());
		assertEquals(2, setting.getQuerys().size());
		//
		ColumnGeneratorSetting colSetting = setting.getColumns().get("ORDER_ID");
		assertEquals("ORDER_ID", colSetting.getName());
		assertEquals("_previous.ORDER_ID + 1", colSetting.getNextValue());
		assertNull(colSetting.getGenerationGroup());
		assertEquals("1", colSetting.getMinValue());
		assertEquals("9223372036854775807", colSetting.getMaxValue());
		//
		colSetting = setting.getColumns().get("CUSTOMER_ID");
		assertEquals("CUSTOMER_ID", colSetting.getName());
		assertEquals("_previous.CUSTOMER_ID", colSetting.getNextValue());
		assertEquals("FK_ORDERS_CUSTOMER", colSetting.getGenerationGroup());
		assertNull(colSetting.getMinValue());
		assertNull(colSetting.getMaxValue());
		QueryGeneratorSetting queryGeneratorSetting = setting.getQuerys().get(colSetting.getGenerationGroup());
		String productSqlBalance = """
				SELECT
					CUSTOMER_ID
				FROM PUBLIC.CUSTOMERS""";
		assertEquals(productSqlBalance, queryGeneratorSetting.getSelectSql());
		//
		colSetting = setting.getColumns().get("ORDER_DATE");
		assertEquals("ORDER_DATE", colSetting.getName());
		assertEquals("addDays(_previous.ORDER_DATE,1)", colSetting.getNextValue());
		assertTrue(colSetting.getMinValue().startsWith("LocalDate.of("));
		assertEquals("addMonths(_min.ORDER_DATE,1)", colSetting.getMaxValue());
		assertNull(colSetting.getGenerationGroup());
		//
		colSetting = setting.getColumns().get("ORDER_STATUS");
		assertEquals("ORDER_STATUS", colSetting.getName());
		assertEquals("nextAlphaNumeric( 20 )", colSetting.getNextValue());
		assertEquals("nextAlphaNumeric( 20 )", colSetting.getMinValue());
		assertNull(colSetting.getMaxValue());
		assertNull(colSetting.getGenerationGroup());
	}

	private void test(Consumer<GenerateGeneratorSettingCommand> cons) {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateGeneratorSettingCommand command = new GenerateGeneratorSettingCommand();
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
