/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.bulk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

class SqliteBulkInsertTest {
	@Test
	void insertsRowsAndResolvesProvider() throws Exception {
		assertInstanceOf(SqliteBulkInsertExecutor.class,
				BulkInsertResolver.resolve(DialectHolder.defaultDialect));
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE SQLAPP_BULK_SQLITE (ID INTEGER PRIMARY KEY, "
					+ "TXT TEXT, NULLABLE_VALUE TEXT, EMPTY_VALUE TEXT, PAYLOAD BLOB)");
			final Table table = createTable();
			for (int i = 0; i < 3; i++) {
				final int index = i;
				table.getRows().add(row -> {
					row.put("TXT", "日本語-" + index + "\nline");
					row.put("NULLABLE_VALUE", null);
					row.put("EMPTY_VALUE", "");
					row.put("PAYLOAD", new byte[] { 0, (byte) (0xfd + index) });
				});
			}
			assertEquals(3, BulkInsertResolver.execute(connection, table,
					BulkOption.builder().batchSize(2).build()));
			final Table explicit = createTable();
			explicit.getRows().add(row -> {
				row.put("ID", 42);
				row.put("TXT", "explicit");
				row.put("EMPTY_VALUE", "");
				row.put("PAYLOAD", new byte[] { 1 });
			});
			assertEquals(1, BulkInsertResolver.execute(connection, explicit,
					BulkOption.builder().keepIdentity(true).build()));
			try (var resultSet = statement.executeQuery("SELECT * FROM SQLAPP_BULK_SQLITE "
					+ "WHERE ID=1")) {
				resultSet.next();
				assertEquals("日本語-0\nline", resultSet.getString("TXT"));
				assertNull(resultSet.getString("NULLABLE_VALUE"));
				assertEquals("", resultSet.getString("EMPTY_VALUE"));
				assertArrayEquals(new byte[] { 0, (byte) 0xfd }, resultSet.getBytes("PAYLOAD"));
			}
		}
	}

	@Test
	void upsertsAndSupportsSingleActionModes() throws Exception {
		assertInstanceOf(SqliteBulkUpsertExecutor.class,
				BulkUpsertResolver.resolve(DialectHolder.defaultDialect));
		try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement=connection.createStatement()){
			statement.execute("CREATE TABLE SQLAPP_UPSERT_SQLITE (ID INTEGER PRIMARY KEY AUTOINCREMENT, "
					+"CODE TEXT UNIQUE, TXT TEXT, PAYLOAD BLOB)");
			statement.execute("INSERT INTO SQLAPP_UPSERT_SQLITE(CODE,TXT) VALUES('A','old')");
			final Table both=upsertTable();
			both.getRows().add(r->{r.put("CODE","A");r.put("TXT","更新後\nline");r.put("PAYLOAD",new byte[]{0,(byte)0xff});});
			both.getRows().add(r->{r.put("CODE","B");r.put("TXT",null);});
			assertEquals(2,BulkUpsertResolver.execute(connection,both,BulkUpsertOption.builder()
					.bulkOption(BulkOption.builder().batchSize(1).build()).build()));

			final Table updateOnly=upsertTable();
			updateOnly.getRows().add(r->{r.put("CODE","A");r.put("TXT","update-only");});
			updateOnly.getRows().add(r->{r.put("CODE","D");r.put("TXT","must-not-insert");});
			assertEquals(1,BulkUpsertResolver.execute(connection,updateOnly,
					BulkUpsertOption.builder().insertWhenNotMatched(false).build()));

			final Table insertOnly=upsertTable();
			insertOnly.getRows().add(r->{r.put("CODE","A");r.put("TXT","must-not-update");});
			insertOnly.getRows().add(r->{r.put("CODE","C");r.put("TXT","");r.put("PAYLOAD",new byte[]{2});});
			assertEquals(1,BulkUpsertResolver.execute(connection,insertOnly,
					BulkUpsertOption.builder().updateWhenMatched(false).build()));

			try(var rs=statement.executeQuery("SELECT CODE,TXT,PAYLOAD FROM SQLAPP_UPSERT_SQLITE ORDER BY CODE")){
				rs.next();assertEquals("A",rs.getString("CODE"));assertEquals("update-only",rs.getString("TXT"));
				rs.next();assertEquals("B",rs.getString("CODE"));assertNull(rs.getString("TXT"));
				rs.next();assertEquals("C",rs.getString("CODE"));assertEquals("",rs.getString("TXT"));assertArrayEquals(new byte[]{2},rs.getBytes("PAYLOAD"));
			}
		}
	}

	private static Table createTable() {
		final Table table = new Table("SQLAPP_BULK_SQLITE");
		table.getColumns().add(new Column("ID").setDataType(DataType.BIGINT).setIdentity(true));
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("NULLABLE_VALUE").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("EMPTY_VALUE").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("PAYLOAD").setDataType(DataType.VARBINARY));
		return table;
	}

	private static Table upsertTable(){final Table t=new Table("SQLAPP_UPSERT_SQLITE");
		final Column id=new Column("ID").setDataType(DataType.BIGINT).setIdentity(true);
		final Column code=new Column("CODE").setDataType(DataType.VARCHAR);
		t.getColumns().add(id);t.getColumns().add(code);
		t.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR));
		t.getColumns().add(new Column("PAYLOAD").setDataType(DataType.VARBINARY));
		t.setPrimaryKey("UQ_SQLAPP_UPSERT_SQLITE",code);return t;}
}
