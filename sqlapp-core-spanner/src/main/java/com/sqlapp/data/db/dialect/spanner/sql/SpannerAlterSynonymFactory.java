/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.SimpleAlterSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Synonym;

/** Replaces a modeled GoogleSQL table synonym using native DROP and ADD. */
public class SpannerAlterSynonymFactory extends SimpleAlterSqlFactory<Synonym, SpannerSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(final Synonym synonym) {
		throw new UnsupportedOperationException("Spanner ALTER SYNONYM requires original and target synonym models");
	}

	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		if (!(difference.getOriginal() instanceof Synonym original)
				|| !(difference.getTarget() instanceof Synonym target))
			throw new IllegalArgumentException("Spanner ALTER SYNONYM requires original and target synonym models");
		final var changes = difference.toDifference().getChangedProperties(getDialect());
		for (final String property : changes.keySet()) {
			if (!SchemaProperties.NAME.getLabel().equals(property)
					&& !SchemaProperties.OBJECT_NAME.getLabel().equals(property)
					&& !SchemaProperties.OBJECT_SCHEMA_NAME.getLabel().equals(property))
				throw new UnsupportedOperationException("Spanner synonym change requires a separate migration: " + property);
		}
		if (changes.isEmpty())
			return List.of();
		final var registry = getSqlFactoryRegistry();
		// Generate and validate both sides before returning an executable plan.
		final var before = registry.createSql(original, SqlType.CREATE);
		final var after = registry.createSql(target, SqlType.CREATE);
		if (before.getFirst().getSqlText().equals(after.getFirst().getSqlText()))
			return List.of();
		final List<SqlOperation> result = new ArrayList<>(registry.createSql(original, SqlType.DROP));
		result.addAll(after);
		return result;
	}
}
