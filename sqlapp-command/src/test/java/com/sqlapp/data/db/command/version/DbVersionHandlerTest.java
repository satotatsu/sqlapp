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
import java.text.ParseException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.version.DbVersionFileHandler.SqlFile;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.test.AbstractTest;
import com.sqlapp.util.DateUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.OutputTextBuilder;

public class DbVersionHandlerTest extends AbstractTest {

	String PATH_UP="src/test/resources/test/up";
	String PATH_DOWN="src/test/resources/test/down";

	@Test
	public void testCreateVersionTableDefinitionString() {
		DbVersionHandler hander=new DbVersionHandler();
		Table table=hander.createVersionTableDefinition("aaaa.bbbb");
		assertEquals("aaaa", table.getSchemaName());
		assertEquals("bbbb", table.getName());
	}

	@Test
	public void testMergeSqlFiles() throws IOException, ParseException {
		DbVersionHandler hander=new DbVersionHandler();
		Table table=hander.createVersionTableDefinition("aaaa.bbbb");
		List<SqlFile> sqlFiles=getSqlFiles();
		hander.mergeSqlFiles(sqlFiles, table);
		OutputTextBuilder builder=new OutputTextBuilder();
		hander.append(table, builder);
		String expected=this.getResource("table1.txt");
		System.out.println(builder.toString());
		assertEquals(expected, builder.toString());
	}

	@Test
	public void testMergeSqlFiles2() throws IOException, ParseException {
		DbVersionHandler hander=new DbVersionHandler();
		Table table=hander.createVersionTableDefinition("aaaa.bbbb");
		Row row=table.newRow();
		row.put("change_number", Long.valueOf("20160608123634123"));
		row.put(hander.getAppliedAtColumnName(), DateUtils.parse("20160111123634.023", "yyyyMMddHHmmss.SSS"));
		row.put(hander.getAppliedByColumnName(), "Satoh");
		row.put(hander.getStatusColumnName(), Status.Completed.toString());
		table.getRows().add(row);
		List<SqlFile> sqlFiles=getSqlFiles();
		hander.mergeSqlFiles(sqlFiles, table);
		OutputTextBuilder builder=new OutputTextBuilder();
		hander.append(table, builder);
		System.out.println(builder.toString());
		String expected=this.getResource("table2.txt");
		assertEquals(expected, builder.toString());
	}
	
	@Test
	public void testMergeSqlFiles3() throws IOException, ParseException {
		DbVersionHandler hander=new DbVersionHandler();
		Table table=hander.createVersionTableDefinition("aaaa.bbbb");
		Row row=table.newRow();
		row.put("change_number", Long.valueOf("20160608123634123"));
		row.put(hander.getAppliedAtColumnName(), DateUtils.parse("20160111123634.023", "yyyyMMddHHmmss.SSS"));
		row.put(hander.getAppliedByColumnName(), "Satoh");
		row.put(hander.getStatusColumnName(), Status.Completed.toString());
		table.getRows().add(row);
		row=table.newRow();
		row.put("change_number", Long.valueOf("20160608133634123"));
		row.put(hander.getAppliedAtColumnName(), DateUtils.parse("20160121123634.023", "yyyyMMddHHmmss.SSS"));
		row.put(hander.getAppliedByColumnName(), "Satoh");
		row.put(hander.getStatusColumnName(), Status.Completed.toString());
		table.getRows().add(row);
		List<SqlFile> sqlFiles=getSqlFiles2();
		hander.mergeSqlFiles(sqlFiles, table);
		List<Row> rows=hander.getRowsForVersionUp(table, Long.MAX_VALUE);
		OutputTextBuilder builder=new OutputTextBuilder();
		hander.append(table, builder);
		System.out.println(builder.toString());
		String expected=this.getResource("table3.txt");
		assertEquals(expected, builder.toString());
	}
	
	
	public List<SqlFile> getSqlFiles() throws IOException, ParseException {
		DbVersionFileHandler handler=new DbVersionFileHandler();
		FileUtils.remove(PATH_UP);
		FileUtils.remove(PATH_DOWN);
		handler.setUpSqlDirectory(new File(PATH_UP));
		handler.setDownSqlDirectory(new File(PATH_DOWN));
		handler.add(DateUtils.parse("20160708123634.123", "yyyyMMddHHmmss.SSS"), "create_table");
		List<SqlFile> list=handler.read();
		return list;
	}

	public List<SqlFile> getSqlFiles2() throws IOException, ParseException {
		DbVersionFileHandler handler=new DbVersionFileHandler();
		FileUtils.remove(PATH_UP);
		FileUtils.remove(PATH_DOWN);
		handler.setUpSqlDirectory(new File(PATH_UP));
		handler.setDownSqlDirectory(new File(PATH_DOWN));
		handler.addUpDownSql(DateUtils.parse("20160708123634.123", "yyyyMMddHHmmss.SSS"), "create_table", "create table table1 (id int primary key, text vachar2)", "drop table table1");
		handler.addUpDownSql(DateUtils.parse("20160818123634.123", "yyyyMMddHHmmss.SSS"), "alter table", "alter table table1 add text2 vachar2", "alter table table1 drop text2");
		handler.add(DateUtils.parse("20160911123634.123", "yyyyMMddHHmmss.SSS"), "create index");
		List<SqlFile> list=handler.read();
		return list;
	}


	@AfterEach
	public void after(){
		FileUtils.remove(PATH_UP);
		FileUtils.remove(PATH_DOWN);
	}
}
