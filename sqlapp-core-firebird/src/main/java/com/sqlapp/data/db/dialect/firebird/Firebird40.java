/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;

/** Firebird 4.0 types, retaining the compatible 3.0 metadata readers. */
public class Firebird40 extends Firebird30 {
	@Override
	public com.sqlapp.data.db.metadata.CatalogReader getCatalogReader() {
		return new com.sqlapp.data.db.dialect.firebird.metadata.Firebird40CatalogReader(this);
	}

	private static final long serialVersionUID = 1L;

	protected Firebird40(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	protected void registerDataType() {
		super.registerDataType();
		getDbDataTypes().addDecimal(
				type -> type.setDefaultPrecision(18).setDefaultScale(5).setMaxPrecision(38).setMaxScale(38));
		getDbDataTypes().addNumeric(
				type -> type.setDefaultPrecision(18).setDefaultScale(5).setMaxPrecision(38).setMaxScale(38));
		getDbDataTypes().addDecimalFloat("DECFLOAT");
		getDbDataTypes().addTimeWithTimeZone("TIME WITH TIME ZONE",
				type -> type.setCreateFormat("TIME WITH TIME ZONE"));
		getDbDataTypes().addTimestampWithTimeZone("TIMESTAMP WITH TIME ZONE",
				type -> type.setCreateFormat("TIMESTAMP WITH TIME ZONE"));
	}
}
