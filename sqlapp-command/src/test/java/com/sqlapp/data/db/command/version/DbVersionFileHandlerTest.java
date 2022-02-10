/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.command.version;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.version.DbVersionFileHandler.SqlFile;
import com.sqlapp.util.FileUtils;

public class DbVersionFileHandlerTest {

	@Test
	public void test1() throws IOException {
		DbVersionFileHandler handler=new DbVersionFileHandler();
		String path1="src/test/resources/test/up";
		String path2="src/test/resources/test/down";
		FileUtils.remove(path1);
		FileUtils.remove(path2);
		handler.setUpSqlDirectory(new File(path1));
		handler.setDownSqlDirectory(new File(path2));
		handler.add("create_table");
		List<SqlFile> list=handler.read();
		assertEquals(1, list.size());
		SqlFile sqlFile=list.get(0);
		assertTrue(sqlFile.getUpSqlFile()!=null);
		assertTrue(sqlFile.getDownSqlFile()!=null);
		sqlFile.getUpSqlFile().delete();
		sqlFile.getDownSqlFile().delete();
	}

	@Test
	public void test2() throws IOException {
		DbVersionFileHandler handler=new DbVersionFileHandler();
		String path1="src/test/resources/test/up";
		String path2="src/test/resources/test/up";
		FileUtils.remove(path1);
		FileUtils.remove(path2);
		handler.setUpSqlDirectory(new File(path1));
		handler.setDownSqlDirectory(new File(path2));
		handler.add("create_table");
		List<SqlFile> list=handler.read();
		assertEquals(1, list.size());
		SqlFile sqlFile=list.get(0);
		assertTrue(sqlFile.getUpSqlFile()!=null);
		assertTrue(sqlFile.getDownSqlFile()==null);
		sqlFile.getUpSqlFile().delete();
	}


}
