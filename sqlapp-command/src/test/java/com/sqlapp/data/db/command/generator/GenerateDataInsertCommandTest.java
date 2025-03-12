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

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GenerateDataInsertCommandTest extends AbstractGeneratorCommandTest {

	@TempDir
	File testProjectDir;

	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		DataSource ds = newInternalDataSource();
		GenerateDataInsertCommand command = new GenerateDataInsertCommand();
		command.setDataSource(ds);
		command.setDmlBatchSize(500);
		command.setQueryCommitInterval(4);
		command.setSettingDirectory(new File("./src/test/resources/com/sqlapp/data/db/command/generator"));
		String sql = this.getResource("create_table1.sql");
		this.executeSql(command, sql);
		command.run();
		this.executeSql(command, "DROP TABLE TAB1");
	}
}
