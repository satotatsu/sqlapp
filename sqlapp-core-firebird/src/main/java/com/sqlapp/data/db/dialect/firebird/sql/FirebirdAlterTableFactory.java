/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/**
 * Uses shared table change ordering with Firebird-specific column statements.
 */
public class FirebirdAlterTableFactory extends AbstractAlterTableFactory<FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Table table) {
		throw new IllegalArgumentException("Table ALTER requires original.diff(target)");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Table target = FirebirdAlterSql.target(difference, Table.class,
				Set.of("columns", "constraints", "indexes", "remarks", "specifics"));
		if (target instanceof View)
			throw new IllegalArgumentException("Use the view ALTER factory");
		Table original = (Table) difference.getOriginal();
		var before = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		before.putAll(original.getSpecifics());
		var after = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		after.putAll(target.getSpecifics());
		before.remove("firebird.sqlSecurity");
		after.remove("firebird.sqlSecurity");
		if (!before.equals(after))
			throw new UnsupportedOperationException("Firebird table ALTER cannot change these table-specific options");
		return super.createDiffSql(difference);
	}

	private FirebirdSqlBuilder prefix(Table table) {
		return createSqlBuilder()._add("ALTER TABLE ").name(table.getName());
	}

	private FirebirdSqlBuilder columnPrefix(Table table, String name) {
		return prefix(table)._add(" ALTER COLUMN ").name(name);
	}

	@Override
	protected void addColumnDefinitions(Map<String, Difference<?>> allDiff, Table original, Table table,
			DbObjectDifferenceCollection columns, List<SqlOperation> result) {
		// The shared collection diff can also report a renamed column as deleted and
		// added.
		// A matched modification takes precedence so that its data is retained.
		var modified = columns.getList(State.Modified);
		boolean removed = columns.getList(State.Deleted).stream()
				.anyMatch(d -> modified.stream().noneMatch(m -> m.getOriginal() == d.getOriginal()));
		boolean added = columns.getList(State.Added).stream()
				.anyMatch(d -> modified.stream().noneMatch(m -> m.getTarget() == d.getTarget()));
		if (removed && added)
			throw new IllegalArgumentException(
					"Unmatched column additions and deletions may represent a rename; split renames, type changes and column replacements into separate diffs");
		for (var diff : columns.getList(State.Deleted)) {
			if (modified.stream().noneMatch(m -> m.getOriginal() == diff.getOriginal()))
				addDeleteColumn(original, table, diff, result);
		}
		var changes = new ArrayList<>(modified);
		for (var diff : columns.getList(State.Added)) {
			if (modified.stream().noneMatch(m -> m.getTarget() == diff.getTarget()))
				changes.add(diff);
		}
		sortColumnDiff(changes);
		for (var diff : changes) {
			if (diff.getState() == State.Added)
				addAddColumn(original, table, diff, result);
			else
				addRenameOrAlterColumn(original, table, diff, result);
		}
	}

	@Override
	protected void addAddColumn(Table original, Table table, DbObjectDifference difference, List<SqlOperation> result) {
		Column column = (Column) difference.getTarget();
		addSql(result, prefix(table)._add(" ADD ").name(column.getName()).space().definition(column, false),
				SqlType.ALTER, column);
		if (column.getRemarks() != null)
			result.addAll(getSqlFactoryRegistry().createSql(column, SqlType.SET_COMMENT));
	}

	@Override
	protected void addDeleteColumn(Table original, Table table, DbObjectDifference difference,
			List<SqlOperation> result) {
		Column column = (Column) difference.getOriginal();
		addSql(result, prefix(table)._add(" DROP ").name(column.getName()), SqlType.ALTER, column);
	}

	@Override
	protected void addRenameOrAlterColumn(Table original, Table table, DbObjectDifference difference,
			List<SqlOperation> result) {
		Column column = FirebirdAlterSql.target(difference, Column.class, Set.of("name", "dataType", "dataTypeName",
				"length", "scale", "domainName", "defaultValue", "notNull", "remarks"));
		Column old = (Column) difference.getOriginal();
		if (!Objects.equals(old.getCharacterSet(), column.getCharacterSet())
				|| !Objects.equals(old.getCollation(), column.getCollation())
				|| !old.getSpecifics().equals(column.getSpecifics()))
			throw new UnsupportedOperationException(
					"Firebird column ALTER cannot change charset, collation or vendor-specific properties");
		var changed = difference.getChangedProperties();
		if (changed.containsKey("name"))
			addSql(result, columnPrefix(table, old.getName())._add(" TO ").name(column.getName()), SqlType.ALTER,
					column);
		if (changed.keySet().stream()
				.anyMatch(Set.of("dataType", "dataTypeName", "length", "scale", "domainName")::contains)) {
			if (old.getArrayDimension() > 0 || old.isIdentity() || old.getFormula() != null)
				throw new UnsupportedOperationException(
						"Array, identity and computed column type changes require a dedicated migration");
			if (old.getCollation() != null && !"0".equals(old.getSpecifics().get("firebird.column.collationId")))
				throw new IllegalArgumentException(
						"Column TYPE cannot safely retain a non-default or unverified collation");
			addSql(result, columnPrefix(table, column.getName())._add(" TYPE ").columnType(column), SqlType.ALTER,
					column);
		}
		if (changed.containsKey("defaultValue")) {
			if (getDialect().compareTo(DialectHolder.defaultDialect20) < 0)
				throw new IllegalArgumentException("Column default ALTER requires Firebird 2.0 or later");
			addSql(result, column.getDefaultValue() == null
					? columnPrefix(table, column.getName())._add(" DROP DEFAULT")
					: columnPrefix(table, column.getName())._add(" SET DEFAULT ")._add(column.getDefaultValue()),
					SqlType.ALTER, column);
		}
		if (changed.containsKey("notNull")) {
			if (getDialect().compareTo(DialectHolder.defaultDialect30) < 0)
				throw new IllegalArgumentException("Column nullability ALTER requires Firebird 3.0 or later");
			addSql(result,
					columnPrefix(table, column.getName())._add(column.isNotNull() ? " SET NOT NULL" : " DROP NOT NULL"),
					SqlType.ALTER, column);
		}
		if (changed.containsKey("remarks"))
			result.addAll(getSqlFactoryRegistry().createSql(column, SqlType.SET_COMMENT));
	}

	@Override
	protected void addIndexDefinition(Table original, Table table, Index before, Index after,
			DbObjectDifference difference, List<SqlOperation> result) {
		if (difference.getState() == State.Modified)
			result.addAll(getSqlFactoryRegistry().createSql(difference, SqlType.ALTER));
		else if (difference.getState() == State.Added)
			result.addAll(getSqlFactoryRegistry().createSql(after, SqlType.CREATE));
		else
			super.addIndexDefinition(original, table, before, after, difference, result);
	}

	@Override
	protected void addOtherDefinitions(Map<String, Difference<?>> changed, Table original, Table table,
			List<SqlOperation> result) {
		if (changed.containsKey("remarks"))
			result.addAll(getSqlFactoryRegistry().createSql(table, SqlType.SET_COMMENT));
		String oldSecurity = original.getSpecifics().get("firebird.sqlSecurity");
		String security = table.getSpecifics().get("firebird.sqlSecurity");
		if (!Objects.equals(oldSecurity, security)) {
			if (getDialect().compareTo(DialectHolder.defaultDialect40) < 0)
				throw new IllegalArgumentException("Table SQL SECURITY requires Firebird 4.0 or later");
			var builder = prefix(table);
			if (security == null)
				builder._add(" DROP SQL SECURITY");
			else {
				builder._add(" ALTER");
				FirebirdRoutineSql.addSecurity(table.getSpecifics(), builder);
			}
			addSql(result, builder, SqlType.ALTER, table);
		}
	}
}
