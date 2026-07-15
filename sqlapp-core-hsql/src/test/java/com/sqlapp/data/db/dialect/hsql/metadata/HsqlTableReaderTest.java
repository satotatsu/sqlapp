/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.core.test.AbstractTest;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.JdbcUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.sqlapp.util.CommonUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class HsqlTableReaderTest extends AbstractTest {

	/**
	 * JDBC URL
	 */
	protected String url;

	private String username;
	private String password;

	@BeforeEach
	public void before() {
		url = getTestProp("jdbc.url");
		username = getTestProp("jdbc.username");
		password = getTestProp("jdbc.password");
	}

	@Test
	void test() throws SQLException, XMLStreamException {
		SqlappDataSource dataSource = newDataSource();
		try (Connection conn = dataSource.getConnection()) {
			Dialect dialect = DialectResolver.getInstance().getDialect(conn);
			String sql = this.getResource("create_table1.sql");
			executeStatement(conn, sql);
			TableReader reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setObjectName("TAB1");
			Table obj = CommonUtils.first(reader.getAllFull(conn));
			Column dateCol = obj.getColumns().get("AMOUNT");
			String colXml = dateCol.asXml();
			String xml = obj.asXml();
			String exptected = """
					<table xml:space="preserve" name="TAB1" readonly="false" tableType="Memory" catalogName="PUBLIC" schemaName="PUBLIC">
						<columns>
							<column name="ID" dataType="INT" notNull="true" identity="true" identityStartValue="1" identityMaxValue="2147483647" identityMinValue="-2147483648" identityStep="1"/>
							<column name="CODE" dataType="CHAR" length="5"/>
							<column name="TITLE" dataType="VARCHAR" length="40"/>
							<column name="DATETIME_COL" dataType="TIMESTAMP" length="4" notNull="true"/>
							<column name="AMOUNT" dataType="DECIMAL" length="15" scale="2" notNull="true"/>
						</columns>
						<constraints>
							<primaryKeyConstraint name="PK_TAB1">
								<columns>
									<column name="ID"/>
								</columns>
							</primaryKeyConstraint>
						</constraints>
					</table>
						""";
			assertEquals(exptected.trim(), xml.trim());
		}
	}

	protected void executeStatement(Connection conn, String sql) throws SQLException {
		try (Statement stmt = conn.createStatement()) {
			stmt.execute(sql);
		}
	}

	protected SqlappDataSource newDataSource() {
		final SqlappDataSource ds = new SqlappDataSource(newInternalDataSource());
		return ds;
	}

	private HikariDataSource newInternalDataSource() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(this.getUrl());
		config.setUsername(this.getUsername());
		config.setPassword(this.getPassword());
		config.setMaximumPoolSize(10);
		config.setDriverClassName(JdbcUtils.getDriverClassNameByUrl(this.getUrl()));
		final HikariDataSource ds = new HikariDataSource(config);
		return ds;
	}

	public String getUrl() {
		return url;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

}
