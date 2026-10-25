/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.schema;

import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;

/**
 * Offline statement/batch boundary preview. This is not SQL syntax validation.
 */
public class MigrationSqlInspection {
	public record Part(int number, boolean comment, String text) {
		public Part {
			Objects.requireNonNull(text, "text");
		}
	}

	public record Result(String product, String version, String resolvedDialect, List<Part> parts) {
		public Result {
			parts = List.copyOf(parts);
		}

		public long sqlParts() {
			return parts.stream().filter(part -> !part.comment()).count();
		}

		public long commentParts() {
			return parts.stream().filter(Part::comment).count();
		}
	}

	/**
	 * Resolves an explicitly selected product/version and returns the shared
	 * splitter's output unchanged.
	 */
	public Result inspect(final String text, final String product, final String version) {
		Objects.requireNonNull(text, "text");
		if (!java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(text))
			throw new IllegalArgumentException("SQL preview text must contain valid UTF-8 characters.");
		if (text.length() > 1_048_576 || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1_048_576)
			throw new IllegalArgumentException("SQL preview text must not exceed 1 MiB.");
		if (product == null || product.isBlank())
			throw new IllegalArgumentException("Select a database product for SQL splitting.");
		if (version == null || !version.trim().matches("[0-9]+(?:\\.[0-9]+){0,2}"))
			throw new IllegalArgumentException(
					"Enter the database version as major, major.minor or major.minor.revision.");
		final String[] values = version.trim().split("\\.");
		final int major, minor, revision;
		try {
			major = Integer.parseInt(values[0]);
			minor = values.length > 1 ? Integer.parseInt(values[1]) : 0;
			revision = values.length > 2 ? Integer.parseInt(values[2]) : 0;
		} catch (final NumberFormatException exception) {
			throw new IllegalArgumentException("Database version components must fit a non-negative Java integer.",
					exception);
		}
		final Dialect dialect = DialectResolver.getInstance().getDialect(product.trim(), major, minor, revision);
		if (dialect == null || dialect.getClass() == Dialect.class)
			throw new IllegalArgumentException(
					"No supported SQL dialect is available for the selected product/version.");
		final var parts = new java.util.ArrayList<Part>();
		for (final var split : dialect.createSqlSplitter().parse(text)) {
			if (split.getText() != null && !split.getText().isBlank())
				parts.add(new Part(parts.size() + 1, split.getTextType().isComment(), split.getText()));
		}
		return new Result(product.trim(), version.trim(), dialect.getClass().getSimpleName(), parts);
	}
}
