/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** Rename, convert and exclude rules of {@link JdbcTreeDataCopySession#newCopy}. */
class JdbcTreeDataCopySessionColumnMappingTest {

	@Test
	void copiesRenamedConvertedAndRetypedValues() throws Exception {
		try (var connection = database()) {
			var tables = tables(connection);
			copy(connection, tables, JdbcTreeDataColumnMapping.builder().exclude("ID").rename("NAME", "CUSTOMER_NAME")
					.convert("STATUS", "value == 'A' ? 'ACTIVE' : 'INACTIVE'")
					.rename("NOTE", "NOTE_LENGTH").convert("NOTE", "value == null ? 0 : value.length()")
					.convert("NAME", "value.trim() + '/' + row['STATUS']").build());
			assertEquals(List.of("alice/A|ACTIVE|5", "bob/I|INACTIVE|0"), rows(connection));
		}
	}

	@Test
	void rejectsUnknownColumnsDuplicateTargetsAndInvalidExpressionsImmediately() throws Exception {
		try (var connection = database()) {
			var tables = tables(connection);
			for (var mapping : List.of(JdbcTreeDataColumnMapping.builder().rename("MISSING", "CUSTOMER_NAME").build(),
					JdbcTreeDataColumnMapping.builder().rename("NAME", "MISSING").build(),
					JdbcTreeDataColumnMapping.builder().convert("NOTE", "value").build(),
					JdbcTreeDataColumnMapping.builder().rename("NAME", "STATUS").build(),
					JdbcTreeDataColumnMapping.builder().convert("STATUS", "value.(").build())) {
				try (var copy = copySession(connection, tables)) {
					assertThrows(IllegalArgumentException.class,
							() -> copy.setColumnMapping(tables.get(0), tables.get(1), mapping));
				}
			}
			assertThrows(IllegalArgumentException.class,
					() -> JdbcTreeDataColumnMapping.builder().exclude("NAME").rename("NAME", "X").build());
			assertThrows(IllegalArgumentException.class,
					() -> JdbcTreeDataColumnMapping.builder().rename("NAME", "X").rename("NAME", "Y"));
		}
	}

	@Test
	void valuesThatTheTargetTypeCannotRepresentStopTheCopy() throws Exception {
		try (var connection = database()) {
			var tables = tables(connection);
			var mapping = JdbcTreeDataColumnMapping.builder().exclude("ID").rename("NAME", "CUSTOMER_NAME")
					.rename("NOTE", "NOTE_LENGTH").convert("NOTE", "'not a number'").build();
			var failure = assertThrows(IllegalStateException.class, () -> copy(connection, tables, mapping));
			assertTrue(failure.getMessage().contains("SRC.NOTE"), failure.getMessage());
			assertEquals(List.of(), rows(connection));
		}
	}

	private static void copy(Connection connection, List<Table> tables, JdbcTreeDataColumnMapping mapping)
			throws SQLException {
		var source = tables.get(0);
		var target = tables.get(1);
		try (var copy = copySession(connection, tables)) {
			copy.setColumnMapping(source, target, mapping);
			copy.execute(active -> {
				while (active.next(source)) {
					active.newCopy(active.getRow(source), target);
				}
			});
		} catch (SQLException | RuntimeException e) {
			connection.rollback();
			throw e;
		}
	}

	private static JdbcTreeDataCopySession copySession(Connection connection, List<Table> tables) {
		var reader = new JdbcTreeDataSession(connection, tables.get(0));
		reader.setTableOperationMode(TableOperationMode.NONE);
		reader.select(tables.get(0), "SELECT ID, NAME, STATUS, NOTE FROM SRC ORDER BY ID");
		var writer = new JdbcTreeDataSession(connection, tables.get(1));
		writer.setTableOperationMode(TableOperationMode.INSERT);
		return new JdbcTreeDataCopySession(reader, writer);
	}

	private static List<Table> tables(Connection connection) throws SQLException {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		var schema = new Schema("PUBLIC");
		var source = new Table("SRC");
		schema.getTables().add(source);
		source.setDialect(dialect);
		source.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		source.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(20));
		source.getColumns().add(new Column("STATUS").setDataType(DataType.VARCHAR).setLength(1));
		source.getColumns().add(new Column("NOTE").setDataType(DataType.VARCHAR).setLength(20));
		source.setPrimaryKey((String) null, source.getColumns().get("ID"));
		var target = new Table("TGT");
		schema.getTables().add(target);
		target.setDialect(dialect);
		target.getColumns().add(new Column("ID").setDataType(DataType.INT).setIdentity(true));
		target.getColumns().add(new Column("CUSTOMER_NAME").setDataType(DataType.VARCHAR).setLength(20));
		target.getColumns().add(new Column("STATUS").setDataType(DataType.VARCHAR).setLength(10));
		target.getColumns().add(new Column("NOTE_LENGTH").setDataType(DataType.INT));
		target.setPrimaryKey((String) null, target.getColumns().get("ID"));
		return List.of(source, target);
	}

	private static Connection database() throws SQLException {
		var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "sa", "");
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE SRC(ID INT PRIMARY KEY, NAME VARCHAR(20), STATUS VARCHAR(1), NOTE VARCHAR(20))");
			statement.execute("CREATE TABLE TGT(ID INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
					+ " CUSTOMER_NAME VARCHAR(20), STATUS VARCHAR(10), NOTE_LENGTH INT)");
			statement.execute("INSERT INTO SRC VALUES(1,' alice ','A','hello'),(2,'bob','I',NULL)");
		}
		connection.setAutoCommit(false);
		return connection;
	}

	private static List<String> rows(Connection connection) throws SQLException {
		var result = new ArrayList<String>();
		try (var statement = connection.createStatement(); var rs = statement
				.executeQuery("SELECT CUSTOMER_NAME, STATUS, NOTE_LENGTH FROM TGT ORDER BY CUSTOMER_NAME")) {
			while (rs.next())
				result.add(rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3));
		}
		return result;
	}
}
