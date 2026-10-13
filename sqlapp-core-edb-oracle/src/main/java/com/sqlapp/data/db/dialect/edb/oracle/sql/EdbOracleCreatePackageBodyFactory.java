/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.sql;

import com.sqlapp.data.db.sql.AbstractCreatePackageBodyFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.PackageBody;

/** Emits a complete executable EPAS package specification. */
public class EdbOracleCreatePackageBodyFactory extends AbstractCreatePackageBodyFactory<PostgresSqlBuilder> {
	@Override protected void addCreateObject(PackageBody object, PostgresSqlBuilder builder) {
		if (object.getDefinition() == null || object.getDefinition().isEmpty()) {
			throw new IllegalArgumentException("EPAS package requires a complete CREATE definition");
		}
		builder._add(object.getDefinition());
	}
}
