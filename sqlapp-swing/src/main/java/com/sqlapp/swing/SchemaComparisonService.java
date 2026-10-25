/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.db.command.GenerateDiffSqlCommand;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.DbCommonObject;

/**
 * Offline adapter; Swing and JDBC execution are deliberately kept outside it.
 */
final class SchemaComparisonService {

	record Result(Path original, Path target, String review, String sqlDraft, int statements) {
	}

	private record Input(Path file, Schema schema, String fingerprint) {
	}

	String compare(final Path original, final Path target) throws IOException {
		return inspect(original, target).review();
	}

	Result inspect(final Path original, final Path target) throws IOException {
		final var beforeInput = read(original);
		final var afterInput = read(target);
		final Schema before = beforeInput.schema();
		final Schema after = afterInput.schema();
		final Dialect dialect = SchemaUtils.getDialect((DbCommonObject<?>) after);
		if (after.getProductName() == null || dialect == null || dialect.getClass() == Dialect.class) {
			throw new IllegalArgumentException("Target Schema must identify a supported database product and version.");
		}
		if (!java.util.Objects.equals(before.getProductName(), after.getProductName())) {
			throw new IllegalArgumentException("Use Schemas from the same database product for a Schema comparison.");
		}
		final var command = new GenerateDiffSqlCommand();
		command.setOriginal(before);
		command.setTarget(after);
		command.run();
		final var text = new StringBuilder("-- sqlapp Schema comparison: review only\n"
				+ "-- Statement delimiters and execution policies must be reviewed before use.\n\n");
		for (final var operation : command.getSqlOperations()) {
			if (operation.getSqlText() != null && !operation.getSqlText().isBlank()) {
				text.append(operation.getSqlText()).append("\n\n");
			}
		}
		if (command.getSqlOperations().isEmpty()) {
			text.append("-- No Schema changes.\n");
		}
		final var writer = new java.io.StringWriter();
		writer.write("-- sqlapp Schema migration SQL draft\n"
				+ "-- Review destructive changes, delimiters, transactions and rollback before use.\n" + "-- Before: "
				+ beforeInput.fingerprint() + "\n-- After: " + afterInput.fingerprint() + "\n\n");
		final var operations = command.getSqlOperations().stream().filter(operation -> operation.getSqlText() != null)
				.toList();
		for (final var operation : operations) {
			final String sql = operation.getSqlText();
			if (sql.isBlank() || operation.getSqlType() != null && !operation.getSqlType().isSql()) {
				writer.write(sql + "\n");
				continue;
			}
			// Serialize the factory's SQL using the dialect's delimiter policy.
			// The generic WriterSqlExecutor always appends ';', even for custom delimiters.
			final var delimiter = dialect.createSqlDelimiter(sql);
			final String start = delimiter.getStartStatementTerminator();
			final String terminator = delimiter.getTerminator() == null || delimiter.getTerminator().isBlank() ? ";"
					: delimiter.getTerminator();
			final String end = delimiter.getEndStatementTerminator();
			if (start != null && !start.isBlank())
				writer.write(start + "\n");
			writer.write(sql + "\n" + terminator.stripTrailing() + "\n");
			if (end != null && !end.isBlank() && !end.strip().equals(terminator.strip())) {
				writer.write(end + "\n");
			}
			writer.write("\n");
		}
		final int statements = (int) operations.stream().filter(operation -> !operation.getSqlText().isBlank()
				&& (operation.getSqlType() == null || operation.getSqlType().isSql())).count();
		if (statements == 0)
			writer.write("-- No Schema changes.\n");
		return new Result(beforeInput.file(), afterInput.file(), text.toString(), writer.toString(), statements);
	}

	void save(final Result result, final Path destination, final boolean sqlDraft) throws IOException {
		java.util.Objects.requireNonNull(result, "result");
		if (sqlDraft && (destination == null || destination.getFileName() == null
				|| !destination.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".sql"))) {
			throw new IllegalArgumentException("Choose a .sql file for the migration SQL draft.");
		}
		if (sqlDraft && result.statements() == 0) {
			throw new IllegalArgumentException("No SQL statements are available to save.");
		}
		new ReviewFileWriter().write(destination, sqlDraft ? result.sqlDraft() : result.review(), result.original(),
				result.target());
	}

	static String defaultSqlDraftName() {
		return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
				+ "_schema_migration_draft.sql";
	}

	Path migrationDirectory(final Result result, final Path savedDraft) throws IOException {
		java.util.Objects.requireNonNull(result, "result");
		if (savedDraft == null || !Files.isRegularFile(savedDraft) || !Files.isReadable(savedDraft)) {
			throw new IllegalArgumentException("Save the SQL draft before selecting its migration directory.");
		}
		final Path file = savedDraft.toAbsolutePath().normalize();
		if (!java.util.Arrays.equals(Files.readAllBytes(file),
				result.sqlDraft().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
			throw new IllegalArgumentException(
					"Saved SQL draft changed. Review and save the displayed draft again before selecting its directory.");
		}
		final var handler = new com.sqlapp.data.db.command.migration.schema.DbVersionFileHandler();
		handler.setUpSqlDirectory(file.getParent().toFile());
		final boolean recognized;
		try {
			recognized = handler.read().stream()
					.anyMatch(sql -> sql.getUpSqlFile().toPath().toAbsolutePath().normalize().equals(file));
		} catch (final NumberFormatException exception) {
			throw new IllegalArgumentException(
					"A SQL filename version exceeds the supported non-negative Java long range. Rename it before selecting this directory.",
					exception);
		}
		if (!recognized) {
			throw new IllegalArgumentException(
					"Save the draft as <numeric version>_<description>.sql, for example 1_create_customer.sql. The version must fit a non-negative Java long.");
		}
		return file.getParent();
	}

	private Input read(final Path file) throws IOException {
		if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) {
			throw new IllegalArgumentException("Select a readable Schema XML file: " + file);
		}
		final byte[] bytes = Files.readAllBytes(file);
		final String xml = java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes))
				.toString();
		validateXml(xml);
		final var model = SchemaUtils.readXml(new StringReader(xml));
		if (!(model instanceof Schema schema)) {
			throw new IllegalArgumentException("Select XML with a single Schema root: " + file);
		}
		if (schema.getTables().stream().anyMatch(table -> !table.getRows().isEmpty())) {
			throw new IllegalArgumentException("Use Schema XML without row data: " + file);
		}
		return new Input(file.toAbsolutePath().normalize(), schema,
				"sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes));
	}

	private void validateXml(final String xml) throws IOException {
		final var factory = XMLInputFactory.newFactory();
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		try {
			final var reader = factory.createXMLStreamReader(new StringReader(xml));
			try {
				while (reader.hasNext()) {
					if (reader.next() == XMLStreamConstants.DTD) {
						throw new IllegalArgumentException("Schema XML must not contain a DOCTYPE.");
					}
				}
			} finally {
				reader.close();
			}
		} catch (final XMLStreamException exception) {
			throw new IOException("Invalid Schema XML: " + exception.getMessage(), exception);
		}
	}
}
