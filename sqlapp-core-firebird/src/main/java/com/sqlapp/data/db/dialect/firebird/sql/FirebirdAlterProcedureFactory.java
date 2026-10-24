/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

public class FirebirdAlterProcedureFactory extends FirebirdAlterRoutineFactory<com.sqlapp.data.schemas.Procedure> {
	@Override
	protected String kind() {
		return "PROCEDURE";
	}
}
