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

/**
 * This file is part of sqlapp.
 *
 * sqlapp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command.export;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.generator.GenerateGeneratorSettingAndInsertCommand;
import com.sqlapp.data.db.command.test.AbstractDbCommandTest;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.util.CommonUtils;
import com.zaxxer.hikari.HikariDataSource;

public class ExportData2FileCommandTest extends AbstractDbCommandTest {

	private String username;
	private String password;

//	private File directoryPath = new File("./");
	@TempDir
	private File directoryPath;

	public ExportData2FileCommandTest() {
		url = getTestProp("jdbc.url");
		username = getTestProp("jdbc.username");
		password = getTestProp("jdbc.password");
	}

	@Test
	public void testRunExcel() throws ParseException, IOException, SQLException {
		testRun(WorkbookFileType.EXCEL);
	}

	@Test
	public void testRunCsv() throws ParseException, IOException, SQLException {
		testRun(WorkbookFileType.CSV);
	}

	@Test
	public void testRunJson() throws ParseException, IOException, SQLException {
		testRun(WorkbookFileType.JSON);
	}

	@Test
	public void testRunJsonl() throws ParseException, IOException, SQLException {
		testRun(WorkbookFileType.JSONL);
	}

	@Test
	public void testRunYaml() throws ParseException, IOException, SQLException {
		testRun(WorkbookFileType.YAML);
	}

	private void testRun(WorkbookFileType outputFileType) throws ParseException, IOException, SQLException {
		if (CommonUtils.isEmpty(this.getUrl())) {
			return;
		}
		HikariDataSource ds = newInternalDataSource();
		generetaInsert(ds, dataSource -> {
			final ExportData2FileCommand command = new ExportData2FileCommand();
			command.setOutputFileType(outputFileType);
			command.setDataSource(dataSource);
			command.setOutputDirectory(directoryPath);
			command.setUseSchemaNameDirectory(false);
			command.setIncludeSchemas("PUBLIC");
			command.setIncludeTables("TAB1");
			command.setOutputFileType(WorkbookFileType.JSON);
			command.setOnlyCurrentSchema(false);
			command.setCloseDataSource(false);
			command.run();
		});
	}

	private void generetaInsert(HikariDataSource ds, Consumer<DataSource> cons) throws SQLException {
		try {
			GenerateGeneratorSettingAndInsertCommand command = new GenerateGeneratorSettingAndInsertCommand();
			command.setDataSource(ds);
			command.setIncludeTables("TAB1");
			this.dropTables(ds, "TAB1");
			command.setOutputDirectory(directoryPath);
			String sql = this.getResource("create_table1.sql");
			this.executeSql(ds, sql);
			// command.setConsoleOutputLevel(ConsoleOutputLevel.DEBUG);
			command.run();
			cons.accept(ds);
			this.dropTables(ds, "TAB1");
		} finally {
			ds.close();
		}
	}

	/**
	 * @return the url
	 */
	@Override
	public String getUrl() {
		return url;
	}

	/**
	 * @return the username
	 */
	@Override
	public String getUsername() {
		return username;
	}

	/**
	 * @param username the username to set
	 */
	public void setUsername(final String username) {
		this.username = username;
	}

	/**
	 * @return the password
	 */
	@Override
	public String getPassword() {
		return password;
	}

	/**
	 * @param password the password to set
	 */
	public void setPassword(final String password) {
		this.password = password;
	}

	/**
	 * @param url the url to set
	 */
	public void setUrl(final String url) {
		this.url = url;
	}

}
