/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.jdbc.ExResultSet;

/** Preserves explicit SQL SECURITY while leaving inherited security unset. */
public class Firebird40TriggerReader extends Firebird30TriggerReader {
	protected Firebird40TriggerReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void readVersionProperties(ExResultSet rs, Trigger trigger) throws SQLException {
		super.readVersionProperties(rs, trigger);
		if (rs.getObject("RDB$SQL_SECURITY") != null)
			trigger.getSpecifics().put("firebird.sqlSecurity",
					rs.getBoolean("RDB$SQL_SECURITY") ? "DEFINER" : "INVOKER");
	}
}
