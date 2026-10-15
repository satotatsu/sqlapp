/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.PartitionFunction;

/**
 * SQL Server 2005+ DROP PARTITION FUNCTION, without implicit dependency
 * deletion.
 */
public class SqlServer2005DropPartitionFunctionFactory
		extends AbstractDropNamedObjectFactory<PartitionFunction, SqlServerSqlBuilder> {
	@Override
	protected void addDropObject(PartitionFunction object, SqlServerSqlBuilder builder) {
		if (getOptions().isDropIfExists()) {
			builder._add("IF EXISTS (SELECT 1 FROM sys.partition_functions WHERE name = N'")
					._add(object.getName().replace("'", "''"))._add("') ");
		}
		builder.drop().partition().function().space().name(object);
	}
}
