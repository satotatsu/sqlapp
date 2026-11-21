/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.ExcludeFilterEqualsHandler;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.properties.GrantableProperty;

/** Upgrades grant option without revoking the original privilege. */
public class InformixGrantOptionFactory extends SimpleSqlFactory<Object, InformixSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Object privilege) {
		throw new IllegalArgumentException("Informix privilege ALTER requires an original/target difference");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var original = difference.getOriginal();
		var target = difference.getTarget();
		if (!(original instanceof GrantableProperty<?> before) || !(target instanceof GrantableProperty<?> after))
			throw new IllegalArgumentException("Informix privilege ALTER requires original and target grants");
		if (original.equals(target))
			return List.of();
		if (before.isGrantable() || !after.isGrantable()
				|| !original.equals(target, new ExcludeFilterEqualsHandler(SchemaProperties.GRANTABLE.getLabel())))
			throw new UnsupportedOperationException(
					"Only a grant-option upgrade can be applied without a grant-chain plan");
		return getSqlFactoryRegistry().createSql(target, SqlType.GRANT);
	}
}
