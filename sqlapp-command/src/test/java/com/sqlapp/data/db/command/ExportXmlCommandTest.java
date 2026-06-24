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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.test.AbstractDbCommandTest;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaCollection;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.zaxxer.hikari.HikariDataSource;

public class ExportXmlCommandTest extends AbstractDbCommandTest {

	public ExportXmlCommandTest() {
	}

	@TempDir
	protected File testProjectDir;

	@Test
	public void testRun() throws ParseException, IOException, SQLException {

		HikariDataSource ds = newInternalDataSource();
		try {
			final ExportSchemaXmlCommand command = new ExportSchemaXmlCommand();
			final DataSource dataSource = newDataSource();
			command.setIncludeSchemas("PUBLIC");
			command.setDataSource(dataSource);
			command.setOnlyCurrentSchema(false);
			command.setOutputDirectory(testProjectDir);
			command.setOnlyCurrentSchema(false);
			command.setTarget("schemas");
			command.setCloseDataSource(false);
			command.run();
			this.dropTables(ds, "TAB1");
			String sql = this.getResource("create_table1.sql");
			this.executeSql(ds, sql);
			// command.setConsoleOutputLevel(ConsoleOutputLevel.DEBUG);
			command.run();
			File file = new File(testProjectDir, "Schemas.xml");
			SchemaCollection schemas = SchemaUtils.readXml(file);
			assertEquals(1, schemas.size());
			Schema schema = schemas.get(0);
			assertEquals("PUBLIC", schema.getName());
			assertEquals(1, schema.getTables().size());
			Table table = schema.getTables().get(0);
			assertEquals("TAB1", table.getName());
			this.dropTables(ds, "TAB1");
		} finally {
			ds.close();
		}
	}
}
