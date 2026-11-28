/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver;

import java.util.function.Supplier;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2025CatalogReader;
import com.sqlapp.data.db.metadata.CatalogReader;

/** SQL Server 2025 native JSON and vector types. */
public class SqlServer2025 extends SqlServer2022 {
	private static final long serialVersionUID = 1L;

	protected SqlServer2025(Supplier<Dialect> nextVersion) {
		super(nextVersion);
	}

	@Override
	protected void registerDataType() {
		super.registerDataType();
		getDbDataTypes().addJsonType(type -> {
			type.setDefaultValueLiteral("'{}'");
			type.setJdbcTypeHandler(new SqlServer2025NativeTypeHandler(false));
		});
		getDbDataTypes().addVector();
		var vector = getDbDataTypes().getDbType(DataType.VECTOR);
		vector.setJdbcTypeHandler(new SqlServer2025NativeTypeHandler(true));
		vector.setLiteral("'", "'");
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new SqlServer2025CatalogReader(this);
	}
}
