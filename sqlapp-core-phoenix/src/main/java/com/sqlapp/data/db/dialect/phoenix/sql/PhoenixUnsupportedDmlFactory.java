/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import java.util.List;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;

/**
 * Prevents unsupported DML requests from becoming invalid SQL or empty plans.
 */
public class PhoenixUnsupportedDmlFactory extends SimpleSqlFactory<Object, PhoenixSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Object object) {
		throw new UnsupportedOperationException("Apache Phoenix does not support generic UPDATE or MERGE; "
				+ "use explicit UPSERT with the complete primary key and the intended conflict semantics");
	}
}
