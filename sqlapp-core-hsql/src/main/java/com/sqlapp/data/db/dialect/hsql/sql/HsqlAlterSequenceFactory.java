/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.StandardAlterSequenceFactory;

/** HSQLDB ALTER SEQUENCE. HSQLDB has no CACHE clause; CREATE ignores it too. */
public class HsqlAlterSequenceFactory extends StandardAlterSequenceFactory<HsqlSqlBuilder> {
	@Override
	protected boolean isCacheAlterable() {
		return false;
	}
}
