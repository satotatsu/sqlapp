/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class SchemaCompatibilityAnalyzerTest {

	@Test
	void acceptsWiderAndNullableAdditionalColumns() {
		final Schema expected = schema(column("NAME", 20L, false));
		final Schema actual = schema(column("NAME", 100L, false), column("NOTE", 30L, false));

		final SchemaCompatibilityReport report = SchemaCompatibilityAnalyzer.compare(expected, actual);

		assertEquals(SchemaCompatibility.COMPATIBLE, report.compatibility());
		assertTrue(report.isCompatible());
		assertEquals(2, report.changes().size());
	}

	@Test
	void rejectsNarrowerAndMandatoryAdditionalColumns() {
		final Schema expected = schema(column("NAME", 100L, false));
		final Schema actual = schema(column("NAME", 20L, false), column("REQUIRED", 30L, true));

		final SchemaCompatibilityReport report = SchemaCompatibilityAnalyzer.compare(expected, actual);

		assertEquals(SchemaCompatibility.BREAKING, report.compatibility());
		assertEquals(2, report.changes().stream()
				.filter(change -> change.compatibility() == SchemaCompatibility.BREAKING).count());
	}

	@Test
	void rejectsMissingContractObjects() {
		final Schema expected = schema(column("NAME", 20L, false));
		final Schema actual = new Schema("PUBLIC");

		final SchemaCompatibilityReport report = SchemaCompatibilityAnalyzer.compare(expected, actual);

		assertEquals(SchemaCompatibility.BREAKING, report.compatibility());
		assertEquals("Required table is missing", report.changes().getFirst().message());
	}

	@Test
	void acceptsGeneratedMandatoryColumnButRejectsIdentityAndPrimaryKeyDrift() {
		final Schema expected = schema(
				column("ID", null, true).setIdentity(true).setIdentityGenerationType(IdentityGenerationType.ByDefault),
				column("NAME", 20L, false));
		expected.getTables().get("CUSTOMER").setPrimaryKey(expected.getTables().get("CUSTOMER").getColumns().get("ID"));
		final Schema actual = schema(
				column("ID", null, true).setIdentity(true).setIdentityGenerationType(IdentityGenerationType.Always),
				column("NAME", 20L, false), column("CREATED_BY", 20L, true).setDefaultValue("'system'"));

		final SchemaCompatibilityReport report = SchemaCompatibilityAnalyzer.compare(expected, actual);

		assertEquals(SchemaCompatibility.BREAKING, report.compatibility());
		assertEquals(2, report.changes().stream()
				.filter(change -> change.compatibility() == SchemaCompatibility.BREAKING).count());
		assertTrue(report.changes().stream().anyMatch(change -> change.property().equals("identityGenerationType")));
		assertTrue(report.changes().stream().anyMatch(change -> change.property().equals("primaryKey")));
		assertTrue(report.changes().stream().anyMatch(change -> change.objectId().endsWith("CREATED_BY")
				&& change.compatibility() == SchemaCompatibility.COMPATIBLE));
	}

	private static Schema schema(final Column... columns) {
		final Schema schema = new Schema("PUBLIC");
		final Table table = new Table("CUSTOMER");
		for (final Column column : columns) {
			table.getColumns().add(column);
		}
		schema.getTables().add(table);
		return schema;
	}

	private static Column column(final String name, final Long length, final boolean notNull) {
		return new Column(name).setDataType(DataType.VARCHAR).setLength(length).setNotNull(notNull);
	}
}
