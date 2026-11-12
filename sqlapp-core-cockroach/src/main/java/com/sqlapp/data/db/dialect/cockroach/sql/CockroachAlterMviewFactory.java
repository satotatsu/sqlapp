/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.cockroach.util.CockroachPlacement;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres93AlterMviewFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.State;

public class CockroachAlterMviewFactory extends Postgres93AlterMviewFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var changes = difference.getChangedProperties();
		if (changes.isEmpty())
			return List.of();
		if (changes.size() == 1 && changes.containsKey("ownerName"))
			return super.createDiffSql(difference);
		if (changes.containsKey("schemaName"))
			return moveToSchema(difference.getOriginal(Mview.class), difference.getTarget(Mview.class));
		if (changes.containsKey("name")) {
			var original = difference.getOriginal(Mview.class);
			var target = difference.getTarget(Mview.class);
			if (!original.diff(target.clone().setName(original.getName())).getChangedProperties().isEmpty())
				throw new UnsupportedOperationException(
						"CockroachDB materialized view rename requires an isolated name change");
			if (original.getSchemaName() == null || original.getSchemaName().isBlank() || target.getName() == null
					|| target.getName().isBlank())
				throw new IllegalArgumentException(
						"CockroachDB materialized view rename requires a schema and target name");
			var builder = createSqlBuilder().alter().materialized().view().space().name(original, true)
					._add(" RENAME TO ").name(target, false);
			var operations = new ArrayList<SqlOperation>();
			addSql(operations, builder, SqlType.ALTER, target);
			return operations;
		}
		if (changes.size() != 1 || !(changes.get("indexes") instanceof DbObjectDifferenceCollection indexes))
			throw new UnsupportedOperationException(
					"CockroachDB materialized view ALTER supports an isolated rename or index changes");

		var registry = getSqlFactoryRegistry();
		var operations = new ArrayList<SqlOperation>();
		for (var removed : indexes.getList(State.Deleted))
			operations.addAll(registry.createSql(removed.getOriginal(), SqlType.DROP));
		for (var modified : indexes.getList(State.Modified))
			operations.addAll(registry.createSql(modified));
		for (var added : indexes.getList(State.Added))
			operations.addAll(registry.createSql(added.getTarget(), SqlType.CREATE));
		return operations;
	}

	private List<SqlOperation> moveToSchema(Mview original, Mview target) {
		if (!original.diff(target.clone().setSchemaName(original.getSchemaName())).getChangedProperties().isEmpty())
			throw new UnsupportedOperationException(
					"CockroachDB materialized view move requires an isolated schema change");
		if (original.getSchemaName() == null || original.getSchemaName().isBlank() || target.getSchemaName() == null
				|| target.getSchemaName().isBlank())
			throw new IllegalArgumentException("CockroachDB materialized view move requires source and target schemas");
		var builder = createSqlBuilder().alter().materialized().view().space().name(original, true)._add(" SET SCHEMA ")
				._add(CockroachPlacement.quote(target.getSchemaName()));
		var operations = new ArrayList<SqlOperation>();
		addSql(operations, builder, SqlType.ALTER, target);
		return operations;
	}
}
