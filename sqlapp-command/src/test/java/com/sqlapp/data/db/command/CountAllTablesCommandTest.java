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

package com.sqlapp.data.db.command;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.test.AbstractDbCommandTest;
import com.zaxxer.hikari.HikariDataSource;

public class CountAllTablesCommandTest extends AbstractDbCommandTest {

	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		HikariDataSource ds = newInternalDataSource();
		try {
			final CountAllTablesCommand command = new CountAllTablesCommand();
			final DataSource dataSource = newDataSource();
			command.setIncludeSchemas("PUBLIC");
			command.setDataSource(dataSource);
			command.setOnlyCurrentSchema(false);
			this.dropTables(ds, "TAB1");
			String sql = this.getResource("create_table1.sql");
			this.executeSql(ds, sql);
			// command.setConsoleOutputLevel(ConsoleOutputLevel.DEBUG);
			command.run();
		} finally {
			ds.close();
		}
	}
}
