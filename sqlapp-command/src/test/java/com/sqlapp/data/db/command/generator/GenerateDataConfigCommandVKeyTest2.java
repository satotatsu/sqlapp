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
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.dataconfig.ConfigFileType;
import com.sqlapp.data.db.command.generator.config.ColumnGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.QueryGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorConfigFactory;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.FileUtils;
import com.zaxxer.hikari.HikariDataSource;

public class GenerateDataConfigCommandVKeyTest2 extends AbstractGeneratorCommandTest {

	private TableGeneratorConfigFactory factory = new TableGeneratorConfigFactory();

	private void testFile(ConfigFileType fileType) {
		test(cmd -> {
			cmd.setFileType(fileType);
		});
		File file = new File(testProjectDir, "TAB1." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorConfig config = factory.fromFile(file);
		assertEquals("TAB1", config.getName());
		assertEquals(5, config.getColumns().size());
		assertEquals(1, config.getQueries().size());
		String startSql = """
				SELECT 1
				FROM (VALUES(0))
								""";
		assertEquals(startSql.trim(), config.getStartValueSql().trim());
		String insertSql = """
				INSERT INTO TAB1
				(
					  PK_TEXT1
					, PK_TEXT2
					, PK_TEXT3
					, PK_TEXT4
					, TEXT
				)
				VALUES
				(
					  /*PK_TEXT1*/''
					, /*PK_TEXT2*/''
					, /*PK_TEXT3*/''
					, /*PK_TEXT4*/''
					, /*TEXT*/''
				)
												""";
		assertEquals(insertSql.trim(), config.getInsertSql().trim());
		//
		ColumnGeneratorConfig colConfig = config.getColumns().get("PK_TEXT1");
		assertEquals("PK_TEXT1", colConfig.getName());
		assertEquals(DataType.VARCHAR, colConfig.getDataType());
		assertEquals("nextAlphaNumeric( 10 )", colConfig.getNextValue());
		// ==================================================
		file = new File(testProjectDir, "TAB2." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		config = factory.fromFile(file);
		assertEquals("TAB2", config.getName());
		assertEquals(6, config.getColumns().size());
		assertEquals(2, config.getQueries().size());
		String startValueSql = """
				SELECT 1
				FROM (VALUES(0))
													""";
		assertEquals(startValueSql.trim(), config.getStartValueSql().trim());
		insertSql = """
				INSERT INTO TAB2
				(
					  PK_TEXT1
					, PK_TEXT2
					, PK_TEXT3
					, PK_TEXT4
					, PK_TEXT5
					, TEXT
				)
				VALUES
				(
					  /*PK_TEXT1*/''
					, /*PK_TEXT2*/''
					, /*PK_TEXT3*/''
					, /*PK_TEXT4*/''
					, /*PK_TEXT5*/''
					, /*TEXT*/''
				)
													""";
		assertEquals(insertSql.trim(), config.getInsertSql().trim());
		//
		colConfig = config.getColumns().get("PK_TEXT1");
		assertEquals("PK_TEXT1", colConfig.getName());
		assertEquals(DataType.VARCHAR, colConfig.getDataType());
		assertEquals("_previous.PK_TEXT1", colConfig.getNextValue());
		//
		QueryGeneratorConfig queryGeneratorConfig = config.getQueries().get("fk_TAB2_virtual1");
		String selectSql = """
				SELECT
					PK_TEXT1
					, PK_TEXT2
					, PK_TEXT3
					, PK_TEXT4
				FROM PUBLIC.TAB1
												""";
		assertEquals(selectSql.trim(), queryGeneratorConfig.getSelectSql().trim());
	}

	@Test
	public void testYaml() throws ParseException, IOException, SQLException {
		testFile(ConfigFileType.YAML);
	}

	private void test(Consumer<GenerateDataConfigCommand> cons) {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateDataConfigCommand command = new GenerateDataConfigCommand();
			File vkeyDir = new File(downDirectory, "vkey");
			File vfkeyFile = new File(vkeyDir, "f.fkey");
			FileUtils.writeText(vfkeyFile, "UTF8", "TAB2->TAB1");
			command.setDataSource(ds);
			// command.setOutputDirectory(new File("./"));
			command.setIncludeTables("TAB1", "TAB2");
			command.setForeignKeyDefinitionDirectory(vkeyDir);
			command.setCloseDataSource(false);
			command.setOutputDirectory(testProjectDir);
			dropTables(command, "TAB1");
			String sql = """
					CREATE TABLE TAB1
					(
						PK_TEXT1 VARCHAR(10)
						, PK_TEXT2 VARCHAR(10)
						, PK_TEXT3 VARCHAR(10)
						, PK_TEXT4 VARCHAR(10)
						, TEXT VARCHAR(10)
						, PRIMARY KEY (PK_TEXT1, PK_TEXT2, PK_TEXT3, PK_TEXT4)
					)
					""";
			this.executeSql(command, sql);
			String sql2 = """
					CREATE TABLE TAB2
					(
						PK_TEXT1 VARCHAR(10)
						, PK_TEXT2 VARCHAR(10)
						, PK_TEXT3 VARCHAR(10)
						, PK_TEXT4 VARCHAR(10)
						, PK_TEXT5 VARCHAR(10)
						, TEXT VARCHAR(10)
						, PRIMARY KEY (PK_TEXT1, PK_TEXT2, PK_TEXT3, PK_TEXT4, PK_TEXT5)
					)
					""";
			this.executeSql(command, sql2);
			cons.accept(command);
			command.run();
			dropTables(command, "TAB1");
			dropTables(command, "TAB2");
			File file = new File(testProjectDir,
					"TAB1." + command.getFileType().getWorkbookFileType().getFileExtension());
			assertTrue(file.exists());
			file = new File(testProjectDir, "TAB2." + command.getFileType().getWorkbookFileType().getFileExtension());
		} finally {
			ds.close();
		}
	}
}
