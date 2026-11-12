/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.cockroach.util.CockroachPlacement;
import com.sqlapp.data.db.dialect.cockroach.util.CockroachSequenceOptions;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Changes sequence identity or configuration without restarting it. */
public class CockroachAlterSequenceFactory extends SimpleSqlFactory<Sequence, PostgresSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Sequence sequence) {
		throw new UnsupportedOperationException("Sequence ALTER requires original.diff(target)");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var original = difference.getOriginal(Sequence.class);
		var target = difference.getTarget(Sequence.class);
		if (target.isCycle())
			throw new UnsupportedOperationException("CockroachDB CYCLE is not implemented");
		var changed = new HashSet<>(difference.getChangedProperties().keySet());
		if (changed.remove(SchemaProperties.SCHEMA_NAME.getLabel())) {
			if (!changed.isEmpty() || !Objects.equals(original.getCatalogName(), target.getCatalogName()))
				throw new UnsupportedOperationException("Move a CockroachDB sequence in a separate same-database diff");
			if (original.getSchemaName() == null || original.getSchemaName().isBlank() || target.getSchemaName() == null
					|| target.getSchemaName().isBlank())
				throw new IllegalArgumentException("CockroachDB sequence moves require source and target schema names");
			var move = createSqlBuilder().alter().sequence().space().name(original, true)._add(" SET SCHEMA ")
					._add(CockroachPlacement.quote(target.getSchemaName()));
			var result = new ArrayList<SqlOperation>();
			addSql(result, move, SqlType.ALTER, target);
			return result;
		}
		var builder = createSqlBuilder().alter().sequence().space().name(original, getOptions().isDecorateSchemaName());
		if (changed.contains(SchemaProperties.DEFINITION.getLabel())) {
			var before = ownership(original);
			var after = ownership(target);
			if (after.ownerSql() != null && !Objects.equals(before.ownerSql(), after.ownerSql())) {
				if (changed.size() != 1 || !before.definitionWithoutOwner().equals(after.definitionWithoutOwner()))
					throw new UnsupportedOperationException(
							"Change CockroachDB sequence ownership in a separate definition diff");
				var result = new ArrayList<SqlOperation>();
				builder._add(" OWNED BY ")._add(after.ownerSql());
				addSql(result, builder, SqlType.ALTER, target);
				return result;
			}
		}
		if (changed.remove(SchemaProperties.NAME.getLabel())) {
			if (!changed.isEmpty() || !Objects.equals(original.getSchemaName(), target.getSchemaName())
					|| !Objects.equals(original.getCatalogName(), target.getCatalogName()))
				throw new UnsupportedOperationException("Rename a CockroachDB sequence in a separate same-schema diff");
			var result = new ArrayList<SqlOperation>();
			builder._add(" RENAME TO ").name(target, false);
			addSql(result, builder, SqlType.ALTER, target);
			return result;
		}
		boolean options = false;
		for (var property : List.of(SchemaProperties.INCREMENT_BY, SchemaProperties.MIN_VALUE,
				SchemaProperties.MAX_VALUE, SchemaProperties.START_VALUE, SchemaProperties.CYCLE,
				SchemaProperties.CACHE_SIZE)) {
			if (!changed.remove(property.getLabel()))
				continue;
			options = true;
			switch (property) {
			case INCREMENT_BY -> builder._add(" INCREMENT BY ")._add(target.getIncrementBy());
			case MIN_VALUE -> builder._add(" MINVALUE ")._add(target.getMinValue());
			case MAX_VALUE -> builder._add(" MAXVALUE ")._add(target.getMaxValue());
			case START_VALUE -> builder._add(" START WITH ")._add(target.getStartValue());
			case CYCLE -> builder._add(target.isCycle() ? " CYCLE" : " NO CYCLE");
			case CACHE_SIZE -> {
				Integer cache = target.getCacheSize();
				if (cache == null || cache < 1)
					throw new IllegalArgumentException(
							"CockroachDB sequence CACHE must be positive; use 1 to disable caching");
				String keyword = cacheKeyword(original);
				if (!keyword.equals(cacheKeyword(target)))
					throw new UnsupportedOperationException(
							"Changing CockroachDB sequence cache scope requires explicit SQL");
				builder._add(" ")._add(keyword)._add(" ")._add(cache);
			}
			default -> throw new IllegalStateException();
			}
		}
		if (options)
			changed.remove(SchemaProperties.DEFINITION.getLabel());
		if (!changed.isEmpty())
			throw new UnsupportedOperationException("Unsupported CockroachDB sequence changes: " + changed
					+ "; sequence restart requires an explicit migration");
		var result = new ArrayList<SqlOperation>();
		if (options)
			addSql(result, builder, SqlType.ALTER, target);
		return result;
	}

	private static String cacheKeyword(Sequence sequence) {
		return sequence.getDefinition() == null ? "CACHE"
				: CockroachSequenceOptions.cache(String.join("\n", sequence.getDefinition())).keyword();
	}

	private static CockroachSequenceOptions.Ownership ownership(Sequence sequence) {
		return CockroachSequenceOptions
				.ownership(sequence.getDefinition() == null ? "" : String.join("\n", sequence.getDefinition()));
	}
}
