/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.jdbc.ExResultSet;

/** Index visibility metadata introduced in Oracle 11g. */
public class Oracle11gIndexReader extends OracleIndexReader {
	public Oracle11gIndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void readState(ExResultSet rs, Index index) throws SQLException {
		super.readState(rs, index);
		String visibility = getString(rs, "VISIBILITY");
		if (visibility != null)
			index.getSpecifics().put("oracle.index.visibility", visibility);
	}
}
