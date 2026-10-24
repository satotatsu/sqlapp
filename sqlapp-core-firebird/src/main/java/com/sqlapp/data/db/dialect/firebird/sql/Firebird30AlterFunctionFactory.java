/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

public class Firebird30AlterFunctionFactory extends FirebirdAlterRoutineFactory<com.sqlapp.data.schemas.Function> {
	@Override
	protected String kind() {
		return "FUNCTION";
	}
}
