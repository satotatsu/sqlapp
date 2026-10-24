/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** ALTER VIEW retains the existing view and its grants. */
public class FirebirdAlterViewFactory extends SimpleSqlFactory<View, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(View view) {
		if (getDialect().compareTo(DialectHolder.defaultDialect25) < 0)
			throw new IllegalArgumentException("ALTER VIEW requires Firebird 2.5 or later");
		var create = getSqlFactoryRegistry().createSql(view, SqlType.CREATE);
		String ddl = create.get(0).getSqlText();
		if (!ddl.matches("(?is)^\\s*CREATE\\s+VIEW\\b.*"))
			throw new IllegalArgumentException("View ALTER requires a CREATE VIEW definition or modeled statement");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, createSqlBuilder()._add(ddl.replaceFirst("(?is)^\\s*CREATE\\s+VIEW\\b", "ALTER VIEW")),
				SqlType.ALTER, view);
		result.addAll(create.subList(1, create.size()));
		return result;
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		View target = FirebirdAlterSql.target(difference, View.class, Set.of("statement", "columns", "remarks"));
		View original = (View) difference.getOriginal();
		if (getDialect().compareTo(DialectHolder.defaultDialect30) < 0
				&& String.join("\n", original.getStatement()).matches("(?is).*\\bWITH\\s+CHECK\\s+OPTION\\b.*")
				&& !original.getColumns().stream().map(Column::getName).toList()
						.equals(target.getColumns().stream().map(Column::getName).toList()))
			throw new UnsupportedOperationException(
					"Firebird view column changes with an existing CHECK OPTION require an explicit dependency-aware migration");
		var changed = difference.getChangedProperties();
		boolean structural = changed.containsKey("statement");
		List<Column> comments = new ArrayList<>();
		if (changed.get("columns") instanceof DbObjectDifferenceCollection columns) {
			for (var column : columns.getList()) {
				if (column.getTarget() == null) {
					structural = true;
					continue;
				}
				Column after = (Column) column.getTarget();
				if (column.getOriginal() == null) {
					structural = true;
					if (after.getRemarks() != null)
						comments.add(after);
					continue;
				}
				FirebirdAlterSql.target(column, Column.class, Set.of("name", "remarks"));
				if (column.getChangedProperties().containsKey("name"))
					structural = true;
				if (column.getChangedProperties().containsKey("remarks"))
					comments.add(after);
			}
		}
		if (structural && !target.getDefinition().isEmpty())
			throw new IllegalArgumentException("A complete view definition shadows modeled ALTER changes");
		List<SqlOperation> result = new ArrayList<>();
		if (structural) {
			result.addAll(createSql(target));
			for (Column column : target.getColumns())
				if (column.getRemarks() == null)
					result.addAll(getSqlFactoryRegistry().createSql(column, SqlType.SET_COMMENT));
		}
		for (Column column : comments)
			if (!structural)
				result.addAll(getSqlFactoryRegistry().createSql(column, SqlType.SET_COMMENT));
		if (changed.containsKey("remarks") && (!structural || target.getRemarks() == null))
			result.addAll(getSqlFactoryRegistry().createSql(target, SqlType.SET_COMMENT));
		return result;
	}
}
