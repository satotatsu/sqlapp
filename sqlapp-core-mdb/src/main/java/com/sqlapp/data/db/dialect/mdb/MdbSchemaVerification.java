/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

/**
 * Compares properties represented by the Access writer, independently of row
 * counts.
 */
final class MdbSchemaVerification {
	private MdbSchemaVerification() {
	}

	static List<String> verify(final Schema expected, final Schema observed) {
		final List<String> issues = new ArrayList<>();
		equal(issues, "tables", "count", expected.getTables().size(), observed.getTables().size());
		for (final Table table : expected.getTables()) {
			final Table actual = observed.getTables().get(table.getName());
			if (actual == null) {
				issues.add("Missing table: " + table.getName());
				continue;
			}
			final String location = table.getName();
			equal(issues, location, "column count", table.getColumns().size(), actual.getColumns().size());
			equal(issues, location, "description", table.getRemarks(), actual.getRemarks());
			equal(issues, location, "validation rule", MdbJackcessSupport.tableValidationRule(table),
					MdbJackcessSupport.tableValidationRule(actual));
			for (final Column column : table.getColumns()) {
				final Column found = actual.getColumns().get(column.getName());
				if (found == null) {
					issues.add("Missing column: " + location + "." + column.getName());
					continue;
				}
				column(issues, table, column, found);
			}
			primaryKey(issues, table, actual);
			indexes(issues, table, actual);
			foreignKeys(issues, table, actual);
		}
		equal(issues, "saved queries", "count", expected.getViews().size(), observed.getViews().size());
		for (final Table view : expected.getViews()) {
			if (observed.getViews().get(view.getName()) == null)
				issues.add("Missing saved query: " + view.getName());
		}
		return List.copyOf(issues);
	}

	private static void column(final List<String> issues, final Table table, final Column expected,
			final Column actual) {
		final String location = table.getName() + "." + expected.getName();
		final var nativeType = MdbJackcessSupport.toJackcessType(expected);
		equal(issues, location, "native type", nativeType.name(), actual.getSpecifics().get(MdbFileLoader.SOURCE_TYPE));
		final var primary = table.getPrimaryKeyConstraint();
		final boolean inPrimaryKey = primary != null
				&& primary.getColumns().stream().anyMatch(key -> key.getName().equalsIgnoreCase(expected.getName()));
		equal(issues, location, "NOT NULL", expected.isNotNull() || expected.isIdentity() || inPrimaryKey,
				actual.isNotNull());
		equal(issues, location, "AutoNumber", expected.isIdentity(), actual.isIdentity());
		equal(issues, location, "default", expected.getDefaultValue(), actual.getDefaultValue());
		equal(issues, location, "description", expected.getRemarks(), actual.getRemarks());
		equal(issues, location, "validation rule", expected.getCheck(), actual.getCheck());
		equal(issues, location, "calculated expression", expected.getFormula(), actual.getFormula());
		final boolean sized = nativeType == io.github.spannm.jackcess.DataType.TEXT
				|| nativeType == io.github.spannm.jackcess.DataType.BINARY
				|| nativeType == io.github.spannm.jackcess.DataType.GUID && expected.getDataType().isCharacter()
				|| nativeType.getHasScalePrecision() || nativeType == io.github.spannm.jackcess.DataType.MONEY;
		if (sized && expected.getLength() != null)
			equal(issues, location, "length/precision", expected.getLength(), actual.getLength());
		if ((nativeType.getHasScalePrecision() || nativeType == io.github.spannm.jackcess.DataType.MONEY)
				&& expected.getScale() != null)
			equal(issues, location, "scale", expected.getScale(), actual.getScale());
		final String allowZero = expected.getSpecifics().get(MdbFileLoader.ALLOW_ZERO_LENGTH);
		if (allowZero != null)
			equal(issues, location, "AllowZeroLength", Boolean.valueOf(allowZero),
					actual.getSpecifics().get(MdbFileLoader.ALLOW_ZERO_LENGTH, Boolean.class));
	}

	private static void primaryKey(final List<String> issues, final Table expected, final Table actual) {
		final UniqueConstraint key = expected.getPrimaryKeyConstraint();
		final UniqueConstraint found = actual.getPrimaryKeyConstraint();
		if (key == null || found == null) {
			equal(issues, expected.getName(), "primary key presence", key != null, found != null);
			return;
		}
		if (key.getName() != null)
			equal(issues, expected.getName(), "primary key name", identifier(key.getName()),
					identifier(found.getName()));
		equal(issues, expected.getName(), "primary key columns",
				key.getColumns().stream().map(c -> identifier(c.getName())).toList(),
				found.getColumns().stream().map(c -> identifier(c.getName())).toList());
	}

	private static void indexes(final List<String> issues, final Table expected, final Table actual) {
		final List<Index> indexes = new ArrayList<>(expected.getIndexes());
		for (final UniqueConstraint key : expected.getConstraints().getUniqueConstraints(c -> !c.isPrimaryKey())) {
			final Index index = new Index(key.getName()).setUnique(true).setEnable(key.isEnable());
			for (final var column : key.getColumns())
				index.getColumns().add(column.getName());
			indexes.add(index);
		}
		equal(issues, expected.getName(), "index count", indexes.size(), actual.getIndexes().size());
		for (final Index index : indexes) {
			final String location = expected.getName() + "." + index.getName();
			final Index found = actual.getIndexes().get(index.getName());
			if (found == null) {
				issues.add("Missing index: " + location);
				continue;
			}
			equal(issues, location, "unique", index.isUnique(), found.isUnique());
			equal(issues, location, "enabled", index.isEnable(), found.isEnable());
			equal(issues, location, "columns and order", indexColumns(index), indexColumns(found));
			equal(issues, location, "IgnoreNulls",
					Boolean.parseBoolean(index.getSpecifics().get(MdbFileLoader.INDEX_IGNORE_NULLS)),
					Boolean.parseBoolean(found.getSpecifics().get(MdbFileLoader.INDEX_IGNORE_NULLS)));
		}
	}

	private static List<String> indexColumns(final Index index) {
		return index.getColumns().stream().map(column -> identifier(column.getName()) + ":"
				+ (column.getOrder() == null ? Order.Asc : column.getOrder()).name()).toList();
	}

	private static void foreignKeys(final List<String> issues, final Table expected, final Table actual) {
		final var keys = expected.getConstraints().getForeignKeyConstraints();
		equal(issues, expected.getName(), "foreign key count", keys.size(),
				actual.getConstraints().getForeignKeyConstraints().size());
		for (final ForeignKeyConstraint key : keys) {
			final String location = expected.getName() + "." + key.getName();
			final var matches = actual.getConstraints().getForeignKeyConstraints().stream()
					.filter(candidate -> Objects.equals(identifier(candidate.getRelatedTableName()),
							identifier(key.getRelatedTableName())))
					.filter(candidate -> candidate.getColumns().stream().map(c -> identifier(c.getName())).toList()
							.equals(key.getColumns().stream().map(c -> identifier(c.getName())).toList()))
					.filter(candidate -> candidate.getRelatedColumns().stream().map(c -> identifier(c.getName()))
							.toList()
							.equals(key.getRelatedColumns().stream().map(c -> identifier(c.getName())).toList()))
					.toList();
			final var object = key.getName() != null ? actual.getConstraints().get(key.getName())
					: matches.size() == 1 ? matches.getFirst() : null;
			if (!(object instanceof ForeignKeyConstraint found)) {
				issues.add("Missing foreign key: " + location);
				continue;
			}
			equal(issues, location, "enabled", key.isEnable(), found.isEnable());
			equal(issues, location, "parent table", identifier(key.getRelatedTableName()),
					identifier(found.getRelatedTableName()));
			equal(issues, location, "columns", key.getColumns().stream().map(c -> identifier(c.getName())).toList(),
					found.getColumns().stream().map(c -> identifier(c.getName())).toList());
			equal(issues, location, "parent columns",
					key.getRelatedColumns().stream().map(c -> identifier(c.getName())).toList(),
					found.getRelatedColumns().stream().map(c -> identifier(c.getName())).toList());
			equal(issues, location, "ON DELETE", rule(key.getDeleteRule()), rule(found.getDeleteRule()));
			equal(issues, location, "ON UPDATE", rule(key.getUpdateRule()), rule(found.getUpdateRule()));
		}
	}

	private static CascadeRule rule(final CascadeRule value) {
		return value == null || value == CascadeRule.Restrict ? CascadeRule.None : value;
	}

	private static String identifier(final String value) {
		return value == null ? null : value.toUpperCase(Locale.ROOT);
	}

	private static void equal(final List<String> issues, final String location, final String property,
			final Object expected, final Object actual) {
		if (!Objects.equals(expected, actual))
			issues.add(location + ": " + property + " differs (expected=" + expected + ", actual=" + actual + ")");
	}
}
