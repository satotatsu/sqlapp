/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import io.github.spannm.jackcess.*;

class MdbSavedQuerySupportTest {
	@TempDir
	Path directory;

	@Test
	void missingAndAmbiguousColumnsAreRejectedBeforeCatalogWrites() throws Exception {
		final Path source = directory.resolve("references.accdb");
		try (var file = DatabaseBuilder.create(Database.FileFormat.V2010, source.toFile())) {
			new TableBuilder("P").addColumn(new ColumnBuilder("ID", DataType.LONG))
					.addColumn(new ColumnBuilder("NAME", DataType.TEXT)).toTable(file);
		}
		try (var writer = MdbJackcessSupport.open(source)) {
			for (final String sql : new String[] { "SELECT missing FROM P", "SELECT p.missing FROM P p",
					"SELECT ID FROM P p INNER JOIN P q ON p.ID=q.ID", "SELECT ID FROM P WHERE missing=1",
					"SELECT ID FROM P ORDER BY missing", "SELECT p.ID FROM P p INNER JOIN P q ON p.missing=q.ID",
					"SELECT ID FROM P WHERE ID + (missing)=0" }) {
				assertThrows(IllegalArgumentException.class, () -> writer.createSavedQuery("BAD", sql), sql);
			}
			writer.createSavedQuery("GOOD",
					"PARAMETERS [min value] LONG; SELECT IIf(ID > [min value], NAME, 'p.deleted') AS [display name] FROM P ORDER BY [display name]");
		}
		try (var file = new DatabaseBuilder().withPath(source).withReadOnly(true).open()) {
			assertEquals(1, file.getQueries().size());
			assertEquals("GOOD", file.getQueries().getFirst().getName());
		}
	}

	@ParameterizedTest
	@EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010" })
	void multipleNestedAndSelfJoinsRetainGroupingAfterRebuild(final Database.FileFormat format) throws Exception {
		final Path source = directory.resolve("multi" + format.getFileExtension());
		try (var file = DatabaseBuilder.create(format, source.toFile())) {
			final var parent = new TableBuilder("P").addColumn(new ColumnBuilder("ID", DataType.LONG))
					.addColumn(new ColumnBuilder("NAME", DataType.TEXT)).toTable(file);
			parent.addRow(1, "one");
			parent.addRow(2, "two");
			final var detail = new TableBuilder("D").addColumn(new ColumnBuilder("PID", DataType.LONG))
					.addColumn(new ColumnBuilder("AMOUNT", DataType.LONG)).toTable(file);
			detail.addRow(1, 12);
			detail.addRow(1, 8);
			new TableBuilder("X").addColumn(new ColumnBuilder("PID", DataType.LONG))
					.addColumn(new ColumnBuilder("FACTOR", DataType.LONG)).toTable(file).addRow(1, 2);
		}
		try (var writer = MdbJackcessSupport.open(source)) {
			writer.createSavedQuery("Chain",
					"SELECT p.NAME, Sum(d.AMOUNT*x.FACTOR) AS TOTAL FROM (P AS p) INNER JOIN (D AS d) ON p.ID=d.PID LEFT JOIN X AS x ON d.PID=x.PID GROUP BY p.NAME");
			writer.createSavedQuery("Grouped",
					"SELECT p.NAME, d.AMOUNT FROM P AS p LEFT JOIN (D AS d INNER JOIN X AS x ON d.PID=x.PID) ON p.ID=d.PID");
			writer.createSavedQuery("SelfJoined", "SELECT p.ID FROM P AS p INNER JOIN P AS q ON p.ID=q.ID");
			assertThrows(UnsupportedOperationException.class, () -> writer.createSavedQuery("Invalid",
					"SELECT * FROM P AS p INNER JOIN D AS d ON p.ID=d.PID INNER JOIN X AS p ON d.PID=p.PID"));
		}
		final Path target = directory.resolve("rebuilt" + format.getFileExtension());
		MdbJackcessSupport.rebuild(source, target, MdbFileLoader.load(source));
		try (var file = new DatabaseBuilder().withPath(target).withReadOnly(true).open()) {
			assertEquals(3, file.getQueries().size());
		}
		try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + target.toAbsolutePath());
				var statement = connection.createStatement()) {
			try (var rows = statement.executeQuery("SELECT TOTAL FROM Chain")) {
				assertTrue(rows.next());
				assertEquals(40, rows.getInt(1));
				assertFalse(rows.next());
			}
			try (var rows = statement.executeQuery("SELECT COUNT(*) FROM Grouped")) {
				assertTrue(rows.next());
				assertEquals(3, rows.getInt(1));
			}
			try (var rows = statement.executeQuery("SELECT COUNT(*) FROM SelfJoined")) {
				assertTrue(rows.next());
				assertEquals(2, rows.getInt(1));
			}
		}
	}

	@Test
	void joinsGroupingParametersAndQuotedLiteralsSurviveRebuildAndExecute() throws Exception {
		final Path source = directory.resolve("queries.accdb");
		try (var file = DatabaseBuilder.create(Database.FileFormat.V2010, source.toFile())) {
			new TableBuilder("P").addColumn(new ColumnBuilder("ID", DataType.LONG))
					.addColumn(new ColumnBuilder("NAME", DataType.TEXT)).toTable(file).addRow(1, "one");
			final var detail = new TableBuilder("D").addColumn(new ColumnBuilder("PID", DataType.LONG))
					.addColumn(new ColumnBuilder("AMOUNT", DataType.LONG)).toTable(file);
			detail.addRow(1, 12);
			detail.addRow(1, 8);
		}
		try (var writer = MdbJackcessSupport.open(source)) {
			writer.createSavedQuery("Joined",
					"SELECT p.NAME, Sum(d.AMOUNT) AS TOTAL FROM P AS p INNER JOIN D AS d ON p.ID=d.PID GROUP BY p.NAME HAVING Sum(d.AMOUNT)>10 ORDER BY p.NAME");
			writer.createSavedQuery("Param",
					"PARAMETERS\n[min amount] Long; SELECT AMOUNT FROM D WHERE AMOUNT >= [min amount]");
			writer.createSavedQuery("Literal", "SELECT 'a,b UNION c' AS TXT, PID FROM D WHERE AMOUNT=12");
			writer.createSavedQuery("LeftJoin", "SELECT p.NAME, d.AMOUNT FROM P AS p LEFT JOIN D AS d ON p.ID=d.PID");
			assertThrows(UnsupportedOperationException.class,
					() -> writer.createSavedQuery("bad", "UPDATE D SET AMOUNT=0"));
			assertThrows(UnsupportedOperationException.class,
					() -> writer.createSavedQuery("bad", "SELECT * FROM D; DELETE FROM D"));
			assertThrows(UnsupportedOperationException.class,
					() -> writer.createSavedQuery("bad", "SELECT 'broken FROM D"));
		}
		final var schema = MdbFileLoader.load(source);
		assertEquals(4, schema.getViews().size());
		assertNotNull(schema.getViews().get("Param").getSpecifics().get(MdbFileLoader.SAVED_QUERY_PARAMETERS));
		final Path target = directory.resolve("rebuilt.accdb");
		MdbJackcessSupport.rebuild(source, target, schema);
		try (var file = new DatabaseBuilder().withPath(target).withReadOnly(true).open()) {
			assertEquals(4, file.getQueries().size());
			final var parameter = file.getQueries().stream().filter(query -> query.getName().equals("Param"))
					.findFirst().orElseThrow();
			assertEquals(1, parameter.getParameters().size());
			assertTrue(parameter.toSQLString().contains("PARAMETERS"));
		}
		try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + target.toAbsolutePath());
				var statement = connection.createStatement()) {
			try (var rows = statement.executeQuery("SELECT TOTAL FROM Joined")) {
				assertTrue(rows.next());
				assertEquals(20, rows.getInt(1));
			}
			try (var rows = statement.executeQuery("SELECT TXT FROM Literal")) {
				assertTrue(rows.next());
				assertEquals("a,b UNION c", rows.getString(1));
			}
			try (var rows = statement.executeQuery("SELECT COUNT(*) FROM LeftJoin")) {
				assertTrue(rows.next());
				assertEquals(2, rows.getInt(1));
			}
			try (var call = connection.prepareCall("{call Param(?)}")) {
				call.setInt(1, 10);
				try (var rows = call.executeQuery()) {
					assertTrue(rows.next());
					assertEquals(12, rows.getInt(1));
					assertFalse(rows.next());
				}
			}
		}
	}
}
