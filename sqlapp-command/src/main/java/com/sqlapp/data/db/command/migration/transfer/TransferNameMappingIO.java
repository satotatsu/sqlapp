/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.util.MessageDigests;
import com.sqlapp.util.YamlConverter;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.*;

/**
 * Bounded, pinned table/column mapping IO (names, optional target types and
 * value conversions); no connections or SQL execution.
 */
public final class TransferNameMappingIO {
	static final String CAPTURED_FILE = "transfer-mapping.yaml";
	private static final String DATA_JOB_PIN = "# transfer-name-mapping: ";
	private static final long MAX_BYTES = 1_048_576;

	static String pinDataJob(String text, Snapshot mapping) {
		return mapping == null ? text : DATA_JOB_PIN + mapping.fingerprint() + "\n" + text;
	}

	static void checkDataJobPin(String text, String fingerprint) {
		if (!text.startsWith(DATA_JOB_PIN + fingerprint + "\n"))
			throw new IllegalArgumentException("Reviewed data job name mapping fingerprint changed.");
	}

	static void capture(Snapshot mapping, Path directory) throws IOException {
		if (mapping == null)
			return;
		var io = new TransferNameMappingIO();
		if (!io.read(mapping.file()).fingerprint().equals(mapping.fingerprint()))
			throw new IllegalArgumentException("Name mapping changed during preparation. Review again.");
		io.write(directory.resolve(CAPTURED_FILE), mapping.text(), null);
	}

	/**
	 * One source column's target definition. YAML accepts a plain string as a
	 * rename and an object for type or value changes: {@code NAME: customer_name}
	 * or {@code STATUS: {name: status, type: "VARCHAR(10)", conversion: "..."}}.
	 *
	 * @param name       target column name, or null to keep the source name
	 * @param type       dialect-independent sqlapp DataType with optional length
	 *                   and scale, such as {@code VARCHAR(10)} or
	 *                   {@code DECIMAL(12,2)}
	 * @param conversion MVEL expression applied to each copied source value
	 */
	public record ColumnMapping(String name, String type, String conversion) {
		@com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.PROPERTIES)
		public ColumnMapping(@com.fasterxml.jackson.annotation.JsonProperty("name") String name,
				@com.fasterxml.jackson.annotation.JsonProperty("type") String type,
				@com.fasterxml.jackson.annotation.JsonProperty("conversion") String conversion) {
			if (name != null)
				TransferNameMappingIO.name(name);
			if (type != null)
				com.sqlapp.data.db.command.migration.bulk.BulkMigrationColumnType.parse(type);
			if (conversion != null)
				expression(conversion);
			if (name == null && type == null && conversion == null)
				throw new IllegalArgumentException("A column mapping needs a name, type or conversion.");
			this.name = name;
			this.type = type;
			this.conversion = conversion;
		}

		@com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
		public static ColumnMapping rename(String name) {
			return new ColumnMapping(Objects.requireNonNull(name, "name"), null, null);
		}

		public boolean renameOnly() {
			return type == null && conversion == null;
		}

		/**
		 * Same column definition with another target name; null keeps the source name.
		 */
		public ColumnMapping withName(String value) {
			return value == null && renameOnly() ? null : new ColumnMapping(value, type, conversion);
		}

		@com.fasterxml.jackson.annotation.JsonValue
		Object yaml() {
			if (renameOnly())
				return name;
			var value = new LinkedHashMap<String, String>();
			if (name != null)
				value.put("name", name);
			if (type != null)
				value.put("type", type);
			if (conversion != null)
				value.put("conversion", conversion);
			return value;
		}
	}

	/** Table rename and column definitions keyed by source column name. */
	public record TableMapping(String targetTable, Map<String, ColumnMapping> columns) {
		public TableMapping {
			if (targetTable != null) {
				name(targetTable);
				if (targetTable.contains("."))
					throw new IllegalArgumentException("Use targetSchema for namespaces, not dots in targetTable.");
			}
			columns = columns == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(columns));
			columns.forEach((source, column) -> {
				name(source);
				Objects.requireNonNull(column, "column mapping");
			});
		}

		/** Rename-only mapping from source to target column names. */
		public static TableMapping names(String targetTable, Map<String, String> names) {
			var columns = new LinkedHashMap<String, ColumnMapping>();
			if (names != null)
				names.forEach((source, target) -> columns.put(source, ColumnMapping.rename(target)));
			return new TableMapping(targetTable, columns);
		}

		/** Renamed columns only: source column name to target column name. */
		public Map<String, String> names() {
			var names = new LinkedHashMap<String, String>();
			columns.forEach((source, column) -> {
				if (column.name() != null)
					names.put(source, column.name());
			});
			return Collections.unmodifiableMap(names);
		}

		public boolean hasTypesOrConversions() {
			return columns.values().stream().anyMatch(column -> !column.renameOnly());
		}

		/** Same mapping with a different target table name. */
		public TableMapping withTargetTable(String value) {
			return new TableMapping(value, columns);
		}

		/**
		 * Replaces column names while keeping type and conversion definitions,
		 * including those of columns that are no longer renamed.
		 */
		public TableMapping withNames(Map<String, String> names) {
			var result = new LinkedHashMap<String, ColumnMapping>();
			columns.forEach((source, column) -> {
				var updated = column.withName(names.get(source));
				if (updated != null)
					result.put(source, updated);
			});
			names.forEach((source, target) -> result.putIfAbsent(source, ColumnMapping.rename(target)));
			return new TableMapping(targetTable, result);
		}
	}

	public record Configuration(int formatVersion, Map<String, TableMapping> tables) {
		public Configuration {
			if (formatVersion != 1 || tables == null)
				throw new IllegalArgumentException("Require formatVersion: 1 and tables.");
			tables = Collections.unmodifiableMap(new LinkedHashMap<>(tables));
			tables.forEach((source, mapping) -> {
				name(source);
				Objects.requireNonNull(mapping, "table mapping");
			});
		}

		/** True when any column defines a target type or value conversion. */
		public boolean hasTypesOrConversions() {
			return tables.values().stream().anyMatch(TableMapping::hasTypesOrConversions);
		}
	}

	/**
	 * Rejects column types and conversions on paths that cannot apply them end to
	 * end.
	 */
	static void requireNamesOnly(Snapshot mapping, String path) {
		if (mapping != null && mapping.configuration().hasTypesOrConversions())
			throw new IllegalArgumentException(
					"Column type and conversion mappings are supported only for direct JDBC transfers, not " + path
							+ ". Keep only names in the mapping file or use a direct JDBC transfer.");
	}

	public record Snapshot(Path file, String text, String fingerprint, Configuration configuration) {
		public Snapshot {
			file = file.toAbsolutePath().normalize();
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(fingerprint, "fingerprint");
			Objects.requireNonNull(configuration, "configuration");
		}
	}

	public Configuration validate(String text) {
		bytes(text);
		try {
			var tree = mapper().readTree(text);
			tree.path("tables").forEach(table -> {
				var target = table.get("targetTable");
				if (target != null && !target.isNull() && !target.isString())
					throw new IllegalArgumentException();
				table.path("columns").forEach(value -> {
					if (value.isObject())
						value.forEach(field -> {
							if (!field.isString())
								throw new IllegalArgumentException();
						});
					else if (!value.isString())
						throw new IllegalArgumentException();
				});
			});
			var value = mapper().readValue(text, Configuration.class);
			if (value == null)
				throw new IllegalArgumentException();
			return value;
		} catch (RuntimeException failure) {
			throw new IllegalArgumentException(
					"Invalid name mapping. Require formatVersion: 1 and tables keyed by source table identity; targetTable and columns are optional. A column is a target name or {name, type, conversion}; type is a sqlapp DataType such as VARCHAR(10). Check unknown/duplicate fields, names, types, blank expressions and value types.");
		}
	}

	public Snapshot read(Path file) throws IOException {
		byte[] bytes = BoundedMigrationFile.read(file, MAX_BYTES, "maxTransferMappingBytes", "Transfer name mapping");
		String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
		return new Snapshot(file, text, fingerprint(bytes), validate(text));
	}

	public String format(Configuration value) {
		return mapper().writeValueAsString(Objects.requireNonNull(value));
	}

	public Snapshot write(Path file, String text, String expectedFingerprint) throws IOException {
		String filename = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (!filename.endsWith(".yaml") && !filename.endsWith(".yml"))
			throw new IllegalArgumentException("Choose a .yaml or .yml mapping file.");
		var configuration = validate(text);
		byte[] bytes = bytes(text);
		check(file, expectedFingerprint);
		AtomicMigrationFile.write(file, temporary -> {
			Files.write(temporary, bytes);
			check(file, expectedFingerprint);
		});
		return new Snapshot(file, text, fingerprint(bytes), configuration);
	}

	private void check(Path file, String expected) throws IOException {
		if (expected == null ? Files.exists(file)
				: !Files.isRegularFile(file) || !expected.equals(read(file).fingerprint()))
			throw new IOException("Mapping file exists or changed. Reopen it before saving, or choose a new file.");
	}

	private static void name(String value) {
		if (value == null || value.isBlank() || value.codePoints().anyMatch(Character::isISOControl)
				|| !StandardCharsets.UTF_8.newEncoder().canEncode(value))
			throw new IllegalArgumentException(
					"Mapping names must be nonblank Unicode identifiers without control characters.");
	}

	private static void expression(String value) {
		if (value == null || value.isBlank()
				|| value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
			throw new IllegalArgumentException(
					"Conversion expressions must be nonblank text without control characters other than line breaks and tabs.");
	}

	private static byte[] bytes(String text) {
		if (text == null || text.length() > MAX_BYTES || !StandardCharsets.UTF_8.newEncoder().canEncode(text))
			throw new IllegalArgumentException("Name mapping must be valid UTF-8 and at most 1 MiB.");
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_BYTES)
			throw new IllegalArgumentException("Name mapping exceeds 1 MiB.");
		return bytes;
	}

	private static String fingerprint(byte[] bytes) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(bytes);
	}

	private static tools.jackson.databind.ObjectMapper mapper() {
		return new YamlConverter().getObjectMapper().rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
				DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
				.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
				.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
	}
}
