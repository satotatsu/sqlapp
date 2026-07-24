/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.util.Locale;

import com.sqlapp.data.schemas.migration.LegacyMigrationContract;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.DataSet;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract.Field;

/**
 * Renders an AI-ready extraction specification and a PL/I implementation
 * template. IMS access calls intentionally remain adapter points.
 */
public class PliCsvExtractorGenerator {

	public void validate(LegacyMigrationContract contract) {
		new LegacyMigrationContractValidator().validate(contract);
		java.util.Set<String> symbols = new java.util.HashSet<>();
		for (DataSet dataSet : contract.getDataSets()) {
			if (!symbols.add(symbol(dataSet))) {
				throw new com.sqlapp.exceptions.CommandException(
						"Duplicate generated PL/I symbol: " + symbol(dataSet));
			}
		}
	}

	public String specification(LegacyMigrationContract contract, String programName) {
		StringBuilder builder = new StringBuilder();
		line(builder, "# PL/I CSV extraction specification");
		line(builder, "");
		line(builder, "- Program: `" + programName + "`");
		line(builder, "- Migration: `" + value(contract.getMigrationId()) + "`");
		line(builder, "- Contract fingerprint: `" + value(contract.getMappingFingerprint()) + "`");
		line(builder, "- Encoding: `" + value(contract.getCsv().getEncoding()) + "`");
		line(builder, "- Delimiter: `" + visible(contract.getCsv().getDelimiter()) + "`");
		line(builder, "- Quote: `" + visible(contract.getCsv().getQuote()) + "`");
		line(builder, "- NULL representation: `" + visible(contract.getCsv().getNullValue()) + "`");
		line(builder, "- Header row: `" + contract.getCsv().isHeader() + "`");
		line(builder, "- Record separator: `" + value(contract.getCsv().getRecordSeparator()) + "`");
		line(builder, "");
		line(builder, "## Required CSV behavior");
		line(builder, "");
		line(builder, "1. Write fields in the listed order; do not reorder them.");
		line(builder, "2. Represent null values with the configured NULL representation.");
		line(builder, "3. Quote a non-null field when it contains the delimiter, quote, CR or LF.");
		line(builder, "4. Escape a quote inside a quoted field by writing it twice.");
		line(builder, "5. Write one CSV record atomically; abort on truncation or output errors.");
		line(builder, "6. Propagate every listed ancestor key into child and descendant records.");
		line(builder, "7. Write occurrence numbers as one-based values.");
		line(builder, "");
		line(builder, "## IMS adapter requirements");
		line(builder, "");
		line(builder, "- Replace `READ-NEXT-ROOT` with the site's qualified GU/GN root retrieval.");
		line(builder, "- Replace `PROCESS-CHILDREN-*` with qualified GNP traversal for that parent.");
		line(builder, "- Treat non-success IMS status codes as fatal except the configured end/not-found status.");
		line(builder, "- Keep the root key in the checkpoint so a failed run can be restarted deterministically.");
		for (DataSet dataSet : contract.getDataSets()) {
			dataSet(builder, dataSet);
		}
		return builder.toString();
	}

	public String template(LegacyMigrationContract contract, String programName) {
		StringBuilder builder = new StringBuilder();
		line(builder, "/* GENERATED PL/I TEMPLATE. REVIEW IMS PCB AND FILE DECLARATIONS BEFORE USE. */");
		line(builder, programName + ": PROCEDURE OPTIONS(MAIN);");
		line(builder, "");
		line(builder, "   /* CSV: encoding=" + value(contract.getCsv().getEncoding())
				+ ", delimiter=" + visible(contract.getCsv().getDelimiter())
				+ ", quote=" + visible(contract.getCsv().getQuote())
				+ ", null=" + visible(contract.getCsv().getNullValue()) + " */");
		line(builder, "   /* TODO: INCLUDE the actual IMS PCB and PL/I record declarations. */");
		line(builder, "   /* TODO: DECLARE one OUTPUT RECORD file for each CSV listed below. */");
		for (DataSet dataSet : contract.getDataSets()) {
			line(builder, "   /* FILE " + fileSymbol(dataSet) + " -> " + dataSet.getFileName() + " */");
		}
		line(builder, "");
		line(builder, "   CALL OPEN-OUTPUT-FILES;");
		if (contract.getCsv().isHeader()) {
			line(builder, "   CALL WRITE-HEADERS;");
		}
		line(builder, "   DO WHILE (READ-NEXT-ROOT());");
		for (DataSet root : contract.getDataSets().stream()
				.filter(dataSet -> dataSet.getParentDataSetId() == null).toList()) {
			line(builder, "      CALL WRITE-" + symbol(root) + ";");
			for (DataSet child : directChildren(contract, root.getId())) {
				line(builder, "      CALL PROCESS-CHILDREN-" + symbol(child) + ";");
			}
		}
		line(builder, "      CALL SAVE-CHECKPOINT;");
		line(builder, "   END;");
		line(builder, "   CALL CLOSE-OUTPUT-FILES;");
		line(builder, "   RETURN;");
		for (DataSet dataSet : contract.getDataSets()) {
			writeProcedure(builder, dataSet);
			if (dataSet.getParentDataSetId() != null) {
				childProcedure(builder, contract, dataSet);
			}
		}
		commonProcedures(builder, contract);
		line(builder, "END " + programName + ";");
		return builder.toString();
	}

	private void dataSet(StringBuilder builder, DataSet dataSet) {
		line(builder, "");
		line(builder, "## `" + dataSet.getId() + "` → `" + dataSet.getFileName() + "`");
		line(builder, "");
		line(builder, "- Source path: `" + dataSet.getSourcePath() + "`");
		line(builder, "- Target: `" + qualifiedName(dataSet) + "`");
		line(builder, "- Staging table: `" + value(dataSet.getStagingTable()) + "`");
		line(builder, "- Hierarchy depth/load order: `" + dataSet.getHierarchyDepth() + "/"
				+ dataSet.getLoadOrder() + "`");
		if (dataSet.getParentDataSetId() != null) {
			line(builder, "- Parent data set: `" + dataSet.getParentDataSetId() + "`");
		}
		if (dataSet.getMaximumOccurrences() != null) {
			line(builder, "- Occurrences: `1.." + dataSet.getMaximumOccurrences() + "` using `"
					+ dataSet.getOccurrenceColumn() + "`");
		}
		line(builder, "");
		line(builder, "| CSV | Staging column | Source path | Target column | Action |");
		line(builder, "|---:|---|---|---|---|");
		int csvPosition = 1;
		for (Field field : dataSet.getFields().stream().filter(Field::isExtracted).toList()) {
			line(builder, "| " + csvPosition++ + " | `" + value(field.getStagingColumn()) + "` | `"
					+ value(field.getSourcePath()) + "` | `" + value(field.getTargetColumn()) + "` | `"
					+ value(field.getAction()) + "` |");
		}
		if (!dataSet.getAncestorKeys().isEmpty()) {
			line(builder, "");
			line(builder, "Ancestor key propagation:");
			for (var key : dataSet.getAncestorKeys()) {
				line(builder, "- `" + key.getAncestorDataSetId() + "`: "
						+ key.getColumns().stream().map(column -> "`" + column.getAncestorColumn()
								+ "` → `" + column.getSourceColumn() + "` → `"
								+ column.getTargetColumn() + "`").toList());
			}
		}
	}

	private void writeProcedure(StringBuilder builder, DataSet dataSet) {
		line(builder, "");
		line(builder, "WRITE-" + symbol(dataSet) + ": PROCEDURE;");
		line(builder, "   CALL CSV-BEGIN-RECORD(" + fileSymbol(dataSet) + ");");
		int csvPosition = 0;
		var fields = dataSet.getFields().stream().filter(Field::isExtracted).toList();
		for (Field field : fields) {
			if (csvPosition++ > 0) {
				line(builder, "   CALL CSV-WRITE-DELIMITER(" + fileSymbol(dataSet) + ");");
			}
			String expression = field.isOccurrenceIndex()
					? "OCCURRENCE-INDEX-" + symbol(dataSet) : pliReference(field.getSourcePath());
			line(builder, "   CALL CSV-WRITE-FIELD(" + fileSymbol(dataSet) + ", " + expression + ");"
					+ " /* " + field.getStagingColumn() + " */");
		}
		line(builder, "   CALL CSV-END-RECORD(" + fileSymbol(dataSet) + ");");
		line(builder, "END WRITE-" + symbol(dataSet) + ";");
	}

	private void childProcedure(StringBuilder builder, LegacyMigrationContract contract, DataSet dataSet) {
		line(builder, "");
		line(builder, "PROCESS-CHILDREN-" + symbol(dataSet) + ": PROCEDURE;");
		line(builder, "   /* TODO: position IMS at children of the current "
				+ dataSet.getParentDataSetId() + " segment. */");
		line(builder, "   DO OCCURRENCE-INDEX-" + symbol(dataSet) + " = 1 TO "
				+ (dataSet.getMaximumOccurrences() == null ? "CHILD-COUNT" : dataSet.getMaximumOccurrences()) + ";");
		line(builder, "      IF READ-NEXT-CHILD('" + dataSet.getSourcePath() + "') THEN DO;");
		line(builder, "         CALL WRITE-" + symbol(dataSet) + ";");
		for (DataSet child : directChildren(contract, dataSet.getId())) {
			line(builder, "         CALL PROCESS-CHILDREN-" + symbol(child) + ";");
		}
		line(builder, "      END;");
		line(builder, "      ELSE LEAVE;");
		line(builder, "   END;");
		line(builder, "END PROCESS-CHILDREN-" + symbol(dataSet) + ";");
	}

	private void commonProcedures(StringBuilder builder, LegacyMigrationContract contract) {
		line(builder, "");
		line(builder, "/* TODO: Implement these adapters for the target compiler and IMS installation:");
		line(builder, "   OPEN-OUTPUT-FILES, WRITE-HEADERS, READ-NEXT-ROOT, READ-NEXT-CHILD,");
		line(builder, "   SAVE-CHECKPOINT, CLOSE-OUTPUT-FILES, CSV-BEGIN-RECORD,");
		line(builder, "   CSV-WRITE-DELIMITER, CSV-WRITE-FIELD and CSV-END-RECORD.");
		line(builder, "   CSV-WRITE-FIELD must use delimiter '" + visible(contract.getCsv().getDelimiter())
				+ "', quote '" + visible(contract.getCsv().getQuote()) + "', null '"
				+ visible(contract.getCsv().getNullValue()) + "', double embedded quotes,");
		line(builder, "   and quote fields containing delimiter, quote, CR or LF. */");
	}

	private java.util.List<DataSet> directChildren(LegacyMigrationContract contract, String parentId) {
		return contract.getDataSets().stream()
				.filter(dataSet -> parentId.equals(dataSet.getParentDataSetId())).toList();
	}

	private String qualifiedName(DataSet dataSet) {
		return (dataSet.getTargetSchema() == null ? "" : dataSet.getTargetSchema() + ".")
				+ value(dataSet.getTargetTable());
	}

	private String fileSymbol(DataSet dataSet) {
		return "FILE-" + symbol(dataSet);
	}

	private String symbol(DataSet dataSet) {
		String value = dataSet.getId().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9@#$]+", "-");
		if (value.length() <= 14) {
			return value;
		}
		String hash = String.format(Locale.ROOT, "%08X", dataSet.getId().hashCode());
		return value.substring(0, 5) + "-" + hash;
	}

	private String pliReference(String path) {
		if (path == null) {
			return "/* MISSING-SOURCE-PATH */";
		}
		String[] parts = path.split("\\.");
		StringBuilder builder = new StringBuilder();
		for (int i = parts.length - 1; i >= 0; i--) {
			if (builder.length() > 0) {
				builder.append(" OF ");
			}
			builder.append(parts[i]);
		}
		return builder.toString();
	}

	private String visible(String value) {
		if (value == null || value.isEmpty()) {
			return "<empty>";
		}
		return value.replace("\r", "\\r").replace("\n", "\\n").replace("`", "\\`");
	}

	private String value(String value) {
		return value == null ? "" : value;
	}

	private void line(StringBuilder builder, String value) {
		builder.append(value).append(System.lineSeparator());
	}
}
