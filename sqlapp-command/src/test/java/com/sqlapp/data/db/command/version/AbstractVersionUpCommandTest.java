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
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.JdbcUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.sqlapp.test.AbstractTest;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

public abstract class AbstractVersionUpCommandTest extends AbstractTest {
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
		DbVersionFileHandler handler=new DbVersionFileHandler();
		List<Long> times=testVersionUp(handler);
		VersionDownCommand versionDownCommand=new VersionDownCommand();
		initialize(versionDownCommand);
		versionDownCommand.setLastChangeToApply(times.get(times.size()-2));
		versionDownCommand.run();
		String result=versionDownCommand.getLastState();
		String expected=this.getResource("versionAfter.txt");
		assertEquals(expected, result);
	}
	
	protected void replaceAppliedAt(Table table, Date date, DbVersionHandler handler){
		for(Row row:table.getRows()){
			Date val=(Date)row.get(handler.getAppliedAtColumnName());
			if (val!=null){
				row.put(handler.getAppliedAtColumnName(), date);
			}
		}
	}
	
	protected void replaceAppliedAt(Table table, Date date){
		replaceAppliedAt(table, date, new DbVersionHandler());
	}
	
	protected List<Long> testVersionUp(DbVersionFileHandler handler) throws ParseException, IOException, SQLException {
		VersionUpCommand versionUpCommand=createVersionUpCommand();
		removeFiles();
		initialize(versionUpCommand);
		List<Long> times=initialize(handler);
		versionUpCommand.run();
		return times;
	}
	
	protected VersionUpCommand createVersionUpCommand() {
		return new VersionUpCommand();
	}
	
	private void removeFiles(){
		FileUtils.remove(path1);
		FileUtils.remove(path2);
	}
	
	protected List<Long> testVersionUpNoRemove(DbVersionFileHandler handler) throws ParseException, IOException, SQLException {
		VersionUpCommand versionUpCommand=createVersionUpCommand();
		initialize(versionUpCommand);
		List<Long> times=initialize(handler);
		versionUpCommand.run();
		return times;
	}
	
	protected void initialize(VersionUpCommand command){
		command.setSqlDirectory(path1);
		command.setDownSqlDirectory(path2);
		DataSource dataSource=newDataSource();
		command.setDataSource(dataSource);
	}

	private List<Long> initialize(DbVersionFileHandler handler) throws IOException{
		handler.setUpSqlDirectory(new File(path1));
		handler.setDownSqlDirectory(new File(path2));
		List<Long> times=CommonUtils.list();
		Long time2=BASEDATE;
		handler.addUpDownSql(time2.toString(), "create table2", "create table BBB (id int primary key, text varchar(10))", "drop table BBB");
		Long time1=time2-1;
		handler.addUpDownSql(time1.toString(), "create table1", "create /*#schemaName*/table AAA (id int primary key, text varchar(10))", "drop table AAA");
		Long time3=time2+1;
		handler.addUpDownSql(time3.toString(), "create table3", "create table CCC (id int primary key, text varchar(10))", "drop table CCC");
		times.add(time1);
		times.add(time2);
		times.add(time3);
		return times;
	}

	protected PoolConfiguration getPoolConfiguration() {
		PoolConfiguration poolConfiguration = new PoolProperties();
		poolConfiguration.setUrl(this.getUrl());
		poolConfiguration.setDriverClassName(JdbcUtils.getDriverClassNameByUrl(this.getUrl()));
		//poolConfiguration.setUsername(this.getUsername());
		//poolConfiguration.setPassword(this.getPassword());
		return poolConfiguration;
	}

	protected DataSource newDataSource() {
		DataSource ds = new SqlappDataSource(
					new org.apache.tomcat.jdbc.pool.DataSource(
							getPoolConfiguration()));
		return ds;
	}

	/**
	 * @return the url
	 */
	protected String getUrl() {
		return url;
	}

	/**
	 * @param url the url to set
	 */
	protected void setUrl(String url) {
		this.url = url;
	}

}
