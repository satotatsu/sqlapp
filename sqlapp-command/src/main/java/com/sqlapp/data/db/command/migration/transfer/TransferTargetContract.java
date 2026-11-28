/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Checks that an existing target table accepts every row a declared contract
 * allows without changing a value. The declared table carries the contract
 * types, NOT NULL columns and primary key; the actual table is read from the
 * target. Lengths are compared as numbers in the target's own unit: a target
 * that counts bytes can still reject multi-byte text, which the load detects.
 */
final class TransferTargetContract {
	private static final List<DataType> INTEGERS = List.of(DataType.TINYINT, DataType.SMALLINT, DataType.MEDIUMINT,
			DataType.INT, DataType.BIGINT, DataType.HUGEINT);
	/** Decimal digits that every value of the integer type needs at most. */
	private static final Map<DataType, Integer> INTEGER_DIGITS = Map.of(DataType.TINYINT, 3, DataType.SMALLINT, 5,
			DataType.MEDIUMINT, 7, DataType.INT, 10, DataType.BIGINT, 19, DataType.HUGEINT, 39);
	/** Decimal digits that every integer type can hold. */
	private static final Map<DataType, Integer> INTEGER_CAPACITY = Map.of(DataType.TINYINT, 2, DataType.SMALLINT, 4,
			DataType.MEDIUMINT, 6, DataType.INT, 9, DataType.BIGINT, 18, DataType.HUGEINT, 38);

	private TransferTargetContract() {
	}

	/** Problems found, empty when the target accepts the contract. */
	static List<String> violations(final Table declared, final Table actual) {
		final List<String> problems = new ArrayList<>();
		for (final Column column : declared.getColumns()) {
			final Column existing = column(actual, column.getName());
			if (existing == null) {
				problems.add(column.getName() + ": missing in the target");
				continue;
			}
			final String type = type(column, existing);
			if (type != null) {
				problems.add(column.getName() + ": " + type);
			}
			if (!column.isNotNull() && existing.isNotNull()) {
				problems.add(column.getName() + ": NOT NULL in the target, nullable in the contract");
			}
		}
		for (final Column existing : actual.getColumns()) {
			if (column(declared, existing.getName()) == null && existing.isNotNull()
					&& existing.getDefaultValue() == null && !existing.isIdentity() && existing.getFormula() == null) {
				problems.add(existing.getName() + ": NOT NULL target column without a default is not in the contract");
			}
		}
		final Set<String> key = names(declared.getPrimaryKeyConstraint() == null ? List.of()
				: declared.getPrimaryKeyConstraint().getColumns().stream().map(column -> column.getName()).toList());
		for (final Set<String> unique : uniqueKeys(actual)) {
			if (key.isEmpty() || !unique.containsAll(key)) {
				problems.add("unique key " + unique + " in the target can reject rows that are unique on "
						+ (key.isEmpty() ? "no declared key" : "the declared key " + key));
			}
		}
		return problems;
	}

	private static List<Set<String>> uniqueKeys(final Table table) {
		final List<Set<String>> keys = new ArrayList<>();
		if (table.getPrimaryKeyConstraint() != null) {
			keys.add(names(
					table.getPrimaryKeyConstraint().getColumns().stream().map(column -> column.getName()).toList()));
		}
		table.getConstraints().getUniqueConstraints().stream().filter(unique -> !unique.isPrimaryKey()).forEach(
				unique -> keys.add(names(unique.getColumns().stream().map(column -> column.getName()).toList())));
		table.getIndexes().stream().filter(index -> index.isUnique() && index.getWhere() == null).forEach(
				index -> keys.add(names(index.getColumns().stream().map(column -> column.getName()).toList())));
		return keys.stream().filter(set -> !set.isEmpty()).distinct().toList();
	}

	/**
	 * Why the existing column cannot hold every value of the declared type, or
	 * null.
	 */
	private static String type(final Column declared, final Column existing) {
		final DataType from = declared.getDataType();
		final DataType to = existing.getDataType();
		if (from == null || to == null) {
			return null;
		}
		final String mismatch = text(declared) + " does not fit " + text(existing);
		if (from.isCharacter()) {
			if (!to.isCharacter()) {
				return mismatch;
			}
			return shorter(existing.getLength(), declared.getLength()) ? mismatch : null;
		}
		if (from.isBinary()) {
			if (!to.isBinary()) {
				return mismatch;
			}
			return shorter(existing.getLength(), declared.getLength()) ? mismatch : null;
		}
		if (INTEGERS.contains(from)) {
			if (INTEGERS.contains(to)) {
				return INTEGERS.indexOf(to) >= INTEGERS.indexOf(from) ? null : mismatch;
			}
			if (decimal(to)) {
				return integerDigits(existing) == null || integerDigits(existing) >= INTEGER_DIGITS.get(from) ? null
						: mismatch;
			}
			return mismatch;
		}
		if (decimal(from)) {
			final Integer scale = declared.getScale() == null ? 0 : declared.getScale();
			if (INTEGERS.contains(to)) {
				return scale == 0 && declared.getLength() != null && declared.getLength() <= INTEGER_CAPACITY.get(to)
						? null
						: mismatch;
			}
			if (!decimal(to)) {
				return mismatch;
			}
			if (existing.getLength() == null) {
				return null;
			}
			if (declared.getLength() == null) {
				return mismatch;
			}
			final int targetScale = existing.getScale() == null ? 0 : existing.getScale();
			return targetScale >= scale && integerDigits(existing) >= declared.getLength() - scale ? null : mismatch;
		}
		if (from == DataType.REAL) {
			return to == DataType.REAL || to == DataType.FLOAT || to == DataType.DOUBLE ? null : mismatch;
		}
		if (from == DataType.DOUBLE || from == DataType.FLOAT) {
			return to == DataType.DOUBLE || to == DataType.FLOAT ? null : mismatch;
		}
		if (from == DataType.DATE) {
			return to == DataType.DATE || to == DataType.DATETIME || to == DataType.TIMESTAMP ? null : mismatch;
		}
		if (from == DataType.TIMESTAMP || from == DataType.DATETIME) {
			if (to != DataType.TIMESTAMP && to != DataType.DATETIME) {
				return mismatch;
			}
			return fraction(existing, declared) ? null : mismatch;
		}
		if (from == DataType.TIME) {
			return to == DataType.TIME && fraction(existing, declared) ? null : mismatch;
		}
		return from == to ? null : mismatch;
	}

	private static boolean decimal(final DataType type) {
		return type == DataType.DECIMAL || type == DataType.NUMERIC;
	}

	private static Integer integerDigits(final Column column) {
		return column.getLength() == null ? null
				: (int) (column.getLength() - (column.getScale() == null ? 0 : column.getScale()));
	}

	/** True when the target keeps at least the declared fractional seconds. */
	private static boolean fraction(final Column existing, final Column declared) {
		return existing.getScale() == null || declared.getScale() == null || existing.getScale() >= declared.getScale();
	}

	/** True when a bounded target length is below the declared length. */
	private static boolean shorter(final Long target, final Long declared) {
		return target != null && (declared == null || target < declared);
	}

	private static String text(final Column column) {
		final StringBuilder text = new StringBuilder(column.getDataType().name());
		if (column.getLength() != null) {
			text.append('(').append(column.getLength());
			if (column.getScale() != null && !column.getDataType().isCharacter()) {
				text.append(',').append(column.getScale());
			}
			text.append(')');
		}
		return text.toString();
	}

	private static Column column(final Table table, final String name) {
		final Column exact = table.getColumns().get(name);
		if (exact != null) {
			return exact;
		}
		return table.getColumns().stream().filter(column -> column.getName().equalsIgnoreCase(name)).findFirst()
				.orElse(null);
	}

	private static Set<String> names(final List<String> values) {
		final Set<String> names = new TreeSet<>();
		values.forEach(value -> names.add(value.toUpperCase(Locale.ROOT)));
		return names;
	}
}
