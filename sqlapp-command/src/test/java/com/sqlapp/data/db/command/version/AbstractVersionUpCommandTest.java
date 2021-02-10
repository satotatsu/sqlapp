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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.function.BiConsumer;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.DataSourceConnectionUtils;
import com.sqlapp.test.AbstractDbCommandTest;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

public abstract class AbstractVersionUpCommandTest extends AbstractDbCommandTest {
	/**
	 * JDBC URL
	 */
	protected String url="jdbc:hsqldb:.";
	protected String path1="src/test/resources/test/up";
	protected String path2="src/test/resources/test/down";

	protected Long BASEDATE=20160603124532123l;
	
	@AfterEach
	public void after(){
		removeFiles();
	}
	
	@Test
	public void testRun() throws ParseException, IOException, SQLException {
		final DbVersionFileHandler handler=new DbVersionFileHandler();
		testVersionUp(handler, (tm, ds)->{
			final VersionDownCommand versionDownCommand=new VersionDownCommand();
			initialize(versionDownCommand, ds);
			versionDownCommand.setLastChangeToApply(tm.get(tm.size()-2));
			versionDownCommand.run();
			final String result=versionDownCommand.getLastState();
			final String expected=this.getResource("versionAfter.txt");
			assertEquals(expected, result);
		});
	}
	
	protected void replaceAppliedAt(final Table table, final Date date, final DbVersionHandler handler){
		if (table.getRows()==null) {
			return;
		}
		for(final Row row:table.getRows()){
			final Date val=(Date)row.get(handler.getAppliedAtColumnName());
			if (val!=null){
				row.put(handler.getAppliedAtColumnName(), date);
			}
		}
	}
	
	protected void replaceAppliedAt(final Table table, final Date date){
		replaceAppliedAt(table, date, new DbVersionHandler());
	}
	
	protected void testVersionUp(final DbVersionFileHandler handler, final BiConsumer<List<Long>, DataSource> cons) throws ParseException, IOException, SQLException {
		final VersionUpCommand command=createVersionUpCommand();
		removeFiles();
		initialize(command);
		this.initTable(command);
		final List<Long> times=initialize(handler);
		command.run();
		cons.accept(times, command.getDataSource());
		if (command.getDataSource() instanceof Closeable) {
			((Closeable)command.getDataSource()).close();
		}
	}

	protected void testVersionUp(final VersionUpCommand command, final DbVersionFileHandler handler, final BiConsumer<List<Long>, DataSource> cons) throws ParseException, IOException, SQLException {
		removeFiles();
		initialize(command);
		final List<Long> times=initialize(handler);
		command.run();
		cons.accept(times, command.getDataSource());
		if (command.getDataSource() instanceof Closeable) {
			((Closeable)command.getDataSource()).close();
		}
	}

	protected VersionUpCommand createVersionUpCommand() {
		return new VersionUpCommand();
	}
	
	private void removeFiles(){
		FileUtils.remove(path1);
		FileUtils.remove(path2);
		//FileUtils.remove("./hsqldb");
	}
	
	protected List<Long> testVersionUpNoRemove(final DbVersionFileHandler handler, final DataSource dataSource) throws ParseException, IOException, SQLException {
		final VersionUpCommand command=createVersionUpCommand();
		initialize(command, dataSource);
		final List<Long> times=initialize(handler);
		command.run();
		return times;
	}

	private void executeSql(final Statement stmt, final String sql) {
		try {
			stmt.execute(sql);
		} catch (final SQLException e) {
		}
	}
	
	protected void initialize(final VersionUpCommand command){
		command.setSqlDirectory(path1);
		command.setDownSqlDirectory(path2);
		initialize(command, newDataSource());
	}

	protected void initialize(final VersionUpCommand command, final DataSource dataSource){
		command.setSqlDirectory(path1);
		command.setDownSqlDirectory(path2);
		if (command.getDataSource()==null) {
			command.setDataSource(dataSource);
		}
	}

	protected void initTable(final AbstractDataSourceCommand command) {
		dropTables(command, "AAA", "BBB", "CCC", "DDD");
	}

	protected void dropTables(final DataSource dataSource, final String...tables) {
		Connection con=null;
		try(Connection conn=DataSourceConnectionUtils.get(dataSource)){
			con=conn;
			try(Statement stmt=conn.createStatement()){
				for(final String table:tables) {
					executeSql(stmt,"drop table "+table);
				}
			};
		} catch (final SQLException e) {
		} finally {
			try {
				DataSourceConnectionUtils.release(dataSource, con);
			} catch (final SQLException e) {
			}
		}
	}

	protected void dropTables(final AbstractDataSourceCommand command, final String...tables) {
		try(Connection conn=command.getConnectionHandler().getConnection()){
			try(Statement stmt=conn.createStatement()){
				for(final String table:tables) {
					executeSql(stmt,"drop table "+table);
				}
			};
		} catch (final SQLException e) {
		}
	}

	private List<Long> initialize(final DbVersionFileHandler handler) throws IOException{
		handler.setUpSqlDirectory(new File(path1));
		handler.setDownSqlDirectory(new File(path2));
		final List<Long> times=CommonUtils.list();
		final Long time2=BASEDATE;
		handler.addUpDownSql(time2.toString(), "create table2", "create table BBB (id int primary key, text varchar(10))", "drop table BBB");
		final Long time1=time2-1;
		handler.addUpDownSql(time1.toString(), "create table1", "create /*#schemaName*/table AAA (id int primary key, text varchar(10))", "drop table AAA");
		final Long time3=time2+1;
		handler.addUpDownSql(time3.toString(), "create table3", "create table CCC (id int primary key, text varchar(10))", "drop table CCC");
		times.add(time1);
		times.add(time2);
		times.add(time3);
		return times;
	}

	/**
	 * @return the url
	 */
	@Override
	public String getUrl() {
		return url;
	}

	/**
	 * @param url the url to set
	 */
	public void setUrl(final String url) {
		this.url = url;
	}

}
