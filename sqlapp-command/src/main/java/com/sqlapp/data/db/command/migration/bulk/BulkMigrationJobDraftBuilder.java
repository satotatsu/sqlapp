/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTask;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.util.MessageDigests;
import com.sqlapp.util.YamlConverter;

/**
 * Offline creation of concise, pinned INSERT drafts from the canonical Schema
 * model.
 */
public class BulkMigrationJobDraftBuilder {
	private static final long MAX_SCHEMA_BYTES = 16_777_216;

	public record TableChoice(String table, List<String> primaryKey, String restriction) {
		public TableChoice {
			primaryKey = List.copyOf(primaryKey);
		}

		public boolean selectable() {
			return restriction == null;
		}
	}

	public record SchemaSnapshot(Path file, String fingerprint, List<TableChoice> tables) {
		public SchemaSnapshot {
			file = file.toAbsolutePath().normalize();
			tables = List.copyOf(tables);
		}
	}

	/**
	 * Reads at most 16 MiB, rejects DTDs/row data and reports per-table draft
	 * eligibility.
	 */
	public SchemaSnapshot inspect(final Path file) throws IOException {
		final var captured = read(file);
		return snapshot(file, captured);
	}

	/**
	 * Rechecks the XML byte fingerprint and delegates selected-table ordering to
	 * the shared planner. No connection, data fingerprint, resume guarantee or
	 * target compatibility is inferred.
	 */
	public String draft(final SchemaSnapshot reviewed, final List<String> selectedTables, final String jobId,
			final int chunkSize, final boolean verify) throws IOException {
		Objects.requireNonNull(reviewed, "reviewed");
		final var captured = read(reviewed.file());
		final var current = snapshot(reviewed.file(), captured);
		if (!Objects.equals(reviewed.fingerprint(), current.fingerprint())) {
			throw new IllegalArgumentException("Schema XML changed. Inspect it again before creating a draft.");
		}
		if (selectedTables == null || selectedTables.isEmpty()) {
			throw new IllegalArgumentException("Select at least one eligible table.");
		}
		if (selectedTables.stream().distinct().count() != selectedTables.size()) {
			throw new IllegalArgumentException("Select each table only once.");
		}
		final var tasks = new ArrayList<BulkMigrationJobTask>();
		for (final String selected : selectedTables) {
			final var choice = current.tables().stream().filter(table -> table.table().equals(selected)).findFirst()
					.orElseThrow(
							() -> new IllegalArgumentException("Table is not in the inspected Schema: " + selected));
			if (!choice.selectable())
				throw new IllegalArgumentException(choice.restriction());
			final var table = BulkMigrationJobConfigurationResolver.findTable(captured.tables(), selected);
			tasks.add(
					BulkMigrationJobTask.builder().taskId(taskId(selected)).sourceTable(table).requireEmptyTarget(true)
							.options(ChunkedBulkMigrationOption.builder().migrationId(taskId(selected))
									.chunkSize(chunkSize).mode(BulkMigrationMode.INSERT).resume(false).build())
							.build());
		}
		final String name = jobId == null || jobId.isBlank() ? null : jobId.trim();
		final var plan = name == null ? BulkMigrationJobPlanner.plan(tasks) : BulkMigrationJobPlanner.plan(name, tasks);
		final var yaml = new LinkedHashMap<String, Object>();
		if (name != null)
			yaml.put("jobId", name);
		yaml.put("schemaFile", current.file().toString());
		yaml.put("schemaFingerprint", current.fingerprint());
		final var definitions = new ArrayList<Map<String, Object>>();
		for (final var task : plan.getTasks()) {
			final var definition = new LinkedHashMap<String, Object>();
			definition.put("id", task.getTaskId());
			definition.put("table", identity(task.getEffectiveSourceTable()));
			definition.put("mode", "INSERT");
			definition.put("resume", false);
			definition.put("requireEmptyTarget", true);
			if (chunkSize != 10_000)
				definition.put("chunkSize", chunkSize);
			definitions.add(definition);
		}
		yaml.put("tasks", definitions);
		if (verify)
			yaml.put("verification", Map.of("enabled", true));
		final String text = "# Draft: review targets and policies, save, then inspect the live plan.\n"
				+ "# INSERT requires empty targets; resume is disabled. No data fingerprints were inferred.\n"
				+ new YamlConverter().toJsonString(yaml);
		new BulkMigrationJobConfigurationEditorIO().validate(text);
		return text;
	}

	private record Captured(String fingerprint, List<Table> tables) {
	}

	private Captured read(final Path file) throws IOException {
		final byte[] bytes = BoundedMigrationFile.read(file, MAX_SCHEMA_BYTES, "maxSchemaFileSizeBytes", "Schema XML");
		final var factory = XMLInputFactory.newFactory();
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		try {
			final var reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
			try {
				while (reader.hasNext()) {
					if (reader.next() == XMLStreamConstants.DTD)
						throw new IllegalArgumentException("Schema XML must not contain a DOCTYPE.");
				}
			} finally {
				reader.close();
			}
		} catch (final XMLStreamException failure) {
			throw new IOException("Invalid Schema XML.", failure);
		}
		final var tables = BulkMigrationJobConfigurationResolver
				.tables(SchemaUtils.readXml(new ByteArrayInputStream(bytes)));
		if (tables.stream().anyMatch(table -> !table.getRows().isEmpty())) {
			throw new IllegalArgumentException("Use Schema XML without row data for job creation.");
		}
		return new Captured("sha256:" + MessageDigests.SHA256.checksumAsString(bytes), tables);
	}

	private SchemaSnapshot snapshot(final Path file, final Captured captured) {
		final var choices = new ArrayList<TableChoice>();
		for (final var table : captured.tables()) {
			final String id = identity(table);
			final var keys = table.getPrimaryKeyConstraint() == null ? List.<String>of()
					: table.getPrimaryKeyConstraint().getColumns().stream().map(column -> column.getName()).toList();
			String restriction = null;
			if (java.util.stream.Stream.of(table.getCatalogName(), table.getSchemaName(), table.getName())
					.anyMatch(part -> part != null && part.contains("."))) {
				restriction = "Identifiers containing dots cannot be represented by the job resolver.";
			} else {
				try {
					BulkMigrationJobConfigurationResolver.findTable(captured.tables(), id);
				} catch (final RuntimeException failure) {
					restriction = failure.getMessage();
				}
			}
			if (restriction == null && (keys.isEmpty() || keys.stream().anyMatch(
					key -> table.getColumns().get(key) == null || !table.getColumns().get(key).isNotNull()))) {
				restriction = "Draft creation requires a complete NOT NULL primary key; specify an explicit unique keyset in YAML otherwise.";
			}
			choices.add(new TableChoice(id, keys, restriction));
		}
		return new SchemaSnapshot(file, captured.fingerprint(), choices);
	}

	private static String identity(final Table table) {
		if (table.getSchemaName() == null || table.getSchemaName().isBlank())
			return table.getName();
		return java.util.stream.Stream.of(table.getCatalogName(), table.getSchemaName(), table.getName())
				.filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining("."));
	}

	private static String taskId(final String table) {
		return "table_" + MessageDigests.SHA256
				.checksumAsString(table.getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 16);
	}
}
