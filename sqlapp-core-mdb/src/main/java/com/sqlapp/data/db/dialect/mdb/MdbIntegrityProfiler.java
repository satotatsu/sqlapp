/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.*;
import com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile.*;

import io.github.spannm.jackcess.Database;

/** Bounded, exact scalar checks driven by the canonical Schema. No index or
 * target collation assumptions and no key values in the result. */
final class MdbIntegrityProfiler {
	static final int MAX_DISTINCT_KEYS = 100_000;
	private static final Set<String> NUMBERS = Set.of("BYTE", "INT", "LONG", "BIG_INT", "MONEY", "NUMERIC");
	private static final Set<String> DATES = Set.of("SHORT_DATE_TIME", "EXT_DATE_TIME");
	private MdbIntegrityProfiler() { }
	record Result(List<IntegrityCheck> checks, List<Finding> findings) { }
	private record Spec(Table table, String name, IntegrityKind kind, List<Column> columns,
			Table parent, List<Column> parentColumns) { }

	static Result scan(final Database database, final Schema schema, final int limit) throws IOException {
		if (limit < 1) { throw new IllegalArgumentException("Distinct-key limit must be positive"); }
		final var specs = new ArrayList<Spec>();
		for (final var table : schema.getTables()) {
			for (final var key : table.getConstraints().getUniqueConstraints()) {
				specs.add(new Spec(table, key.getName(), key.isPrimaryKey() ? IntegrityKind.PRIMARY_KEY : IntegrityKind.UNIQUE_KEY,
						key.getColumns().stream().map(c -> table.getColumns().get(c.getName())).toList(), null, List.of()));
			}
			for (final var index : table.getIndexes()) {
				if (index.isUnique()) {
					specs.add(new Spec(table, index.getName(), IntegrityKind.UNIQUE_KEY,
							index.getColumns().stream().map(c -> table.getColumns().get(c.getName())).toList(), null, List.of()));
				}
			}
			for (final var key : table.getConstraints().getForeignKeyConstraints()) {
				final var parent = key.getRelatedTable();
				specs.add(new Spec(table, key.getName(), IntegrityKind.FOREIGN_KEY, new ArrayList<>(key.getColumns()), parent,
						key.getRelatedColumns().stream().map(c -> parent == null ? null : parent.getColumns().get(c.getName())).toList()));
			}
		}
		final var checks = new ArrayList<IntegrityCheck>();
		final var findings = new ArrayList<Finding>();
		for (final var spec : specs) {
			final var check = check(database, schema, spec, limit, findings);
			checks.add(check);
			if (check.coverage() != IntegrityCoverage.CHECKED) {
				findings.add(new Finding("access.data.integrity-not-checked", Severity.REVIEW, Evidence.MANUAL_CHECK,
						check.object(), check.reason(), "Verify this key separately with explicit comparison and NULL semantics.", null));
			} else {
				addObservedFindings(spec, check, check.violationRows(), check.nullRows(), false, findings);
			}
		}
		return new Result(List.copyOf(checks), List.copyOf(findings));
	}

	private static void addObservedFindings(final Spec spec, final IntegrityCheck check, final long violations,
			final long nulls, final boolean partial, final List<Finding> findings) {
		final String prefix = partial ? "At least " : "";
		final String suffix = partial ? " The key limit stopped this check; these are observed lower bounds." : "";
		if (violations > 0) {
			final boolean foreign = spec.kind() == IntegrityKind.FOREIGN_KEY;
			findings.add(new Finding(foreign ? "access.data.orphan-key" : "access.data.duplicate-key",
					Severity.BLOCKER, Evidence.DATABASE, check.object(), prefix + violations
							+ (foreign ? " rows have non-NULL keys without a matching parent." : " duplicate non-NULL key rows beyond the first were observed.") + suffix,
					"Resolve the data or explicitly revise the key mapping before loading.", null));
		}
		if (spec.kind() == IntegrityKind.PRIMARY_KEY && nulls > 0) {
			findings.add(new Finding("access.data.primary-key-null", Severity.BLOCKER, Evidence.DATABASE,
					check.object(), prefix + nulls + " rows have a NULL primary-key component." + suffix,
					"Resolve missing primary-key values before loading.", null));
		}
	}

	private static IntegrityCheck check(final Database database, final Schema schema, final Spec spec, final int limit,
			final List<Finding> findings) throws IOException {
		if (!supported(spec.columns())) {
			return result(schema, spec, IntegrityCoverage.UNSUPPORTED, null,
					"Keys require 1 to 32 resolved columns of exact numeric, local date/time, GUID or Boolean types; text collation, floating point and other types are not checked.");
		}
		if (spec.kind() == IntegrityKind.FOREIGN_KEY) {
			if (spec.parent() == null || spec.parent().getSchema() != schema || !supported(spec.parentColumns())
					|| spec.columns().size() != spec.parentColumns().size()) {
				return result(schema, spec, IntegrityCoverage.UNSUPPORTED, null, "The parent key is unresolved, outside the local schema or has unsupported columns.");
			}
			for (int i = 0; i < spec.columns().size(); i++) {
				if (!family(spec.columns().get(i)).equals(family(spec.parentColumns().get(i)))) {
					return result(schema, spec, IntegrityCoverage.UNSUPPORTED, null, "Parent and child key types require an explicit conversion before comparison.");
				}
			}
		}
		final Set<List<Object>> keys = new HashSet<>();
		final long[] counts = new long[3]; // non-NULL rows, rows with any NULL, violation rows
		try {
			if (spec.kind() == IntegrityKind.FOREIGN_KEY) {
				read(database, spec.parent(), spec.parentColumns(), key -> { if (key != null) { add(keys, key, limit); } });
			}
			read(database, spec.table(), spec.columns(), key -> {
				if (key == null) { counts[1]++; return; }
				counts[0]++;
				if (spec.kind() == IntegrityKind.FOREIGN_KEY) {
					if (!keys.contains(key)) { counts[2]++; }
				} else if (!add(keys, key, limit)) { counts[2]++; }
			});
		} catch (final KeyLimitExceeded e) {
			final var incomplete = result(schema, spec, IntegrityCoverage.LIMIT_EXCEEDED, null,
					"The limit of " + limit + " distinct keys per check was exceeded; no complete counts are reported.");
			addObservedFindings(spec, incomplete, counts[2], counts[1], true, findings);
			return incomplete;
		}
		return result(schema, spec, IntegrityCoverage.CHECKED, counts,
				"Exact scalar comparison without target conversions. Rows with any NULL key component are counted separately and excluded from duplicate/orphan counts; target nullable uniqueness remains unverified.");
	}

	private static boolean add(final Set<List<Object>> keys, final List<Object> key, final int limit) throws KeyLimitExceeded {
		if (keys.contains(key)) { return false; }
		if (keys.size() >= limit) { throw new KeyLimitExceeded(); }
		return keys.add(key);
	}
	private static final class KeyLimitExceeded extends IOException { private static final long serialVersionUID = 1L; }
	@FunctionalInterface private interface KeyConsumer { void accept(List<Object> key) throws IOException; }
	private static void read(final Database database, final Table table, final List<Column> columns,
			final KeyConsumer consumer) throws IOException {
		final var metadata = database.getTableMetaData(table.getName());
		if (metadata == null || metadata.isLinked() || metadata.isSystem()) {
			throw new IOException("Integrity scan requires a local table: " + table.getName());
		}
		final var cursor = metadata.open(database).newCursor().toCursor();
		final var names = columns.stream().map(Column::getName).toList();
		io.github.spannm.jackcess.Row row;
		while ((row = cursor.getNextRow(names)) != null) {
			final var values = new ArrayList<Object>();
			boolean hasNull = false;
			for (final var column : columns) {
				final Object value = row.get(column.getName());
				if (value == null) { hasNull = true; break; }
				values.add(normalize(column, value));
			}
			consumer.accept(hasNull ? null : List.copyOf(values));
		}
	}
	private static Object normalize(final Column column, final Object value) throws IOException {
		final String type = type(column);
		if (NUMBERS.contains(type) && value instanceof Number number) {
			return ("BYTE".equals(type) && number instanceof Byte b ? BigDecimal.valueOf(Byte.toUnsignedInt(b))
					: number instanceof BigDecimal decimal ? decimal : new BigDecimal(number.toString())).stripTrailingZeros();
		}
		if (DATES.contains(type) && value instanceof LocalDateTime || "BOOLEAN".equals(type) && value instanceof Boolean) { return value; }
		if ("GUID".equals(type) && value instanceof String text) {
			try { return UUID.fromString(text.startsWith("{") && text.endsWith("}") ? text.substring(1, text.length() - 1) : text); }
			catch (final IllegalArgumentException e) { throw new IOException("Invalid GUID key; no raw value is included"); }
		}
		throw new IOException("Unexpected integrity key type for column " + column.getName());
	}
	private static String type(final Column column) { return column.getSpecifics().get(MdbFileLoader.SOURCE_TYPE, String.class); }
	private static String family(final Column column) {
		final String type = type(column);
		if (type == null) { return "unsupported"; }
		return NUMBERS.contains(type) ? "number" : DATES.contains(type) ? "date" : type;
	}
	private static boolean supported(final List<Column> columns) {
		return !columns.isEmpty() && columns.size() <= 32 && columns.stream().allMatch(c -> c != null
				&& Set.of("number", "date", "GUID", "BOOLEAN").contains(family(c)));
	}
	private static IntegrityCheck result(final Schema schema, final Spec spec, final IntegrityCoverage coverage,
			final long[] counts, final String reason) {
		return new IntegrityCheck(new ObjectId(schema.getCatalogName(), schema.getName(),
				spec.kind() == IntegrityKind.FOREIGN_KEY ? "foreignKey" : "key", spec.name(), spec.table().getName()),
				spec.kind(), coverage, spec.columns().stream().map(c -> c == null ? "<unresolved>" : c.getName()).toList(),
				spec.parent() == null ? null : new ObjectId(spec.parent().getCatalogName(), spec.parent().getSchemaName(), "table", spec.parent().getName()),
				spec.parentColumns().stream().map(c -> c == null ? "<unresolved>" : c.getName()).toList(),
				counts == null ? null : counts[0], counts == null ? null : counts[1], counts == null ? null : counts[2], reason);
	}
}
