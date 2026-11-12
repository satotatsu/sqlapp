/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

public class Postgres93AlterMviewFactory extends SimpleSqlFactory<Mview, AbstractSqlBuilder<?>> {
	@Override
	public List<SqlOperation> createSql(Mview view) {
		String owner = view.getOwnerName();
		if (owner == null || owner.isBlank() || owner.indexOf('\0') >= 0)
			throw new IllegalArgumentException(
					"Materialized view ownership change requires an existing role name without NUL");
		if (view.getName() == null || view.getName().isBlank() || view.getSchemaName() == null
				|| view.getSchemaName().isBlank())
			throw new IllegalArgumentException("Materialized view ownership change requires a name and schema");
		var sql = createSqlBuilder().alter().materialized().view().space().name(view, true)._add(" OWNER TO \"")
				._add(owner.replace("\"", "\"\""))._add("\"");
		return List.of(new SqlOperation(sql.toString(), SqlType.ALTER, view));
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var changes = difference.getChangedProperties();
		if (changes.isEmpty())
			return List.of();
		if (!Set.of("ownerName", "remarks", "tableSpaceName", "columns", "indexes").containsAll(changes.keySet()))
			throw new UnsupportedOperationException("Unsupported materialized view change");
		var columns = (DbObjectDifferenceCollection) changes.get("columns");
		if (columns != null)
			for (var column : columns.getList(State.Added, State.Deleted, State.Modified))
				requireCommentOnly(column);
		var indexes = (DbObjectDifferenceCollection) changes.get("indexes");
		if (indexes != null)
			for (var index : indexes.getList(State.Modified))
				requireCommentOnly(index);
		var target = difference.getTarget(Mview.class);
		var operations = new ArrayList<SqlOperation>();
		if (changes.containsKey("tableSpaceName")) {
			String tablespace = target.getTableSpaceName();
			if (tablespace == null || tablespace.isBlank())
				throw new IllegalArgumentException(
						"Materialized view tablespace change requires an explicit target tablespace");
			var builder = createSqlBuilder().alter().materialized().view().space().name(target, true)
					._add(" SET TABLESPACE ").name(tablespace);
			addSql(operations, builder, SqlType.ALTER, target);
		}
		if (indexes != null) {
			for (var removed : indexes.getList(State.Deleted))
				operations.addAll(getSqlFactoryRegistry().createSql(removed.getOriginal(), SqlType.DROP));
			for (var added : indexes.getList(State.Added))
				operations.addAll(getSqlFactoryRegistry().createSql(added.getTarget(), SqlType.CREATE));
			for (var modified : indexes.getList(State.Modified)) {
				var index = modified.getTarget(Index.class);
				addSql(operations, indexComment(index, createSqlBuilder(), getOptions().isDecorateSchemaName()),
						SqlType.SET_COMMENT, index);
			}
		}
		if (columns != null)
			for (var modified : columns.getList(State.Modified)) {
				var column = modified.getTarget(Column.class);
				addSql(operations,
						columnComment(target, column, createSqlBuilder(), getOptions().isDecorateSchemaName()),
						SqlType.SET_COMMENT, column);
			}
		if (changes.containsKey("remarks"))
			addSql(operations, comment(target, createSqlBuilder(), getOptions().isDecorateSchemaName()),
					SqlType.SET_COMMENT, target);
		if (changes.containsKey("ownerName"))
			operations.addAll(createSql(target));
		return operations;
	}

	private static void requireCommentOnly(DbObjectDifference difference) {
		if (difference.getState() != State.Modified
				|| !difference.getChangedProperties().keySet().equals(Set.of("remarks")))
			throw new UnsupportedOperationException(
					"Materialized view columns and existing indexes support only comment changes; use an explicit migration for structural changes");
	}

	static AbstractSqlBuilder<?> columnComment(Mview view, Column column, AbstractSqlBuilder<?> builder,
			boolean qualifySchema) {
		builder.comment().on().column().space().name(view, qualifySchema)._add(".").name(column.getName()).is().space();
		return commentValue(builder, column.getRemarks());
	}

	static AbstractSqlBuilder<?> indexComment(Index index, AbstractSqlBuilder<?> builder, boolean qualifySchema) {
		builder.comment().on().index().space().name(index, qualifySchema).is().space();
		return commentValue(builder, index.getRemarks());
	}

	static AbstractSqlBuilder<?> comment(Mview view, AbstractSqlBuilder<?> builder, boolean qualifySchema) {
		builder.comment().on().materialized().view().space().name(view, qualifySchema).is().space();
		return commentValue(builder, view.getRemarks());
	}

	static AbstractSqlBuilder<?> commentValue(AbstractSqlBuilder<?> builder, String value) {
		if (value == null)
			builder._add("NULL");
		else
			builder._add("'")._add(value.replace("'", "''"))._add("'");
		return builder;
	}
}
