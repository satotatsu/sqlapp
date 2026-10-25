/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.SQLException;
import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.jdbc.ExResultSet;

/** Catalog observations, not user-settable DDL attributes. */
final class FirebirdCompilationState {
	static void blr(ExResultSet row, AbstractNamedObject<?> object) throws SQLException {
		if (row.getObject("RDB$VALID_BLR") != null)
			object.getSpecifics().put("firebird.validBlr", Boolean.toString(row.getBoolean("RDB$VALID_BLR")));
	}

	static void packageBody(ExResultSet row, AbstractNamedObject<?> object) throws SQLException {
		// Unlike VALID_BLR, a null package-body flag means invalid/unimplemented.
		object.getSpecifics().put("firebird.validBody", Boolean.toString(row.getBoolean("RDB$VALID_BODY_FLAG")));
		object.getSpecifics().put("firebird.hasBody", Boolean.toString(row.getBoolean("HAS_BODY")));
	}

	private FirebirdCompilationState() {
	}
}
