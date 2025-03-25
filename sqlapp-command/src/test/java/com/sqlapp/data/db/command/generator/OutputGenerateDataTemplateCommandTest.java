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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OutputGenerateDataTemplateCommandTest extends AbstractGeneratorCommandTest {

	@TempDir
	File testProjectDir;
	// File testProjectDir = new File("./");

	@Test
	public void testExcel() throws ParseException, IOException, SQLException {
		GeneratorSettingFileType setting = GeneratorSettingFileType.EXCEL2007;
		test(cmd -> {
			cmd.setFileType(setting);
		});
		File file = new File(testProjectDir, "TAB1." + setting.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
	}

	@Test
	public void testJson() throws ParseException, IOException, SQLException {
		GeneratorSettingFileType setting = GeneratorSettingFileType.JSON;
		test(cmd -> {
			cmd.setFileType(setting);
		});
		File file = new File(testProjectDir, "TAB1." + setting.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
	}

	@Test
	public void testYaml() throws ParseException, IOException, SQLException {
		GeneratorSettingFileType setting = GeneratorSettingFileType.YAML;
		test(cmd -> {
			cmd.setFileType(GeneratorSettingFileType.YAML);
		});
		File file = new File(testProjectDir, "TAB1." + setting.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
	}

	private void test(Consumer<OutputGenerateDataTemplateCommand> cons) {
		DataSource ds = newInternalDataSource();
		OutputGenerateDataTemplateCommand command = new OutputGenerateDataTemplateCommand();
		command.setDataSource(ds);
		// command.setOutputDirectory(new File("./"));
		command.setTableName("TAB1");
		command.setOutputDirectory(testProjectDir);
		String sql = this.getResource("create_table1.sql");
		this.executeSql(command, sql);
		cons.accept(command);
		command.run();
		this.executeSql(command, "DROP TABLE TAB1");
		File file = new File(testProjectDir, "TAB1.xlsx");
		assertTrue(file.exists());
	}
}
