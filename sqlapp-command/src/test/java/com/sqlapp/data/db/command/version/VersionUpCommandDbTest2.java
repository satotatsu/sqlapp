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

package com.sqlapp.data.db.command.version;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.test.AbstractDbCommandTest;
import com.sqlapp.jdbc.SqlappDataSource;

public class VersionUpCommandDbTest2 extends AbstractDbCommandTest {

	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		final VersionUpCommand command = new VersionUpCommand();
		final VersionDownCommand downDommand = new VersionDownCommand();
		try (final SqlappDataSource dataSource = newDataSource()) {
			command.setSqlDirectory(new File("src/test/resources/migration3/sqlup"));
			command.setDownSqlDirectory(new File("src/test/resources/migration3/sqldown"));
			command.setSchemaChangeLogTableName("changelog");
			// command.setSchemaChangeLogTableName("master"+suffix+".changelog");
			command.setDataSource(dataSource);
			command.setCloseDataSource(false);
			command.run();
			downDommand.setSqlDirectory(new File("src/test/resources/migration3/sqlup"));
			downDommand.setDownSqlDirectory(new File("src/test/resources/migration3/sqldown"));
			downDommand.setSchemaChangeLogTableName("changelog");
			// command.setSchemaChangeLogTableName("master"+suffix+".changelog");
			downDommand.setDataSource(dataSource);
			downDommand.run();
		}
	}
}
