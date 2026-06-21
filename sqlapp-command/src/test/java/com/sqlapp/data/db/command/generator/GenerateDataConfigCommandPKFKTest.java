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
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorConfigFactory;
import com.zaxxer.hikari.HikariDataSource;

public class GenerateDataConfigCommandPKFKTest extends AbstractGeneratorCommandTest {

	private TableGeneratorConfigFactory factory = new TableGeneratorConfigFactory();

	@Test
	public void testExcel() throws ParseException, IOException, SQLException {
		testFile(ConfigFileType.EXCEL);
	}

	private String resultSql = """
			SELECT
				a.CUSTOMER_ID
			FROM CUSTOMERS a
			WHERE NOT EXISTS
			(
				SELECT 1
				FROM ACCOUNTS_RECEIVABLE b
				WHERE
				b.CUSTOMER_ID = a.CUSTOMER_ID
			)""";

	private void testFile(ConfigFileType fileType) {
		test(cmd -> {
			cmd.setFileType(fileType);
		});
		File file = new File(testProjectDir,
				"ACCOUNTS_RECEIVABLE." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorConfig config = factory.fromFile(file);
		assertEquals("ACCOUNTS_RECEIVABLE", config.getName());
		assertEquals(3, config.getColumns().size());
		assertEquals(1, config.getQueries().size());
		assertEquals("iterator(1)", config.getDataSourceExpression());
		assertEquals("[\"_index\":value]", config.getColumnMappingExpression());
		assertEquals(resultSql, config.getStartValueSql());
		//
		ColumnGeneratorConfig colConfig = config.getColumns().get("CUSTOMER_ID");
		assertEquals("CUSTOMER_ID", colConfig.getName());
		assertEquals("_previous.CUSTOMER_ID", colConfig.getNextValue());
		assertNull(colConfig.getLookupGroup());
		assertEquals(1, config.getQueries().size());
	}

	private void test(Consumer<GenerateDataConfigCommand> cons) {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateDataConfigCommand command = new GenerateDataConfigCommand();
			command.setDataSource(ds);
			// command.setOutputDirectory(new File("./"));
			command.setIncludeTables("CUSTOMERS", "ACCOUNTS_RECEIVABLE");
			command.setCloseDataSource(false);
			command.setOutputDirectory(testProjectDir);
			dropTables(command, "CUSTOMERS", "ACCOUNTS_RECEIVABLE");
			String sql = this.getResource("create_table_customers.sql");
			this.executeSql(command, sql);
			sql = this.getResource("create_table_accounts_receivable.sql");
			this.executeSql(command, sql);
			cons.accept(command);
			command.run();
			dropTables(command, "ACCOUNTS_RECEIVABLE", "CUSTOMERS");
			File file = new File(testProjectDir,
					"ACCOUNTS_RECEIVABLE." + command.getFileType().getWorkbookFileType().getFileExtension());
			assertTrue(file.exists());
		} finally {
			ds.close();
		}
	}
}
