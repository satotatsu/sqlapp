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
			String xml = obj.asXml();
			assertEquals(this.getResource("create_table1.xml"), xml);
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
