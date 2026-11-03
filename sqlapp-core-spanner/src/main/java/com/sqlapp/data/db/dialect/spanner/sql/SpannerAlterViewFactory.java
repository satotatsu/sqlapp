/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.List;
import java.util.ArrayList;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.View;

/**
 * Replaces a same-identity GoogleSQL view without parsing native CREATE text.
 */
public class SpannerAlterViewFactory extends SpannerCreateViewFactory {
	@Override
	public List<SqlOperation> createSql(final View view) {
		if (!view.getDefinition().isEmpty()) {
			throw new UnsupportedOperationException("Cloud Spanner view replacement requires a query statement "
					+ "without raw definition; native CREATE text is not rewritten: " + view.getName());
		}
		final var builder = createSqlBuilder();
		addStatementView(view, builder, true);
		final List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, SqlType.ALTER, view);
		return result;
	}

	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		if (!(difference.getOriginal() instanceof View original) || !(difference.getTarget() instanceof View target)) {
			throw new IllegalArgumentException("Cloud Spanner ALTER VIEW requires original and target View models");
		}
		final var changed = difference.toDifference().getChangedProperties(getDialect());
		for (String property : changed.keySet()) {
			if (!SchemaProperties.STATEMENT.getLabel().equals(property) && !SECURITY_TYPE.equalsIgnoreCase(property)) {
				throw new UnsupportedOperationException(
						"Cloud Spanner view change requires a separate migration: " + property);
			}
		}
		if (changed.isEmpty() || (original.getStatement().equals(target.getStatement())
				&& securityType(original).equals(securityType(target)))) {
			return List.of();
		}
		return createSql(target);
	}
}
