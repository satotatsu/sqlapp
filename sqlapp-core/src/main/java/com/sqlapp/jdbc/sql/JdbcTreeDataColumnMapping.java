/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.sql;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Column rules for {@link JdbcTreeDataCopySession#newCopy}, keyed by source
 * column name like transfer and bulk mappings.
 *
 * <p>Without rules, same-name columns are copied. {@link Builder#rename} copies a
 * source column into a differently named target column, {@link Builder#convert}
 * copies the result of an MVEL expression, and {@link Builder#exclude} skips a
 * column. An expression receives {@code value} (this column's source value),
 * {@code column} (the source column name) and {@code row} (a read-only map of the
 * unconverted source values); its result is converted to the target column type,
 * and a non-NULL value that the type cannot represent fails the copy.</p>
 */
public final class JdbcTreeDataColumnMapping {
	private final Map<String, String> names;
	private final Map<String, String> conversions;
	private final Set<String> excluded;

	private JdbcTreeDataColumnMapping(final Builder builder) {
		this.names = Collections.unmodifiableMap(new LinkedHashMap<>(builder.names));
		this.conversions = Collections.unmodifiableMap(new LinkedHashMap<>(builder.conversions));
		this.excluded = Collections.unmodifiableSet(new LinkedHashSet<>(builder.excluded));
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Target column name by source column name for renamed columns. */
	public Map<String, String> getNames() {
		return names;
	}

	/** MVEL expression by source column name. */
	public Map<String, String> getConversions() {
		return conversions;
	}

	/** Source columns that are not copied. */
	public Set<String> getExcluded() {
		return excluded;
	}

	public static final class Builder {
		private final Map<String, String> names = new LinkedHashMap<>();
		private final Map<String, String> conversions = new LinkedHashMap<>();
		private final Set<String> excluded = new LinkedHashSet<>();

		private Builder() {
		}

		/** Copies a source column into a differently named target column. */
		public Builder rename(final String sourceColumn, final String targetColumn) {
			final String previous = names.put(name(sourceColumn), name(targetColumn));
			if (previous != null && !previous.equals(targetColumn)) {
				throw new IllegalArgumentException("Column " + sourceColumn + " is already copied to " + previous
						+ "; one source column has one target column.");
			}
			return this;
		}

		/** Copies the result of an MVEL expression instead of the source value. */
		public Builder convert(final String sourceColumn, final String expression) {
			if (expression == null || expression.isBlank()) {
				throw new IllegalArgumentException("Conversion expression must not be blank: " + sourceColumn);
			}
			conversions.put(name(sourceColumn), expression);
			return this;
		}

		/** Does not copy a source column. */
		public Builder exclude(final String sourceColumn) {
			excluded.add(name(sourceColumn));
			return this;
		}

		public JdbcTreeDataColumnMapping build() {
			for (final String column : excluded) {
				if (names.containsKey(column) || conversions.containsKey(column)) {
					throw new IllegalArgumentException("Excluded column cannot be renamed or converted: " + column);
				}
			}
			return new JdbcTreeDataColumnMapping(this);
		}

		private static String name(final String value) {
			if (value == null || value.isBlank()) {
				throw new IllegalArgumentException("Column name must not be blank");
			}
			return Objects.requireNonNull(value);
		}
	}
}
