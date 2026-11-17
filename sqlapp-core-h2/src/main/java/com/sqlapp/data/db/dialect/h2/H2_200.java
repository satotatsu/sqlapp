/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 */
package com.sqlapp.data.db.dialect.h2;

import java.util.function.Supplier;

import com.sqlapp.data.converter.StandardIntervalConverter;
import com.sqlapp.data.db.datatype.AbstractLengthType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.StandardIntervalCodec;
import com.sqlapp.data.db.datatype.StandardIntervalJdbcTypeHandler;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;

/**
 * H2 2.0.
 */
public class H2_200 extends H2 {

	private static final long serialVersionUID = 1L;
	/** Maximum length of character and binary strings in H2 2.x. */
	private static final long MAX_LENGTH = 1_000_000_000L;

	protected H2_200(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	protected void registerDataType() {
		super.registerDataType();
		getDbDataTypes().getDbType(com.sqlapp.data.db.datatype.DataType.JSON)
				.setJdbcTypeHandler(new H2JsonJdbcTypeHandler());
		for (var type : java.util.List.of(DataType.CHAR, DataType.VARCHAR, DataType.VARCHAR_IGNORECASE,
				DataType.LONGVARCHAR, DataType.NCHAR, DataType.NVARCHAR, DataType.BINARY, DataType.VARBINARY,
				DataType.LONGVARBINARY)) {
			if (getDbDataTypes().getDbType(type) instanceof AbstractLengthType<?> length
					&& length.getDataType() == type)
				length.setMaxLength(MAX_LENGTH);
		}
		getDbDataTypes().addDecimalFloat("DECFLOAT");
		for (DataType type : DataType.values()) {
			if (type.getMetaTtype() == com.sqlapp.data.db.datatype.MetaType.INTERVAL
					&& type != DataType.INTERVAL && type != DataType.INTERVAL_YEAR_TO_DAY)
				getDbDataTypes().addStandardInterval(type, 18, 9);
		}
		getDbDataTypes().addTimeWithTimeZone(type -> {
			type.setDefaultValueLiteral(getCurrentTimeFunction());
		});
	}

	@Override
	public String getSequenceNextValString(String sequenceName) {
		return "SELECT NEXT VALUE FOR " + sequenceName;
	}

	@Override
	public String getSqlValueDefinition(Column column, Object value) {
		var type = column.getDataType();
		if (type != null && type.getMetaTtype() == com.sqlapp.data.db.datatype.MetaType.INTERVAL) {
			var handler = (StandardIntervalJdbcTypeHandler)
					getDbDataTypes().getDbTypeStrict(type).getJdbcTypeHandler();
			return handler.literal(value, column);
		}
		return super.getSqlValueDefinition(column, value);
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
