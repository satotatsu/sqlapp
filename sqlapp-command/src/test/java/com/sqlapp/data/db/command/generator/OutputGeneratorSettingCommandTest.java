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
import java.time.LocalDate;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.generator.factory.TableGeneratorSettingFactory;
import com.sqlapp.data.db.command.generator.setting.ColumnGeneratorSetting;
import com.sqlapp.data.db.command.generator.setting.TableGeneratorSetting;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.CommonUtils;

public class OutputGeneratorSettingCommandTest extends AbstractGeneratorCommandTest {

	private TableGeneratorSettingFactory factory = new TableGeneratorSettingFactory();

	@Test
	public void testExcel() throws ParseException, IOException, SQLException {
		testFile(GeneratorSettingFileType.EXCEL2007);
	}

	private void testFile(GeneratorSettingFileType fileType) {
		test(cmd -> {
			cmd.setFileType(fileType);
		});
		File file = new File(testProjectDir, "TAB1." + fileType.getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
		TableGeneratorSetting setting = factory.fromFile(file);
		assertEquals("TAB1", setting.getName());
		assertEquals("TAB1", setting.getName());
		assertEquals(20, setting.getColumns().size());
		assertEquals(1, setting.getQuerys().size());
		//
		ColumnGeneratorSetting colSetting = setting.getColumns().get("DATE_COL");
		assertEquals("DATE_COL", colSetting.getName());
		assertEquals(DataType.DATE, colSetting.getDataType());
		LocalDate date = LocalDate.now();
		assertEquals("LocalDate.of(" + date.getYear() + "," + date.getMonthValue() + ",1)", colSetting.getMinValue());
		assertEquals("addMonths(_min.DATE_COL,1)", colSetting.getMaxValue());
		assertEquals("addDays(_previous.DATE_COL,1)", colSetting.getNextValue());
		//
		colSetting = setting.getColumns().get("INTEGER_VALUES_COL");
		assertEquals("INTEGER_VALUES_COL", colSetting.getName());
		assertEquals(DataType.INT, colSetting.getDataType());
		assertEquals("1", colSetting.getMinValue());
		assertEquals("2147483647", colSetting.getMaxValue());
		assertTrue(CommonUtils.isEmpty(colSetting.getValues()));
	}

	@Test
	public void testJson() throws ParseException, IOException, SQLException {
		testFile(GeneratorSettingFileType.JSON);
	}

	@Test
	public void testYaml() throws ParseException, IOException, SQLException {
		testFile(GeneratorSettingFileType.YAML);
	}

	private void test(Consumer<OutputGeneratorSettingCommand> cons) {
		DataSource ds = newInternalDataSource();
		OutputGeneratorSettingCommand command = new OutputGeneratorSettingCommand();
		command.setDataSource(ds);
		// command.setOutputDirectory(new File("./"));
		command.setTableName("TAB1");
		command.setOutputDirectory(testProjectDir);
		dropTables(command, "TAB1");
		String sql = this.getResource("create_table1.sql");
		this.executeSql(command, sql);
		cons.accept(command);
		command.run();
		dropTables(command, "TAB1");
		File file = new File(testProjectDir, "TAB1." + command.getFileType().getWorkbookFileType().getFileExtension());
		assertTrue(file.exists());
	}
}
