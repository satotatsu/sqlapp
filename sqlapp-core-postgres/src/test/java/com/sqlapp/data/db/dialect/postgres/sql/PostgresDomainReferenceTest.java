/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Table;

class PostgresDomainReferenceTest {
	@Test
	void explicitSharedDomainReferenceGeneratesQuotedTypeAndArray() {
		var dialect = DialectResolver.getInstance().getDialect("postgres", 18, 0);
		Table table = new Table("t");
		table.getColumns().add(new Column("c").setDomain(new Domain("Email Domain").setSchemaName("Other Schema"))
				.setArrayDimension(1));
		String sql = dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("\"Other Schema\".\"Email Domain\"[]"), sql);
	}
}
