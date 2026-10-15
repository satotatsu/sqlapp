/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.PartitionScheme;

/**
 * SQL Server 2005+ DROP PARTITION SCHEME, without implicit dependency deletion.
 */
public class SqlServer2005DropPartitionSchemeFactory
		extends AbstractDropNamedObjectFactory<PartitionScheme, SqlServerSqlBuilder> {
	@Override
	protected void addDropObject(PartitionScheme object, SqlServerSqlBuilder builder) {
		if (getOptions().isDropIfExists()) {
			builder._add("IF EXISTS (SELECT 1 FROM sys.partition_schemes WHERE name = N'")
					._add(object.getName().replace("'", "''"))._add("') ");
		}
		builder.drop().partition().scheme().space().name(object);
	}
}
