/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 */
package com.sqlapp.data.db.dialect.saphana;

import java.util.function.Supplier;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.saphana.sql.SapHanaCloudSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

/**
 * SAP HANA Cloud (database major version 4).
 */
public class SapHanaCloud extends SapHana {

	private static final long serialVersionUID = 1L;

	protected SapHanaCloud(
			final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	protected void registerDataType() {
		super.registerDataType();
		getDbDataTypes().addJsonType(type -> {
		});
		getDbDataTypes().addVector();
		getDbDataTypes().getDbTypeStrict(DataType.VECTOR)
				.addPetternColumnTypeMatcher(
						"REAL_VECTOR(?:\\s*\\(\\s*\\d+\\s*\\))?");
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new SapHanaCloudSqlFactoryRegistry(this);
	}
}
