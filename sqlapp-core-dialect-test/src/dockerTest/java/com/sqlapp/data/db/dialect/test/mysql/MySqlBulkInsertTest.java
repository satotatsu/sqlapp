/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.mysql;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.sql.Connection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

/** Exercises Connector/J LOAD DATA LOCAL INFILE against MySQL 8.4. */
class MySqlBulkInsertTest {
	private static final MySQLContainer MYSQL = ReusableTestcontainers
			.configure(new MySQLContainer("mysql:8.4")
					.withCommand("--local-infile=1"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(MYSQL);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(MYSQL);
	}

	@Test
	void loadsRowsAndPreservesDelimitedValues() throws Exception {
		try (Connection connection = MYSQL.createConnection(
				"?allowLoadLocalInfile=true");
				var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS sqlapp_bulk_mysql");
			statement.execute("CREATE TABLE sqlapp_bulk_mysql ("
					+ "id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(200), "
					+ "nullable_value VARCHAR(20), empty_value VARCHAR(20), "
					+ "payload VARBINARY(20), amount DECIMAL(12,2))");
			final Table table = new Table("sqlapp_bulk_mysql");
			table.getColumns().add(new Column("id").setDataType(DataType.BIGINT)
					.setIdentity(true));
			table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("nullable_value").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("empty_value").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("payload").setDataType(DataType.VARBINARY));
			table.getColumns().add(new Column("amount").setDataType(DataType.DECIMAL)
					.setLength(12).setScale(2));
			table.getRows().add(row -> {
				row.put("name", "山田,\"太郎\"\nline\\path");
				row.put("nullable_value", null);
				row.put("empty_value", "");
				row.put("payload", new byte[] { 0, (byte) 0xff });
				row.put("amount", new BigDecimal("123.45"));
			});

			assertEquals(1, BulkInsertResolver.execute(connection, table,
					BulkOption.defaults()));
			try (var resultSet = statement.executeQuery("SELECT id, name, "
					+ "nullable_value, empty_value, payload, amount "
					+ "FROM sqlapp_bulk_mysql")) {
				resultSet.next();
				assertEquals(1L, resultSet.getLong("id"));
				assertEquals("山田,\"太郎\"\nline\\path", resultSet.getString("name"));
				assertNull(resultSet.getString("nullable_value"));
				assertEquals("", resultSet.getString("empty_value"));
				assertArrayEquals(new byte[] { 0, (byte) 0xff },
						resultSet.getBytes("payload"));
				assertEquals(new BigDecimal("123.45"), resultSet.getBigDecimal("amount"));
			}
		}
	}

	@Test
	void upsertsThroughLoadDataStaging() throws Exception {
		try (Connection connection = MYSQL.createConnection("?allowLoadLocalInfile=true");
				var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS sqlapp_upsert_mysql");
			statement.execute("CREATE TABLE sqlapp_upsert_mysql (id BIGINT AUTO_INCREMENT UNIQUE, "
					+ "code VARCHAR(20) PRIMARY KEY, name VARCHAR(200), payload VARBINARY(20), amount DECIMAL(12,2))");
			statement.execute("INSERT INTO sqlapp_upsert_mysql(code,name,amount) VALUES('A','old',1.00)");
			final Table table=upsertTable("sqlapp_upsert_mysql");
			table.getRows().add(r->{r.put("code","A");r.put("name","更新後\nline");r.put("payload",new byte[]{0,(byte)0xff});r.put("amount",new BigDecimal("12.34"));});
			table.getRows().add(r->{r.put("code","B");r.put("name",null);});
			table.getRows().add(r->{r.put("code","C");r.put("name","");r.put("payload",new byte[]{2});});
			BulkUpsertResolver.execute(connection,table,BulkUpsertOption.defaults());
			try(var rs=statement.executeQuery("SELECT id,code,name,payload,amount FROM sqlapp_upsert_mysql ORDER BY code")){
				rs.next();assertEquals(1L,rs.getLong("id"));assertEquals("更新後\nline",rs.getString("name"));
				assertArrayEquals(new byte[]{0,(byte)0xff},rs.getBytes("payload"));assertEquals(new BigDecimal("12.34"),rs.getBigDecimal("amount"));
				rs.next();assertEquals("B",rs.getString("code"));assertNull(rs.getString("name"));
				rs.next();assertEquals("C",rs.getString("code"));assertEquals("",rs.getString("name"));assertArrayEquals(new byte[]{2},rs.getBytes("payload"));
			}
		}
	}

	private static Table upsertTable(final String name){final Table t=new Table(name);
		final Column id=new Column("id").setDataType(DataType.BIGINT).setIdentity(true);
		final Column code=new Column("code").setDataType(DataType.VARCHAR).setLength(20);
		t.getColumns().add(id);t.getColumns().add(code);t.getColumns().add(new Column("name").setDataType(DataType.VARCHAR).setLength(200));
		t.getColumns().add(new Column("payload").setDataType(DataType.VARBINARY).setLength(20));
		t.getColumns().add(new Column("amount").setDataType(DataType.DECIMAL).setLength(12).setScale(2));
		t.setPrimaryKey("pk_"+name,code);return t;}
}
