/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.util.LengthColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher;

/** Adds EPAS input aliases without changing PostgreSQL's preferred SQL types. */
final class EdbOracleTypes {
	private EdbOracleTypes() { }
	static void register(Dialect dialect) {
		var types = dialect.getDbDataTypes();
		types.getDbType(DataType.VARCHAR).addColumnTypeMatcher(new LengthColumnTypeMatcher("VARCHAR2"));
		types.getDbType(DataType.VARCHAR).addColumnTypeMatcher(new LengthColumnTypeMatcher("NVARCHAR2"));
		types.getDbType(DataType.BLOB).addColumnTypeMatcher(new LengthColumnTypeMatcher("RAW"));
		types.getDbType(DataType.DECIMAL).addColumnTypeMatcher(new RegexColumnTypeMatcher(
				"NUMBER(?:\\s*\\(\\s*(?<precision>[0-9]+)\\s*(?:,\\s*(?<scale>-?[0-9]+)\\s*)?\\))?", (m, type) -> {
				type.setDataTypeName("NUMBER");
				if (m.group("precision") != null) type.setLength(m.group("precision"));
				if (m.group("scale") != null) type.setScale(m.group("scale"));
			}));
		// DATE is deliberately not remapped: EPAS stores Redwood DATE as timestamp,
		// while PostgreSQL DATE must retain date-only behavior in non-Redwood databases.
	}
}
