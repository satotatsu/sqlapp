/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.DomainCheckConstraint;

class OracleDomainCheckTest {
	@Test
	void numberWithoutPrecisionRetainsOptionalScale() {
		Domain domain = new Domain("NUMERIC_VALUE").setDataType(DataType.DECIMAL);
		var registry = DialectHolder.oracle23aiDialect.createSqlFactoryRegistry();
		assertTrue(registry.createSql(domain, SqlType.CREATE).get(0).getSqlText().endsWith(" AS NUMBER"));
		domain.setScale(2);
		assertTrue(registry.createSql(domain, SqlType.CREATE).get(0).getSqlText().endsWith(" AS NUMBER(*,2)"));
	}
	@Test
	void rejectsAmbiguousMissingAndUnsupportedChecks() {
		Domain domain = new Domain("POSITIVE").setDataType(DataType.INT);
		domain.getChecks().add(new DomainCheckConstraint("CK", "VALUE > 0"));
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle21cDialect.createSqlFactoryRegistry().createSql(domain, SqlType.CREATE));
		domain.setCheck("VALUE < 100");
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle23aiDialect.createSqlFactoryRegistry().createSql(domain, SqlType.CREATE));
		domain.setCheck(null);
		domain.getChecks().get(0).setExpression(" ");
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle23aiDialect.createSqlFactoryRegistry().createSql(domain, SqlType.CREATE));
		domain.getChecks().get(0).setExpression("VALUE > 0");
		domain.getChecks().get(0).getSpecifics().put(Oracle23aiCreateDomainFactory.VALIDATED, "invalid");
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle23aiDialect.createSqlFactoryRegistry().createSql(domain, SqlType.CREATE));
	}
	@Test
	void checkNameIsOptional() {
		for (var dialect : java.util.List.of(DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect)) {
			Domain domain = new Domain("POSITIVE").setDataType(DataType.INT).setCheck("VALUE > 0");
			String sql = dialect.createSqlFactoryRegistry().createSql(domain, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("CHECK (VALUE > 0)"), sql);
			assertFalse(sql.contains("CONSTRAINT"), sql);
			domain.getSpecifics().put(Oracle23aiCreateDomainFactory.CONSTRAINT_NAME, "CK_POSITIVE");
			sql = dialect.createSqlFactoryRegistry().createSql(domain, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("CONSTRAINT CK_POSITIVE CHECK (VALUE > 0)"), sql);
		}
	}
}
