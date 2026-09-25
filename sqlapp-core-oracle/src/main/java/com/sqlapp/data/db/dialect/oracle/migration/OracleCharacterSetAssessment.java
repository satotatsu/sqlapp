/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.migration;

import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Evidence;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Finding;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.ObjectId;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Severity;

/** Metadata-only checks for an AL32UTF8 destination; never guesses source encoding or scans values. */
final class OracleCharacterSetAssessment {
	private static final String REFERENCE = "https://docs.oracle.com/en/database/oracle/dmu/23.1/dumag/ch1_overview.html";

	private OracleCharacterSetAssessment() { }

	static void assess(final Schema schema, final List<Finding> findings) {
		final var schemaId = new ObjectId(schema.getCatalogName(), schema.getName(), "schema", schema.getName());
		final String schemaCharacterSet = sourceCharacterSet(schema);
		if (unknown(schemaCharacterSet)) {
			add(findings, "source-unknown", Severity.REVIEW, schemaId,
					"Source database character set is absent from the Schema snapshot; target=AL32UTF8.",
					"Capture NLS_CHARACTERSET from the source database. Do not assume JA16SJIS or infer it from language or column length.");
		}
		for (final var table : schema.getTables()) {
			for (final Column column : table.getColumns()) {
				final DataType type = column.getDataType();
				if (type == null || !type.isCharacter()) {
					continue;
				}
				final var id = new ObjectId(schema.getCatalogName(), schema.getName(), "column", column.getName(), table.getName());
				if (type == DataType.NCHAR || type == DataType.NVARCHAR || type == DataType.NCLOB || type == DataType.LONGNVARCHAR) {
					add(findings, "national-character-set", Severity.REVIEW, id,
							"This national character column is not governed by the database AL32UTF8 target setting.",
							"Compare source and target NLS_NCHAR_CHARACTERSET and national-type limits separately; do not calculate expansion using AL32UTF8.");
					continue;
				}
				final String modeledSource = unknown(column.getCharacterSet()) ? schemaCharacterSet : column.getCharacterSet();
				final String source = unknown(modeledSource) ? "UNKNOWN" : modeledSource;
				final String context = "Source character set=" + source + "; target=AL32UTF8; modeled type=" + type
						+ "; length=" + column.getLength() + "; source octetLength=" + column.getOctetLength() + ". ";
				if (type != DataType.CHAR && type != DataType.VARCHAR) {
					add(findings, "large-character-data", Severity.REVIEW, id, context
							+ "This character type is outside bounded CHAR/VARCHAR2 column checks.",
							"Validate conversion and LOB/LONG handling with Oracle tools and a Data Pump rehearsal; no fixed expansion factor or byte limit was inferred.");
					continue;
				}
				final CharacterSemantics semantics = column.getCharacterSemantics();
				if (semantics == null) {
					add(findings, "semantics-unknown", Severity.REVIEW, id, context
							+ "BYTE/CHAR semantics are unknown; length equality does not establish semantics.",
							"Capture ALL_TAB_COLUMNS.CHAR_USED, CHAR_LENGTH and DATA_LENGTH for this column. Do not infer existing column semantics from NLS_LENGTH_SEMANTICS.");
				} else if (semantics == CharacterSemantics.Byte && !"AL32UTF8".equalsIgnoreCase(source)) {
					add(findings, "byte-expansion", Severity.WARNING, id, context
							+ "The Schema models BYTE semantics. Conversion may exceed the unchanged byte declaration; this is a candidate, not a measured overflow.",
							"Verify CHAR_USED and the import DDL, then measure converted values against the actual target column. Review widening or explicit CHAR semantics per column; changing NLS_LENGTH_SEMANTICS alone does not override BYTE DDL.");
				} else {
					add(findings, semantics == CharacterSemantics.Char ? "char-byte-limit" : "same-encoding",
							Severity.REVIEW, id, context + "The Schema models " + semantics + " semantics. "
									+ (semantics == CharacterSemantics.Char ? "Character semantics do not eliminate data-type byte limits."
											: "No encoding expansion is expected for valid AL32UTF8 data with unchanged target DDL."),
							"Verify column semantics and target DDL, applicable CHAR/VARCHAR2 byte limits and MAX_STRING_SIZE. The source octetLength is not the target limit. Validate actual data before declaring the column safe.");
				}
			}
		}
		findings.add(new Finding("oracle.charset.data-validation", Severity.REVIEW, Evidence.MANUAL_CHECK, schemaId,
				"AL32UTF8 checks used metadata only; no row values or original bytes were examined.",
				"Validate source encoding and invalid byte sequences with applicable Oracle tooling, then rehearse Data Pump import. Check index key lengths, byte-oriented SQL/PLSQL and client buffers. Report full scans versus sampling; no overflow counts or required widths are available from this assessment.", REFERENCE));
	}

	private static boolean unknown(final String value) {
		return value == null || value.isBlank();
	}

	private static String sourceCharacterSet(final Schema schema) {
		String value = schema.getCharacterSet();
		final Catalog catalog = schema.getAncestor(Catalog.class);
		if (catalog != null) {
			for (final var setting : catalog.getSettings()) {
				if ("NLS_CHARACTERSET".equalsIgnoreCase(setting.getName()) && !unknown(setting.getValue())) {
					if (!unknown(value) && !value.equalsIgnoreCase(setting.getValue())) {
						throw new IllegalArgumentException("Conflicting source characterSet and NLS_CHARACTERSET in Schema " + schema.getName());
					}
					value = setting.getValue();
				}
			}
		}
		return value;
	}

	private static void add(final List<Finding> findings, final String rule, final Severity severity,
			final ObjectId id, final String reason, final String action) {
		findings.add(new Finding("oracle.charset." + rule, severity, Evidence.SCHEMA, id, reason, action, REFERENCE));
	}
}
