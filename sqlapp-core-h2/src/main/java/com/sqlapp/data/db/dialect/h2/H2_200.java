/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 */
package com.sqlapp.data.db.dialect.h2;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * H2 2.0.
 */
public class H2_200 extends H2 {

	private static final long serialVersionUID = 1L;

	protected H2_200(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	protected void registerDataType() {
		super.registerDataType();
		getDbDataTypes().getDbType(com.sqlapp.data.db.datatype.DataType.JSON)
				.setJdbcTypeHandler(new H2JsonJdbcTypeHandler());
		getDbDataTypes().addDecimalFloat("DECFLOAT");
		getDbDataTypes().addTimeWithTimeZone(type -> {
			type.setDefaultValueLiteral(getCurrentTimeFunction());
		});
	}

	@Override
	public String getSequenceNextValString(String sequenceName) {
		return "SELECT NEXT VALUE FOR " + sequenceName;
	}

	@Override
	public String getIdentitySelectString() {
		throw new UnsupportedOperationException("H2 2.x requires JDBC generated keys; CALL IDENTITY() was removed");
	}

	@Override
	public boolean supportsMerge() {
		return true;
	}
}
