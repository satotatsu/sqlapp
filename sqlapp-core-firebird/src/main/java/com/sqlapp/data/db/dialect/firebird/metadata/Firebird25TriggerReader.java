/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.jdbc.ExResultSet;

public class Firebird25TriggerReader extends FirebirdTriggerReader {
	protected Firebird25TriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void readVersionProperties(ExResultSet row, Trigger trigger) throws SQLException {
		FirebirdCompilationState.blr(row, trigger);
	}
}
