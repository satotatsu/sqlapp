/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds and resolves model references in SQL.
 * <p>
 * A reference is a replacement comment followed by a placeholder identifier,
 * so the file stays executable as ordinary SQL in an editor:
 * </p>
 *
 * <pre>
 * SELECT * FROM /*$ref('stg_customer')*&#47;stg_customer
 * </pre>
 */
public final class MigrationModelReferences {

	private static final String IDENTIFIER = "(?:\"[^\"]*\"|[\\p{L}\\p{N}_$]+)";

	private static final Pattern PATTERN = Pattern.compile("/\\*\\$\\s*(?<kind>ref|source)\\s*\\(\\s*"
			+ "(?<quote>['\"])(?<name>[^'\"]*)\\k<quote>\\s*\\)\\s*\\*/" + IDENTIFIER + "(?:\\." + IDENTIFIER + ")*");

	/** Detects a reference comment that is not followed by a placeholder. */
	private static final Pattern LOOSE_PATTERN = Pattern.compile("/\\*\\$\\s*(?:ref|source)\\s*\\(");

	private MigrationModelReferences() {
	}

	public enum Kind {
		REF, SOURCE
	}

	public record Reference(Kind kind, String name) {
		public Reference {
			Objects.requireNonNull(kind, "kind");
			if (name == null || name.isBlank()) {
				throw new IllegalArgumentException("Reference name must not be empty");
			}
		}
	}

	/** Returns the distinct references in first-appearance order. */
	public static List<Reference> find(final String sql) {
		validate(sql);
		final Set<Reference> references = new LinkedHashSet<>();
		final Matcher matcher = PATTERN.matcher(sql);
		while (matcher.find()) {
			references.add(reference(matcher));
		}
		return List.copyOf(references);
	}

	/** Replaces every reference and its placeholder with the supplied relation. */
	public static String resolve(final String sql, final Function<Reference, String> relation) {
		validate(sql);
		Objects.requireNonNull(relation, "relation");
		final Matcher matcher = PATTERN.matcher(sql);
		final StringBuilder builder = new StringBuilder(sql.length() + 64);
		while (matcher.find()) {
			final Reference reference = reference(matcher);
			final String value = relation.apply(reference);
			if (value == null || value.isBlank()) {
				throw new IllegalArgumentException("Reference was not resolved: " + reference);
			}
			matcher.appendReplacement(builder, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(builder);
		return builder.toString();
	}

	private static Reference reference(final Matcher matcher) {
		return new Reference("ref".equals(matcher.group("kind")) ? Kind.REF : Kind.SOURCE,
				matcher.group("name").trim());
	}

	private static void validate(final String sql) {
		Objects.requireNonNull(sql, "sql");
		final List<int[]> valid = new ArrayList<>();
		final Matcher matcher = PATTERN.matcher(sql);
		while (matcher.find()) {
			valid.add(new int[] { matcher.start(), matcher.end() });
		}
		final Matcher loose = LOOSE_PATTERN.matcher(sql);
		while (loose.find()) {
			final int start = loose.start();
			if (valid.stream().noneMatch(range -> range[0] == start)) {
				final int end = Math.min(sql.length(), start + 60);
				throw new IllegalArgumentException(
						"A model reference must be written as /*$ref('name')*/placeholder or /*$source('name')*/placeholder: "
								+ sql.substring(start, end).replaceAll("\\s+", " "));
			}
		}
	}
}
