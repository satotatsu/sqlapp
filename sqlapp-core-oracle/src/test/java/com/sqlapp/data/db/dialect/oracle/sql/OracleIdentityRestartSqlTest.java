/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.Table;

class OracleIdentityRestartSqlTest {
	@Test
	void identityStartsAtTheNextValue() {
		var table = new Table("T");
		table.getColumns()
				.add(new Column("ID").setDataType(DataType.BIGINT).setIdentity(true)
						.setIdentityGenerationType(IdentityGenerationType.ByDefault).setIdentityStartValue(1)
						.setIdentityStep(5).setIdentityLastValue(41L));
		var sql = DialectHolder.oracle12cDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).get(0)
				.getSqlText();
		assertTrue(sql.contains("START WITH 41 INCREMENT BY 5"), sql);
		assertFalse(sql.contains("START WITH 1 "), sql);
	}
}
