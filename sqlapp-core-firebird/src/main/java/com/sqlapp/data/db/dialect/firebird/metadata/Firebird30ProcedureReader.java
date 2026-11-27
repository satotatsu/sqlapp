/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Package members are recreated through their package, not as standalone
 * routines.
 */
public class Firebird30ProcedureReader extends Firebird25ProcedureReader {
	@Override
	protected com.sqlapp.data.schemas.Procedure createProcedure(com.sqlapp.jdbc.ExResultSet rs)
			throws java.sql.SQLException {
		var routine = super.createProcedure(rs);
		String engine = com.sqlapp.util.CommonUtils.trim(getString(rs, "RDB$ENGINE_NAME"));
		if (engine != null) {
			routine.getSpecifics().put("firebird.external.engine", engine);
			routine.getSpecifics().put("firebird.external.entrypoint",
					com.sqlapp.util.CommonUtils.trim(getString(rs, "RDB$ENTRYPOINT")));
		}
		return routine;
	}

	protected Firebird30ProcedureReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo version) {
		return getSqlNodeCache().getString("procedures30.sql");
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new FirebirdProcedureArgumentReader(getDialect()) {
			@Override
			protected SqlNode getSqlSqlNode(ProductVersionInfo version) {
				return getSqlNodeCache(Firebird30ProcedureReader.class).getString("procedureArguments30.sql");
			}
		};
	}
}
