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

import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

public class GenerateDataInsertCommandTest extends AbstractGeneratorCommandTest {

	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		HikariDataSource ds = newInternalDataSource();
		try {
			GenerateDataInsertCommand command = new GenerateDataInsertCommand();
			command.setDataSource(ds);
			command.setDmlBatchSize(1000);
			command.setQueryCommitInterval(4);
			command.setDirectory(new File("./src/test/resources/com/sqlapp/data/db/command/generator"));
			command.setCloseDataSource(false);
			this.dropTables(command, "TAB1");
			String sql = this.getResource("create_table1.sql");
			this.executeSql(command, sql);
			// command.setConsoleOutputLevel(ConsoleOutputLevel.DEBUG);
			command.run();
			this.dropTables(command, "TAB1");
		} finally {
			ds.close();
		}
	}
}
