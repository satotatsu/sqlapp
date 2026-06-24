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

package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.DbVersionFileHandler.SqlFile;

public class DbVersionFileHandlerTest {
	@TempDir
	protected File testProjectDir;

	@Test
	public void testAddAndRead() throws IOException {
		DbVersionFileHandler handler = new DbVersionFileHandler();
		File upDir = new File(testProjectDir, "up");
		File downDir = new File(testProjectDir, "down");
		handler.setUpSqlDirectory(upDir);
		handler.setDownSqlDirectory(downDir);
		handler.add("create_table");
		List<SqlFile> list = handler.read();
		assertEquals(1, list.size());
		SqlFile sqlFile = list.get(0);
		assertTrue(sqlFile.getUpSqlFile() != null);
		assertTrue(sqlFile.getDownSqlFile() != null);
		sqlFile.getUpSqlFile().delete();
		sqlFile.getDownSqlFile().delete();
	}

	@Test
	public void testAddAndReadSameDir() throws IOException {
		DbVersionFileHandler handler = new DbVersionFileHandler();
		File upDir = new File(testProjectDir, "up");
		File downDir = new File(testProjectDir, "up");
		handler.setUpSqlDirectory(upDir);
		handler.setDownSqlDirectory(downDir);
		handler.add("create_table");
		List<SqlFile> list = handler.read();
		assertEquals(1, list.size());
		SqlFile sqlFile = list.get(0);
		assertTrue(sqlFile.getUpSqlFile() != null);
		assertTrue(sqlFile.getDownSqlFile() == null);
		sqlFile.getUpSqlFile().delete();
	}

}
