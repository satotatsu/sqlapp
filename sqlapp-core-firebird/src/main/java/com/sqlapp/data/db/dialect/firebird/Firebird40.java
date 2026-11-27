/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import java.util.function.Supplier;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DecimalFloatType;
import com.sqlapp.data.db.datatype.util.LengthColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;

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
	public boolean setDbType(String productDataType, Long lengthOrPrecision, Integer scale,
			DataTypeLengthProperties<?> column) {
		boolean matched = super.setDbType(productDataType, lengthOrPrecision, scale, column);
		if (matched && "INT128".equalsIgnoreCase(productDataType.trim())) {
			column.setDataTypeName("INT128");
			column.setLength(38L);
			column.setScale(0);
		}
		return matched;
	}

	@Override
	protected void registerDataType() {
		super.registerDataType();
		getDbDataTypes().addDecimal(type -> {
			type.setDefaultPrecision(9).setDefaultScale(0).setMaxPrecision(38).setMaxScale(38);
			type.addColumnTypeMatcher(new RegexColumnTypeMatcher("INT128", (matcher, information) -> {
				information.setDataTypeName("INT128");
				information.setLength(38L);
			}));
		});
		getDbDataTypes()
				.addNumeric(type -> type.setDefaultPrecision(9).setDefaultScale(0).setMaxPrecision(38).setMaxScale(38));
		getDbDataTypes().addDecimalFloat("DECFLOAT");
		DecimalFloatType.class.cast(getDbDataTypes().getDbType(DataType.DECIMALFLOAT)).setDefaultPrecision(34)
				.setMaxPrecision(34);
		getDbDataTypes().addBinary("BINARY", 32767, type -> {
		});
		getDbDataTypes().addVarBinary("VARBINARY", 32765, type -> {
			type.addColumnTypeMatcher(new LengthColumnTypeMatcher("BINARY\\s+VARYING", ""));
		});
		getDbDataTypes().getDbType(DataType.REAL).clearColumnTypeMatchers().addColumnTypeMatcher(
				new RegexColumnTypeMatcher("(?:FLOAT|REAL)|FLOAT\\s*\\(\\s*(?:[1-9]|1[0-9]|2[0-4])\\s*\\)"));
		getDbDataTypes().getDbType(DataType.DOUBLE).setColumnTypeMatcher("DOUBLE", "DOUBLE PRECISION")
				.addColumnTypeMatcher(new RegexColumnTypeMatcher("FLOAT\\s*\\(\\s*(?:2[5-9]|[34][0-9]|5[0-3])\\s*\\)"));
		getDbDataTypes().addTimeWithTimeZone("TIME WITH TIME ZONE",
				type -> type.setCreateFormat("TIME WITH TIME ZONE"));
		getDbDataTypes().addTimestampWithTimeZone("TIMESTAMP WITH TIME ZONE",
				type -> type.setCreateFormat("TIMESTAMP WITH TIME ZONE"));
	}
}
