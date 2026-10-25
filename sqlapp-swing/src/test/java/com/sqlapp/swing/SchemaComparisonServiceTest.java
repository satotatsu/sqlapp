/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.Schema;

class SchemaComparisonServiceTest {
	@TempDir
	Path directory;

	@Test
	void generatesSqlWithoutChangingInputFiles() throws Exception {
		final var before = schema();
		final var after = schema();
		final var table = new com.sqlapp.data.schemas.Table("CUSTOMER");
		final var column = new com.sqlapp.data.schemas.Column("ID");
		column.setDataType(com.sqlapp.data.db.datatype.DataType.INT);
		table.getColumns().add(column);
		after.getTables().add(table);
		final Path original = write("before.xml", before);
		final Path target = write("after.xml", after);
		final String saved = Files.readString(target);
		final String result = new SchemaComparisonService().compare(original, target);
		assertTrue(result.toUpperCase().contains("CREATE TABLE"), result);
		assertTrue(result.contains("CUSTOMER"), result);
		assertEquals(saved, Files.readString(target));
	}

	@Test
	void identicalSchemasHaveNoChanges() throws Exception {
		final Path file = write("same.xml", schema());
		assertTrue(new SchemaComparisonService().compare(file, file).contains("No Schema changes"));
	}

	@Test
	void rejectsMissingMalformedAndNonSchemaFiles() throws Exception {
		final var service = new SchemaComparisonService();
		final Path valid = write("valid.xml", schema());
		assertThrows(IllegalArgumentException.class, () -> service.compare(directory.resolve("missing"), valid));
		final Path malformed = directory.resolve("bad.xml");
		Files.writeString(malformed, "<schema>");
		assertThrows(Exception.class, () -> service.compare(malformed, valid));
		Files.writeString(malformed, "<!DOCTYPE schema [<!ENTITY x SYSTEM 'file:///missing'>]><schema name='&x;'/>");
		assertThrows(IllegalArgumentException.class, () -> service.compare(malformed, valid));
		final Path table = directory.resolve("table.xml");
		new com.sqlapp.data.schemas.Table("OTHER").writeXml(table.toFile());
		assertThrows(IllegalArgumentException.class, () -> service.compare(table, valid));
	}

	@Test
	void rejectsMissingProductAndCrossProductComparison() throws Exception {
		final var service = new SchemaComparisonService();
		final Path valid = write("valid.xml", schema());
		assertThrows(IllegalArgumentException.class,
				() -> service.compare(valid, write("unknown.xml", new Schema("PUBLIC"))));
		final var other = schema();
		other.setProductName("PostgreSQL");
		assertThrows(IllegalArgumentException.class, () -> service.compare(valid, write("other.xml", other)));
		final var unsupported = schema();
		unsupported.setProductName("Not a database product");
		assertThrows(IllegalArgumentException.class,
				() -> service.compare(valid, write("unsupported.xml", unsupported)));
	}

	@Test
	void savesExactSqlDraftWithInputFingerprintsAndSharedDelimiters() throws Exception {
		final var after = schema();
		for (final String name : new String[] { "CUSTOMER", "ORDERS" }) {
			final var table = new com.sqlapp.data.schemas.Table(name);
			table.getColumns().add(
					new com.sqlapp.data.schemas.Column("ID").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
			after.getTables().add(table);
		}
		final Path original = write("before.xml", schema());
		final Path target = write("after.xml", after);
		final var service = new SchemaComparisonService();
		final var result = service.inspect(original, target);
		assertEquals(2, result.statements());
		assertTrue(result.sqlDraft().contains(
				"-- Before: sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(original.toFile())));
		assertTrue(result.sqlDraft().contains(
				"-- After: sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(target.toFile())));
		final var dialect = com.sqlapp.data.schemas.SchemaUtils
				.getDialect((com.sqlapp.data.schemas.DbCommonObject<?>) after);
		final var statements = dialect.createSqlSplitter().parse(result.sqlDraft()).stream()
				.filter(part -> !part.getTextType().isComment() && !part.getText().isBlank()).toList();
		assertEquals(2, statements.size());
		// Only isolated in-memory HSQLDB; confirms statement boundaries survive export.
		try (var connection = java.sql.DriverManager
				.getConnection("jdbc:hsqldb:mem:draft_" + java.util.UUID.randomUUID(), "SA", "");
				var statement = connection.createStatement()) {
			for (final var part : statements)
				statement.execute(part.getText());
			statement.execute("INSERT INTO CUSTOMER VALUES(1)");
			statement.execute("INSERT INTO ORDERS VALUES(2)");
		}
		Files.writeString(target, "changed after comparison");
		final Path destination = directory.resolve("draft.sql");
		service.save(result, destination, true);
		assertEquals(result.sqlDraft(), Files.readString(destination));
		final Path review = directory.resolve("review.txt");
		service.save(result, review, false);
		assertEquals(result.review(), Files.readString(review));
		assertThrows(IllegalArgumentException.class, () -> service.save(result, directory.resolve("draft.txt"), true));
		assertThrows(IllegalArgumentException.class, () -> service.save(result, original, false));
		final Path alias = directory.resolve("source-alias.sql");
		Files.createLink(alias, original);
		assertThrows(IllegalArgumentException.class, () -> service.save(result, alias, true));
	}

	@Test
	void rendersTwoStatementsForEveryBundledDialectWithoutNullOrDuplicateDelimiters() throws Exception {
		int index = 0;
		for (final String product : new String[] { "HSQL Database Engine", "PostgreSQL", "MySQL", "Oracle",
				"Microsoft SQL Server", "SQLite" }) {
			final var before = schema();
			final var after = schema();
			before.setProductName(product);
			after.setProductName(product);
			for (final String name : new String[] { "CUSTOMER", "ORDERS" }) {
				final var table = new com.sqlapp.data.schemas.Table(name);
				table.getColumns().add(
						new com.sqlapp.data.schemas.Column("ID").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
				after.getTables().add(table);
			}
			final var result = new SchemaComparisonService().inspect(write("before" + index + ".xml", before),
					write("after" + index++ + ".xml", after));
			assertEquals(2, result.statements(), product);
			assertFalse(result.sqlDraft().contains("\nnull\n"), product);
			assertFalse(result.sqlDraft().contains(";\n;"), product);
			final var dialect = com.sqlapp.data.schemas.SchemaUtils
					.getDialect((com.sqlapp.data.schemas.DbCommonObject<?>) after);
			final var parsed = dialect.createSqlSplitter().parse(result.sqlDraft()).stream()
					.filter(part -> !part.getTextType().isComment() && !part.getText().isBlank()).toList();
			// SQL Server's shared splitter treats semicolon-separated DDL as one batch.
			assertEquals(product.equals("Microsoft SQL Server") ? 1 : 2, parsed.size(),
					product + "\n" + result.sqlDraft());
			assertTrue(parsed.stream().anyMatch(part -> part.getText().contains("CUSTOMER")), product);
			assertTrue(parsed.stream().anyMatch(part -> part.getText().contains("ORDERS")), product);
		}
	}

	@Test
	void noChangesCannotBeSavedAsMigrationSql() throws Exception {
		final var file = write("same.xml", schema());
		final var service = new SchemaComparisonService();
		final var result = service.inspect(file, file);
		assertEquals(0, result.statements());
		assertThrows(IllegalArgumentException.class, () -> service.save(result, directory.resolve("empty.sql"), true));
	}

	private Schema schema() {
		final var schema = new Schema("PUBLIC");
		schema.setProductName("HSQL Database Engine");
		schema.setProductMajorVersion(2);
		schema.setProductMinorVersion(7);
		return schema;
	}

	private Path write(final String name, final Schema schema) throws Exception {
		final Path file = directory.resolve(name);
		schema.writeXml(file.toFile());
		return file;
	}
}
