/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.jdbc.ExResultSet;

/** External trigger engines became available in Firebird 3.0. */
public class Firebird30TriggerReader extends FirebirdTriggerReader {
	protected Firebird30TriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void readVersionProperties(ExResultSet rs, Trigger trigger) throws SQLException {
		String engine = com.sqlapp.util.CommonUtils.trim(getString(rs, "RDB$ENGINE_NAME"));
		if (engine != null) {
			trigger.getSpecifics().put("firebird.external.engine", engine);
			trigger.getSpecifics().put("firebird.external.entrypoint",
					com.sqlapp.util.CommonUtils.trim(getString(rs, "RDB$ENTRYPOINT")));
		}
	}
}
