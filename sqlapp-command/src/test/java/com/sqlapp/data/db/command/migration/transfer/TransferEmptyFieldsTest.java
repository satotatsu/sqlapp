/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.*;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransferEmptyFieldsTest {
	@TempDir
	Path directory;

	private JDBCDataSource database() {
		var result = new JDBCDataSource();
		result.setUrl("jdbc:hsqldb:mem:empty_fields_" + UUID.randomUUID());
		result.setUser("SA");
		result.setPassword("");
		return result;
	}

	private void sql(JDBCDataSource database, String command) throws Exception {
		try (var connection = database.getConnection(); var statement = connection.createStatement()) {
			statement.execute(command);
		}
	}

	@ParameterizedTest
	@EnumSource(value = FileFormat.class, names = { "CSV", "TSV" })
	void defaultUnquotedNullAndQuotedEmptyRoundTripWithoutAToken(FileFormat format) throws Exception {
		var source = database();
		var target = database();
		try {
			sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(50), NOTE VARCHAR(50))");
			sql(source, "INSERT INTO ITEMS VALUES(1,NULL,''),(2,'',NULL),(3,'__SQLAPP_NULL__','normal')");
			var job = Files.writeString(directory.resolve("job.yaml"),
					"formatVersion: 1\nsource: from\ntarget: to\ntables: [ITEMS]\nschemaMode: CREATE_MISSING\ndataMode: FILE\nfiles: {directory: packages, format: "
							+ format + "}\n");
			var exported = new TransferFileExportService().export(job, Map.of("from", source));
			assertNull(exported.manifest().files().nullValue());
			assertEquals(EmptyFieldMode.NULL, exported.manifest().files().emptyFieldMode());
			String delimiter = format == FileFormat.CSV ? "," : "\t";
			String text = Files
					.readString(exported.file().getParent().resolve(exported.manifest().tables().getFirst().file()));
			assertTrue(text.contains("\"1\"" + delimiter + delimiter + "\"\"\n"), text);
			assertTrue(text.contains("\"2\"" + delimiter + "\"\"" + delimiter + "\n"), text);
			var service = new TransferFileImportService();
			var review = service.review(exported.file(), exported.fingerprint(), Map.of("to", target));
			assertEquals(TransferJobExecutionService.Phase.COMPLETE,
					service.execute(review, Map.of("to", target), directory).phase());
			try (var connection = target.getConnection();
					var statement = connection.createStatement();
					var rows = statement.executeQuery("SELECT NAME,NOTE FROM ITEMS ORDER BY ID")) {
				assertTrue(rows.next());
				assertNull(rows.getString(1));
				assertEquals("", rows.getString(2));
				assertTrue(rows.next());
				assertEquals("", rows.getString(1));
				assertNull(rows.getString(2));
				assertTrue(rows.next());
				assertEquals("__SQLAPP_NULL__", rows.getString(1));
			}
		} finally {
			try {
				sql(source, "SHUTDOWN");
			} finally {
				sql(target, "SHUTDOWN");
			}
		}
	}

	@ParameterizedTest
	@EnumSource(value = FileFormat.class, names = { "CSV", "TSV" })
	void emptyTextChoiceAndLegacyTokenOnlyModeAreExplicit(FileFormat format) throws Exception {
		var table = new com.sqlapp.data.schemas.Table("ITEMS");
		var id = new com.sqlapp.data.schemas.Column("ID").setDataType(com.sqlapp.data.db.datatype.DataType.INT)
				.setNotNull(true);
		table.getColumns().add(id);
		table.setPrimaryKey(id);
		table.getColumns().add(
				new com.sqlapp.data.schemas.Column("NAME").setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR));
		String d = format == FileFormat.CSV ? "," : "\t";
		var file = Files.writeString(directory.resolve("data.txt"),
				"ID" + d + "NAME\n1" + d + "\n2" + d + "\"\"\n3" + d + "__NULL__\n");
		var source = new TransferCsvSource(table, file, new FileTransfer("packages", format, "UTF-8", "__NULL__",
				TemporalPolicy.WARN, HeaderMode.VALIDATE, EmptyFieldMode.EMPTY_STRING), "test", 3);
		source.validate();
		var rows = source.iterator(null);
		try {
			assertEquals("", rows.next().get("NAME"));
			assertEquals("", rows.next().get("NAME"));
			assertNull(rows.next().get("NAME"));
		} finally {
			com.sqlapp.util.iterator.IteratorCloseUtils.close(null, rows);
		}
		assertThrows(IllegalArgumentException.class, () -> new TransferCsvSource(table, file,
				new FileTransfer("packages", format, "UTF-8", "__NULL__"), "test", 3).validate());
		assertThrows(IllegalArgumentException.class, () -> new FileTransfer("packages", format, "UTF-8", null,
				TemporalPolicy.WARN, null, EmptyFieldMode.EMPTY_STRING));
	}
}
