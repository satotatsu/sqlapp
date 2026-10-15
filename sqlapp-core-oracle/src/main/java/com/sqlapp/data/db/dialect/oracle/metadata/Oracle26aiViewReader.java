/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.sql.OracleJsonDualityViewUtils;
import com.sqlapp.data.schemas.View;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Oracle 26ai duality metadata including logical replication state. */
public class Oracle26aiViewReader extends Oracle23aiViewReader {
	protected Oracle26aiViewReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getDualitySqlNode() {
		return getSqlNodeCache().getString("jsonRelationalDualityViews26ai.sql");
	}

	@Override
	protected void setDualityMetadata(View view, ExResultSet rs) throws SQLException {
		super.setDualityMetadata(view, rs);
		final String state = getString(rs, "LOGICAL_REPLICATION");
		if (state == null) {
			return;
		}
		if (!"ENABLED".equals(state) && !"DISABLED".equals(state)) {
			throw new IllegalStateException("Unknown Oracle duality logical replication state: " + state);
		}
		view.getSpecifics().put(OracleJsonDualityViewUtils.LOGICAL_REPLICATION, "ENABLED".equals(state));
	}
}
