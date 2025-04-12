/*
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins

import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.util.function.Consumer

import javax.sql.DataSource

import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir

import com.sqlapp.gradle.plugins.extension.DataSourceExtension
import com.zaxxer.hikari.HikariDataSource;
abstract class AbstractTaskTest {

	@TempDir
	protected File testProjectDir;
	protected File settingsFile;
	protected File buildFile;

	@BeforeEach
	public void setup() {
		settingsFile = new File(testProjectDir, "settings.gradle");
		buildFile = new File(testProjectDir, "build.gradle");
	}

	protected Project createProject(File targetDir) {
		return createProject(targetDir,null);
	}

	protected Project createProject(File targetDir, Consumer<ProjectBuilder> cons) {
		ProjectBuilder projectBuilder=ProjectBuilder.builder();
		FileUtils.createParentDirectories(targetDir);
		projectBuilder.withProjectDir(targetDir);
		if (cons!=null) {
			cons.accept(projectBuilder);
		}
		Project project;
		try {
			project = projectBuilder.build();
		} catch(GradleException e) {
			//for JDK17 error
			project = projectBuilder.build();
		}
		return project;
	}

	protected void writeFile(File destination, String content) throws IOException {
		BufferedWriter output = null;
		try {
			output = new BufferedWriter(new FileWriter(destination));
			output.write(content);
		} finally {
			if (output != null) {
				output.close();
			}
		}
	}

	protected DataSource getDataSource(DataSourceExtension extension) {
		final DataSource ds = new HikariDataSource(extension.toConfig());
		return ds;
	}

	protected void executeSqlQuietly(DataSource ds, String... sqls) {
		Connection con=ds.getConnection();
		con.setAutoCommit(false);
		try{
			for(String sql:sqls) {
				executeSql(con,sql);
			}
		} finally {
			con.close();
		}
	}

	private void executeSql(Connection con, String sql) {
		Statement stmt=con.createStatement();
		try {
			stmt.executeUpdate(sql);
			con.commit();
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			stmt.close();
		}
	}

	protected void dropTables(final DataSource dataSource, final String... tables) {
		Connection con=dataSource.getConnection();
		for (final String table : tables) {
			try {
				executeSql(con, "drop table \"" + table + "\" IF EXISTS");
			} catch (final SQLException e) {
				con.close();
			}
		}
	}

	protected copyDirectory(File from, File to) {
		org.apache.commons.io.FileUtils.copyDirectory(from, to);
	}

	protected copyFile(File from, File to) {
		org.apache.commons.io.FileUtils.copyFile(from, to);
	}
}
