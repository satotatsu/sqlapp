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
