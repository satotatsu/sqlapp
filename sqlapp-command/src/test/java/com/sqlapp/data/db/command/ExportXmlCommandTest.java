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

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.test.AbstractDbCommandTest;

public class ExportXmlCommandTest extends AbstractDbCommandTest {

	public ExportXmlCommandTest() {
	}

	private final String directoryPath = "./bin/export";

	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		final String suffix = "_dev";
		final ExportXmlCommand command = new ExportXmlCommand();
		final DataSource dataSource = newDataSource();
		command.setIncludeSchemas("master" + suffix);
		command.setDataSource(dataSource);
		command.setOnlyCurrentSchema(false);
		command.setOutputPath(new File(directoryPath));
		command.setOnlyCurrentSchema(false);
		command.setTarget("schemas");
		// command.run();
	}
}
