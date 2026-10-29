/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import io.github.spannm.jackcess.DatabaseBuilder;

/**
 * Safe Access schema changes through a separate rebuilt file. Close JDBC
 * connections first.
 */
public final class MdbSchemaMigration {
	private MdbSchemaMigration() {
	}

	/**
	 * Resolved target schema, rename mappings and fingerprints for review before
	 * execution.
	 */
	public record Plan(Path source, Path target, String sourceFingerprint, String schemaXml, String schemaFingerprint,
			Map<String, String> tableMappings, Map<String, Map<String, String>> columnMappings) {
		public Plan {
			source = source.toAbsolutePath().normalize();
			target = target.toAbsolutePath().normalize();
			tableMappings = Map.copyOf(tableMappings);
			final Map<String, Map<String, String>> copy = new LinkedHashMap<>();
			columnMappings.forEach((table, columns) -> copy.put(table, Map.copyOf(columns)));
			columnMappings = Map.copyOf(copy);
		}
	}

	/**
	 * Successful metadata, row-count and copied-value verification. New and
	 * recalculated column values are not compared to source data.
	 */
	public record Result(Plan plan, Schema schema, Map<String, Long> rowCounts) {
		public Result {
			rowCounts = Map.copyOf(rowCounts);
		}
	}

	/**
	 * Post-write verification failed. The separate target and all diagnostics
	 * remain available.
	 */
	public static final class VerificationException extends IOException {
		private static final long serialVersionUID = 1L;
		private final Plan plan;
		private final Schema schema;
		private final Map<String, Long> rowCounts;
		private final List<String> issues;

		VerificationException(final Plan plan, final Schema schema, final Map<String, Long> rowCounts,
				final List<String> issues) {
			super("Access rebuild verification failed for " + plan.target() + ": " + String.join("; ", issues));
			this.plan = plan;
			this.schema = schema;
			this.rowCounts = Map.copyOf(rowCounts);
			this.issues = List.copyOf(issues);
		}

		public Plan plan() {
			return plan;
		}

		public Schema schema() {
			return schema;
		}

		public Map<String, Long> rowCounts() {
			return rowCounts;
		}

		public List<String> issues() {
			return issues;
		}
	}

	/**
	 * Common entry point: edit the shared Schema, rebuild to a new file, and verify
	 * it.
	 */
	public static Result rebuild(final Path source, final Path target, final Consumer<Schema> changes)
			throws IOException {
		return execute(plan(source, target, changes));
	}

	/**
	 * Renames are inferred only for the same table/column objects retained by the
	 * editor.
	 */
	public static Plan plan(final Path source, final Path target, final Consumer<Schema> changes) throws IOException {
		validatePaths(source, target);
		final String fingerprint = fileFingerprint(source);
		final var inventory = MdbFileLoader.loadForAssessment(source);
		if (!inventory.relationshipsCollected())
			throw new IllegalArgumentException("Linked tables are not supported by schema rebuild planning");
		for (final var query : inventory.savedQueries()) {
			if (query.hidden() || !(query.type().equals("SELECT") || query.type().equals("UNION"))) {
				throw new UnsupportedOperationException(
						"Schema rebuild cannot preserve saved query: " + query.name() + " (" + query.type() + ")");
			}
		}
		final Schema schema = MdbFileLoader.load(source);
		final Map<Table, String> originalTables = new IdentityHashMap<>();
		final Map<Column, String> originalColumns = new IdentityHashMap<>();
		for (final Table table : schema.getTables()) {
			table.setRowIteratorHandler(null);
			originalTables.put(table, table.getName());
			for (final Column column : table.getColumns())
				originalColumns.put(column, column.getName());
		}
		Objects.requireNonNull(changes, "Schema editor is required").accept(schema);
		validateMetadataOnly(schema);
		try (var database = new DatabaseBuilder().withPath(source).withReadOnly(true).open()) {
			MdbJackcessSupport.validateTargetSchema(database, schema);
		}
		final Map<String, String> tables = new LinkedHashMap<>();
		final Map<String, Map<String, String>> columns = new LinkedHashMap<>();
		for (final Table table : schema.getTables()) {
			final String oldTable = originalTables.get(table);
			if (oldTable != null && !oldTable.equals(table.getName()))
				tables.put(table.getName(), oldTable);
			final Map<String, String> renamed = new LinkedHashMap<>();
			for (final Column column : table.getColumns()) {
				final String oldColumn = originalColumns.get(column);
				if (oldColumn != null && !oldColumn.equals(column.getName()))
					renamed.put(column.getName(), oldColumn);
			}
			if (!renamed.isEmpty())
				columns.put(table.getName(), renamed);
		}
		final StringWriter xml = new StringWriter();
		try {
			schema.writeXml(xml);
		} catch (final javax.xml.stream.XMLStreamException e) {
			throw new IOException("Cannot serialize target Access schema", e);
		}
		if (!fingerprint.equals(fileFingerprint(source)))
			throw new IOException("Access source changed during planning: " + source);
		return new Plan(source, target, fingerprint, xml.toString(),
				fingerprint(xml.toString().getBytes(StandardCharsets.UTF_8)), tables, columns);
	}

	/**
	 * Executes an unchanged plan. The source is never replaced; an existing target
	 * is rejected.
	 */
	public static Result execute(final Plan plan) throws IOException {
		validatePaths(plan.source(), plan.target());
		if (!plan.sourceFingerprint().equals(fileFingerprint(plan.source())))
			throw new IOException("Access source changed since planning; create a new plan");
		if (!plan.schemaFingerprint().equals(fingerprint(plan.schemaXml().getBytes(StandardCharsets.UTF_8))))
			throw new IOException("Target schema fingerprint does not match the plan");
		final Schema targetSchema = new Schema();
		try {
			targetSchema.loadXml(new StringReader(plan.schemaXml()));
		} catch (final javax.xml.stream.XMLStreamException e) {
			throw new IOException("Cannot read target Access schema", e);
		}
		validateMetadataOnly(targetSchema);
		MdbJackcessSupport.rebuild(plan.source(), plan.target(), targetSchema, plan.tableMappings(),
				plan.columnMappings());
		final Schema observed = MdbFileLoader.load(plan.target());
		final List<String> issues = new ArrayList<>(MdbSchemaVerification.verify(targetSchema, observed));
		final Map<String, Long> counts = new LinkedHashMap<>();
		final Map<String, String> tableMappings = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		tableMappings.putAll(plan.tableMappings());
		final Map<String, Map<String, String>> columnMappings = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		columnMappings.putAll(plan.columnMappings());
		try (var source = new DatabaseBuilder().withPath(plan.source()).withReadOnly(true).open();
				var target = new DatabaseBuilder().withPath(plan.target()).withReadOnly(true).open()) {
			source.setDateTimeType(io.github.spannm.jackcess.DateTimeType.LOCAL_DATE_TIME);
			target.setDateTimeType(io.github.spannm.jackcess.DateTimeType.LOCAL_DATE_TIME);
			for (final Table expected : targetSchema.getTables()) {
				final var original = source
						.getTable(tableMappings.getOrDefault(expected.getName(), expected.getName()));
				final var rebuilt = target.getTable(expected.getName());
				if (rebuilt == null)
					continue;
				final long count = rebuilt.getRowCount();
				if (count != (original == null ? 0 : original.getRowCount()))
					issues.add("Rebuilt row count differs: " + expected.getName());
				counts.put(expected.getName(), count);
				MdbDataVerification.verify(original, rebuilt, columnMappings.get(expected.getName()), issues);
			}
		}
		if (!plan.sourceFingerprint().equals(fileFingerprint(plan.source())))
			issues.add("Access source changed during execution");
		if (!issues.isEmpty())
			throw new VerificationException(plan, observed, counts, issues);
		return new Result(plan, observed, counts);
	}

	private static void validatePaths(final Path source, final Path target) throws IOException {
		if (source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize()))
			throw new IllegalArgumentException("Rebuild target must differ from source");
		if (!Files.isRegularFile(source))
			throw new IllegalArgumentException("Access source file not found: " + source);
		if (Files.exists(target))
			throw new IllegalArgumentException("Rebuild target already exists: " + target);
	}

	private static void validateMetadataOnly(final Schema schema) {
		for (final Table table : schema.getTables()) {
			if (table.getRows().size() != 0 || !table.isDefaultRowIteratorHandler()) {
				throw new IllegalArgumentException(
						"Schema edits cannot provide row data or row iterators: " + table.getName());
			}
		}
	}

	private static String fileFingerprint(final Path path) throws IOException {
		try (var input = Files.newInputStream(path)) {
			final MessageDigest digest = digest();
			final byte[] buffer = new byte[65536];
			int size;
			while ((size = input.read(buffer)) >= 0)
				digest.update(buffer, 0, size);
			return "sha256:" + HexFormat.of().formatHex(digest.digest());
		}
	}

	private static String fingerprint(final byte[] bytes) {
		return "sha256:" + HexFormat.of().formatHex(digest().digest(bytes));
	}

	private static MessageDigest digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
