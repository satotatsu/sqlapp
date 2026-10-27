/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationResolver;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.util.YamlConverter;
import com.sqlapp.util.file.CsvWriter;
import com.sqlapp.util.iterator.IteratorCloseUtils;

/**
 * Source-only streaming CSV export. No target connection, import or DDL is
 * performed.
 */
public final class TransferFileExportService {
	private static final int MAX_FIELD_CHARS = 1_048_576;

	public TransferFilePackageIO.Snapshot export(Path jobFile, Map<String, ? extends DataSource> connections)
			throws java.io.IOException {
		final var io = new TransferJobConfigurationIO();
		final var job = inspectConfiguration(jobFile);
		final var configuration = job.configuration();
		final DataSource source = connections == null ? null : connections.get(configuration.source());
		if (source == null)
			throw new IllegalArgumentException("Supply the source runtime connection for file export.");
		final Path root = job.filesDirectory();
		Files.createDirectories(root);
		final Path directory = Files.createTempDirectory(root, "export-");
		final var exported = new ArrayList<TransferFilePackageIO.TableFile>();
		final var warnings = new ArrayList<TransferFilePackageIO.Warning>();
		String schemaFingerprint;
		try (var connection = source.getConnection()) {
			connection.setReadOnly(true);
			if (!connection.getMetaData().supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE))
				throw new IllegalArgumentException("CSV export requires SERIALIZABLE read isolation.");
			connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
			connection.setAutoCommit(false);
			try {
				if (connection.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE)
					throw new IllegalArgumentException("The source did not retain SERIALIZABLE isolation.");
				final var schema = TransferJobPreparationService.readSchema(connection,
						TransferJobPreparationService.dialect(connection), configuration.sourceSchema());
				final Path xml = directory.resolve("source-schema.xml");
				try (var writer = Files.newBufferedWriter(xml, java.nio.charset.StandardCharsets.UTF_8)) {
					schema.writeXml(writer);
				}
				schemaFingerprint = BoundedMigrationFile.sha256(xml, 16_777_216L, "maxSchemaBytes", "Schema");
				final var definition = new LinkedHashMap<String, Object>();
				definition.put("schemaFile", "source-schema.xml");
				definition.put("schemaFingerprint", schemaFingerprint);
				final var tasks = new ArrayList<Map<String, Object>>();
				for (int index = 0; index < configuration.tables().size(); index++)
					tasks.add(Map.of("id", "export-table-" + (index + 1), "table", configuration.tables().get(index),
							"mode", "INSERT", "resume", false));
				definition.put("tasks", tasks);
				final Path selection = directory.resolve("source-selection.yaml");
				final var selected = new BulkMigrationJobConfigurationEditorIO().write(selection,
						new YamlConverter().toJsonString(definition), null);
				final var resolution = new BulkMigrationJobConfigurationResolver().resolveJob(selection.toFile(),
						connection, selected.fingerprint(), 1_048_576L, 16_777_216L);
				Files.delete(selection);
				final var identities = new java.util.HashSet<List<String>>();
				for (final var task : resolution.plan().getTasks()) {
					final var table = task.getEffectiveSourceTable();
					final var view = TransferCsvTemporalCodec.exportView(table,
							configuration.compatibilityPolicy() == TransferJobConfiguration.CompatibilityPolicy.ERROR
									? TransferJobConfiguration.TemporalPolicy.ERROR
									: configuration.files().unsupportedTemporalPolicy());
					final boolean[] driverText = new boolean[table.getColumns().size()];
					for (int index = 0; index < driverText.length; index++)
						driverText[index] = TransferCsvTemporalCodec.temporal(table.getColumns().get(index))
								&& !TransferCsvTemporalCodec.supported(table.getColumns().get(index));
					for (var column : table.getColumns())
						if (TransferCsvTemporalCodec.temporal(column) && !TransferCsvTemporalCodec.supported(column))
							warnings.add(new TransferFilePackageIO.Warning(task.getTaskId(), column.getName(),
									"Temporal column exported as JDBC text. Automatic typed import is unsupported; review format, precision and timezone before conversion."));
					if (!identities.add(
							java.util.Arrays.asList(table.getCatalogName(), table.getSchemaName(), table.getName())))
						throw new IllegalArgumentException("Multiple selections resolve to the same source table.");
					final String filename = "table-" + (exported.size() + 1) + ".csv";
					final Path file = directory.resolve(filename);
					final List<String> columns = table.getColumns().stream().map(column -> column.getName()).toList();
					long count = 0;
					final var rows = new com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource(connection, view,
							((com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource) task.getKeysetSource())
									.getKeyColumnNames()) {
						@Override
						protected Object readColumn(java.sql.ResultSet rows, int index,
								com.sqlapp.data.schemas.Column column, com.sqlapp.data.db.datatype.DbDataType<?> type)
								throws java.sql.SQLException {
							return driverText[index - 1] ? rows.getString(index)
									: super.readColumn(rows, index, column, type);
						}
					}.iterator(null);
					Throwable primary = null;
					try (var text = new java.io.BufferedWriter(
							new java.io.OutputStreamWriter(Files.newOutputStream(file),
									Charset.forName(configuration.files().encoding()).newEncoder()));
							var writer = new CsvWriter(text, settings -> {
								settings.setNullValue(configuration.files().nullValue());
								settings.setEmptyValue("");
								settings.setQuoteAllFields(true);
								settings.setQuoteNulls(true);
								settings.setNormalizeLineEndingsWithinQuotes(false);
								settings.setIgnoreLeadingWhitespaces(false);
								settings.setIgnoreTrailingWhitespaces(false);
								settings.setMaxCharsPerColumn(MAX_FIELD_CHARS);
								settings.getFormat().setLineSeparator("\n");
							})) {
						writer.writeHeader(columns.toArray(String[]::new));
						while (rows.hasNext()) {
							final var values = rows.next().getValuesAsMap();
							final String[] fields = new String[columns.size()];
							for (int index = 0; index < fields.length; index++)
								fields[index] = scalar(table.getColumns().get(index), values.get(columns.get(index)),
										configuration.files().nullValue(), driverText[index]);
							writer.writeRow(fields);
							count = Math.addExact(count, 1);
						}
					} catch (Exception | Error failure) {
						primary = failure;
						throw failure;
					} finally {
						IteratorCloseUtils.close(primary, rows);
					}
					exported.add(new TransferFilePackageIO.TableFile(task.getTaskId(), table.getCatalogName(),
							table.getSchemaName(), table.getName(), columns, filename, count, Files.size(file),
							BoundedMigrationFile.sha256(file, null, "maxDataBytes", "CSV data")));
				}
			} finally {
				connection.rollback();
			}
		} catch (Exception failure) {
			throw new IllegalArgumentException(
					"CSV export failed. No completed manifest was published. Check source keys, "
							+ "supported scalar values, null-token collisions, encoding and SERIALIZABLE transaction support.");
		}
		if (!io.read(jobFile).fingerprint().equals(job.fingerprint()))
			throw new IllegalArgumentException(
					"Transfer definition changed during export. No completed manifest was published.");
		io.write(directory.resolve("transfer.yaml"), job.text(), null);
		return new TransferFilePackageIO().publish(directory, new TransferFilePackageIO.Manifest(1, job.fingerprint(),
				schemaFingerprint, configuration.files(), exported, warnings));
	}

	/** Offline input validation, usable before a runtime pool is constructed. */
	public TransferJobConfigurationIO.Snapshot inspectConfiguration(Path file) throws java.io.IOException {
		final var job = new TransferJobConfigurationIO().read(file);
		if (job.configuration().dataMode() != TransferJobConfiguration.DataMode.FILE
				|| job.configuration().files().format() != TransferJobConfiguration.FileFormat.CSV)
			throw new IllegalArgumentException("This exporter requires FILE dataMode with CSV format.");
		if (job.configuration().mappingFile() != null)
			throw new IllegalArgumentException("File export with explicit mappingFile is not implemented yet.");
		return job;
	}

	private static String scalar(com.sqlapp.data.schemas.Column column, Object value, String nullValue,
			boolean driverText) {
		if (value == null)
			return null;
		final String text;
		if (TransferCsvTemporalCodec.temporal(column) && !driverText)
			text = TransferCsvTemporalCodec.encode(column, value);
		else if (value instanceof String string)
			text = string;
		else if (value instanceof java.math.BigDecimal decimal)
			text = decimal.toPlainString();
		else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
				|| value instanceof java.math.BigInteger || value instanceof Boolean)
			text = value.toString();
		else if (value instanceof Float number && Float.isFinite(number))
			text = number.toString();
		else if (value instanceof Double number && Double.isFinite(number))
			text = number.toString();
		else
			throw new IllegalArgumentException("CSV export does not support this value type.");
		if (text.length() > MAX_FIELD_CHARS)
			throw new IllegalArgumentException("CSV field exceeds the 1 MiB character limit.");
		if (text.equals(nullValue))
			throw new IllegalArgumentException("CSV null-token collision.");
		return text;
	}
}
