/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Method;

class OracleCharacterSetAssessmentTest {
	private final OracleMigrationAssessmentProvider provider = new OracleMigrationAssessmentProvider();

	private Schema schema() {
		return new Schema("APP").setProductName("Oracle").setProductMajorVersion(10);
	}

	private Column column(final Schema schema, final String table, final DataType type, final CharacterSemantics semantics) {
		final var result = new Column("C.with.dot").setDataType(type).setLength(20).setOctetLength(20)
				.setCharacterSemantics(semantics);
		final var owner = new Table(table);
		owner.getColumns().add(result);
		schema.getTables().add(owner);
		return result;
	}

	private MigrationAssessment assess(final Schema schema) {
		return provider.assess(List.of(schema), "26ai", Method.LOGICAL_MIGRATION, "AL32UTF8");
	}

	private List<String> rules(final MigrationAssessment result) {
		return result.findings().stream().map(f -> f.ruleId()).filter(id -> id.startsWith("oracle.charset.")).toList();
	}

	@Test
	void unknownSourceAndSemanticsAreNotGuessedFromEqualLengths() {
		final var schema = schema();
		column(schema, "T", DataType.VARCHAR, null);
		final var before = schema.clone();
		final var result = assess(schema);
		assertTrue(rules(result).contains("oracle.charset.source-unknown"));
		assertTrue(rules(result).contains("oracle.charset.semantics-unknown"));
		assertFalse(rules(result).contains("oracle.charset.byte-expansion"));
		assertFalse(result.hasBlockers());
		assertEquals(before, schema);
	}

	@Test
	void byteColumnsAreCandidatesEvenWithUnknownSourceAndCharColumnsRemainUnverified() {
		final var schema = schema();
		column(schema, "BYTE.TABLE", DataType.VARCHAR, CharacterSemantics.Byte);
		column(schema, "CHAR.TABLE", DataType.CHAR, CharacterSemantics.Char);
		final var result = assess(schema);
		assertTrue(rules(result).contains("oracle.charset.byte-expansion"));
		assertTrue(rules(result).contains("oracle.charset.char-byte-limit"));
		final var byteFinding = result.findings().stream().filter(f -> f.ruleId().endsWith("byte-expansion")).findFirst().orElseThrow();
		assertEquals("BYTE.TABLE", byteFinding.object().table());
		assertEquals("C.with.dot", byteFinding.object().name());
		assertTrue(byteFinding.reason().contains("UNKNOWN"));
		assertFalse(result.hasBlockers());
	}

	@Test
	void retainsCatalogCharsetAndSemanticsWithColumnOverride() {
		final var catalog = new Catalog("DB").setProductName("Oracle").setProductMajorVersion(10)
				.setCharacterSet("JA16SJIS").setCharacterSemantics(CharacterSemantics.Char);
		final var schema = schema();
		catalog.getSchemas().add(schema);
		column(schema, "INHERITED", DataType.VARCHAR, null);
		column(schema, "OVERRIDE", DataType.VARCHAR, CharacterSemantics.Byte);
		final var result = assess(schema);
		assertFalse(rules(result).contains("oracle.charset.source-unknown"));
		assertTrue(rules(result).contains("oracle.charset.byte-expansion"));
		assertTrue(rules(result).contains("oracle.charset.char-byte-limit"));
		assertTrue(result.findings().stream().anyMatch(f -> f.reason().contains("Source character set=JA16SJIS")));
	}

	@Test
	void usesCapturedNlsCharsetAndRejectsConflicts() {
		final var catalog = new Catalog("DB").setProductName("Oracle").setProductMajorVersion(10);
		catalog.getSettings().add(setting -> setting.setName("nls_characterset").setValue("JA16SJIS"));
		final var schema = schema();
		catalog.getSchemas().add(schema);
		column(schema, "T", DataType.VARCHAR, CharacterSemantics.Byte);
		assertFalse(rules(assess(schema)).contains("oracle.charset.source-unknown"));
		catalog.setCharacterSet("AL32UTF8");
		assertThrows(IllegalArgumentException.class, () -> assess(schema));
	}

	@Test
	void distinguishesOracleUtf8FromAl32utf8AndSkipsNationalAndLobByteRules() {
		final var schema = schema().setCharacterSet("AL32UTF8");
		column(schema, "SAME", DataType.VARCHAR, CharacterSemantics.Byte);
		column(schema, "NATIONAL", DataType.NVARCHAR, null);
		column(schema, "LOB", DataType.CLOB, null);
		column(schema, "NUMBER", DataType.DECIMAL, null);
		assertFalse(rules(assess(schema)).contains("oracle.charset.byte-expansion"));
		assertTrue(rules(assess(schema)).containsAll(List.of("oracle.charset.same-encoding",
				"oracle.charset.national-character-set", "oracle.charset.large-character-data")));
		schema.setCharacterSet("UTF8");
		assertTrue(rules(assess(schema)).contains("oracle.charset.byte-expansion"));
	}

	@Test
	void omittedOptionPreservesExistingChecksAndInvalidTargetsFailClearly() {
		final var schema = schema();
		column(schema, "T", DataType.VARCHAR, CharacterSemantics.Byte);
		assertTrue(rules(provider.assess(List.of(schema), "26ai", Method.LOGICAL_MIGRATION)).isEmpty());
		for (final String value : List.of("", "UTF8", "UTF8-32", "JA16SJIS")) {
			assertThrows(IllegalArgumentException.class,
					() -> provider.assess(List.of(schema), "26ai", Method.LOGICAL_MIGRATION, value));
		}
		assertFalse(rules(provider.assess(List.of(schema), "26ai", Method.LOGICAL_MIGRATION, "al32utf8")).isEmpty());
	}
}
