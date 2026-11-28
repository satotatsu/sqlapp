/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.duckdb.util;

import com.sqlapp.data.converter.DefaultConverter;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;

/**
 * Fallback for native type expressions retained as OTHER in the Schema model.
 */
public final class DuckDbNativeType extends DbDataType<DuckDbNativeType> {

	private static final long serialVersionUID = 1L;

	public DuckDbNativeType() {
		setDataType(DataType.OTHER);
		initialize("OTHER");
		setConverter(new DefaultConverter());
		setJdbcTypeHandler(new DuckDbJdbcTypeHandlers.NativeTextHandler());
	}
}
