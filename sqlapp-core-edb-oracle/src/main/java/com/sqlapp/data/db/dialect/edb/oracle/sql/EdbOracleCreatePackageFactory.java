/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.sql;

import com.sqlapp.data.db.sql.AbstractCreatePackageFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.Package;

/** Emits a complete executable EPAS package specification. */
public class EdbOracleCreatePackageFactory extends AbstractCreatePackageFactory<PostgresSqlBuilder> {
	@Override protected void addCreateObject(Package object, PostgresSqlBuilder builder) {
		if (object.getDefinition() == null || object.getDefinition().isEmpty()) {
			throw new IllegalArgumentException("EPAS package requires a complete CREATE definition");
		}
		builder._add(object.getDefinition());
	}
}
