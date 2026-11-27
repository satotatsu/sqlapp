/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.ColumnPrivilege;

/** Never silently turn a column grant into a broader table grant. */
public class SapHanaColumnPrivilegeFactory extends SimpleSqlFactory<ColumnPrivilege, SapHanaSqlBuilder> {
	@Override
	public java.util.List<SqlOperation> createSql(ColumnPrivilege privilege) {
		throw new IllegalArgumentException(
				"SAP HANA column GRANT/REVOKE is not supported by this dialect; grant on an explicitly modeled projection view instead");
	}
}
