/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.sql.SqlType;

public class SapHanaDropUserFactory extends SapHanaCreateUserFactory {
	@Override
	protected SqlType operation() {
		return SqlType.DROP;
	}
}
