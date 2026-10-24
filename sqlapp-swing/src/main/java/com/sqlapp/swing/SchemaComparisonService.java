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

	String compare(final Path original, final Path target) throws IOException {
		final Schema before = read(original);
		final Schema after = read(target);
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
		return text.toString();
	}

	private Schema read(final Path file) throws IOException {
		if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) {
			throw new IllegalArgumentException("Select a readable Schema XML file: " + file);
		}
		final String xml = Files.readString(file);
		validateXml(xml);
		final var model = SchemaUtils.readXml(new StringReader(xml));
		if (!(model instanceof Schema schema)) {
			throw new IllegalArgumentException("Select XML with a single Schema root: " + file);
		}
		if (schema.getTables().stream().anyMatch(table -> !table.getRows().isEmpty())) {
			throw new IllegalArgumentException("Use Schema XML without row data: " + file);
		}
		return schema;
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
