/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Definition of a GoogleSQL change stream and its native DDL. */
public record SpannerChangeStream(String name, Scope scope, List<Target> targets, Options options) {
	public enum Scope { NONE, ALL, TABLES }
	public enum Capture { OLD_AND_NEW_VALUES, NEW_ROW, NEW_VALUES, NEW_ROW_AND_OLD_VALUES }
	public enum Filter { EXCLUDE_TTL_DELETES, EXCLUDE_INSERT, EXCLUDE_UPDATE, EXCLUDE_DELETE, ALLOW_TXN_EXCLUSION }

	/** A null column list tracks the entire table. An empty list tracks only its primary key. */
	public record Target(String schema, String table, List<String> columns) {
		public Target {
			checkName(table, 128);
			if (schema != null && !schema.isEmpty())
				checkName(schema, 128);
			if (columns != null) {
				columns = List.copyOf(columns);
				final var names = new HashSet<String>();
				for (String column : columns) {
					checkName(column, 128);
					if (!names.add(column.toLowerCase(Locale.ROOT)))
						throw new IllegalArgumentException("Duplicate change stream column: " + column);
				}
			}
		}

		private String sql() {
			final String qualified = schema == null || schema.isEmpty() ? quote(table) : quote(schema) + "." + quote(table);
			return columns == null ? qualified
					: qualified + "(" + String.join(", ", columns.stream().map(SpannerChangeStream::quote).toList()) + ")";
		}
	}

	public record Options(Duration retention, Capture capture, Map<Filter, Boolean> filters) {
		public Options {
			filters = Map.copyOf(filters);
			if (retention != null && (retention.getNano() != 0 || retention.compareTo(Duration.ofDays(1)) < 0
					|| retention.compareTo(Duration.ofDays(30)) > 0))
				throw new IllegalArgumentException("Change stream retention must be whole seconds between 1 and 30 days");
		}

		private List<String> assignments() {
			final var values = new ArrayList<String>();
			if (retention != null)
				values.add("retention_period = '" + retention.toSeconds() + "s'");
			if (capture != null)
				values.add("value_capture_type = '" + capture.name() + "'");
			for (Filter filter : Filter.values()) {
				if (filters.containsKey(filter))
					values.add(filter.name().toLowerCase(Locale.ROOT) + " = " + filters.get(filter));
			}
			return values;
		}
	}

	public SpannerChangeStream {
		checkName(name, 118);
		Objects.requireNonNull(scope, "Change stream scope");
		targets = List.copyOf(targets);
		if ((scope == Scope.TABLES) != !targets.isEmpty())
			throw new IllegalArgumentException("Only TABLES scope requires a nonempty list of targets");
		final var names = new HashSet<String>();
		for (Target target : targets) {
			final String identity = Objects.toString(target.schema(), "") + "." + target.table();
			if (!names.add(identity.toLowerCase(Locale.ROOT)))
				throw new IllegalArgumentException("Duplicate change stream target: " + identity);
		}
	}

	public String createSql(final boolean ifNotExists) {
		final var assignments = options == null ? List.<String>of() : options.assignments();
		return "CREATE CHANGE STREAM " + (ifNotExists ? "IF NOT EXISTS " : "") + quote(name)
				+ (scope == Scope.NONE ? "" : " FOR " + watchedObjects())
				+ (assignments.isEmpty() ? "" : " OPTIONS (" + String.join(", ", assignments) + ")");
	}

	public String dropSql(final boolean ifExists) {
		return "DROP CHANGE STREAM " + (ifExists ? "IF EXISTS " : "") + quote(name);
	}

	/** Applies an explicitly selected tracking scope without changing stream options. */
	public String setScopeSql() {
		return "ALTER CHANGE STREAM " + quote(name)
				+ (scope == Scope.NONE ? " DROP FOR ALL" : " SET FOR " + watchedObjects());
	}

	/** Sets the supplied options. Omitted options keep their current values. */
	public String setOptionsSql() {
		final var assignments = options == null ? List.<String>of() : options.assignments();
		if (assignments.isEmpty())
			throw new IllegalArgumentException("ALTER CHANGE STREAM requires at least one option");
		return "ALTER CHANGE STREAM " + quote(name) + " SET OPTIONS (" + String.join(", ", assignments) + ")";
	}

	private String watchedObjects() {
		return scope == Scope.ALL ? "ALL" : String.join(", ", targets.stream().map(Target::sql).toList());
	}

	private static void checkName(final String name, final int maxLength) {
		if (name == null || name.length() > maxLength || !name.matches("[A-Za-z][A-Za-z0-9_]*"))
			throw new IllegalArgumentException("Invalid GoogleSQL identifier: " + name);
	}

	private static String quote(final String name) {
		return "`" + name + "`";
	}
}
