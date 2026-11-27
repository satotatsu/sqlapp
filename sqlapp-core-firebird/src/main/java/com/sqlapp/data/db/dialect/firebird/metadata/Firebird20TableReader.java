/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Firebird20 covers both 2.0 and 2.1; only 2.1+ catalogs contain relation
 * types.
 */
public class Firebird20TableReader extends FirebirdTableReader {
	protected Firebird20TableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo version) {
		boolean relationTypes = getDialect().compareTo(DialectHolder.defaultDialect25) >= 0;
		if (version != null && version.getMajorVersion() != null) {
			int major = version.getMajorVersion();
			Integer minor = version.getMinorVersion();
			relationTypes = major > 2 || major == 2 && minor != null && minor >= 1;
		}
		return getSqlNodeCache().getString(relationTypes ? "tables21.sql" : "tables.sql");
	}

	@Override
	protected Table createTable(ExResultSet rs) throws SQLException {
		Table table = super.createTable(rs);
		Object type = rs.getObject("RELATION_TYPE");
		if (type instanceof Number number && (number.intValue() == 4 || number.intValue() == 5)) {
			table.setTableType(Table.TableType.Temporary);
			table.getSpecifics().put("firebird.onCommit", number.intValue() == 4 ? "PRESERVE ROWS" : "DELETE ROWS");
		}
		return table;
	}
}
