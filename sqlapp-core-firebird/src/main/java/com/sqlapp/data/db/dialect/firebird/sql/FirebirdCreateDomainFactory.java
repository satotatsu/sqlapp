/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Domain;

/**
 * Scalar and array domains use the same type and bounds model as table columns.
 */
public class FirebirdCreateDomainFactory extends AbstractCreateDomainFactory<FirebirdSqlBuilder> {
	@Override
	protected boolean supportsDomainDefinition() {
		return true;
	}

	@Override
	protected void addCreateObject(Domain domain, FirebirdSqlBuilder builder) {
		if (!domain.getDefinition().isEmpty()) {
			builder._add(domain.getDefinition());
			return;
		}
		if (domain.getOnUpdate() != null || domain.getDeferrability() != null
				&& domain.getDeferrability() != com.sqlapp.data.schemas.Deferrability.NotDeferrable)
			throw new IllegalArgumentException("Firebird domains do not support ON UPDATE or deferred constraints");
		builder._add("CREATE DOMAIN ").name(domain.getName())._add(" AS ").typeDefinition(domain);
		if (domain.getCharacterSet() != null)
			builder._add(" CHARACTER SET ").name(domain.getCharacterSet());
		if (domain.getDefaultValue() != null)
			builder._add(" DEFAULT ")._add(domain.getDefaultValue());
		if (domain.isNotNull())
			builder._add(" NOT NULL");
		if (domain.getCheck() != null)
			builder._add(" CHECK (")._add(domain.getCheck())._add(")");
		if (domain.getCollation() != null)
			builder._add(" COLLATE ").name(domain.getCollation());
	}

	@Override
	protected void addOtherDefinitions(Domain domain, java.util.List<SqlOperation> result) {
		if (domain.getRemarks() != null)
			result.addAll(getSqlFactoryRegistry().createSql(domain, SqlType.SET_COMMENT));
	}
}
