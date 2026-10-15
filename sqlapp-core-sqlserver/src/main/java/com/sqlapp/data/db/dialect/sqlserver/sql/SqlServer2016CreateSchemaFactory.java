/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.List;
import com.sqlapp.data.db.sql.CreateSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Schema;

/** Enables versioning after all tables, including explicitly modeled history tables, exist. */
public class SqlServer2016CreateSchemaFactory extends CreateSchemaFactory {
	@Override
	public List<SqlOperation> createSql(Schema object) {
		return SqlServerTemporalSupport.enableLast(super.createSql(object));
	}
}
