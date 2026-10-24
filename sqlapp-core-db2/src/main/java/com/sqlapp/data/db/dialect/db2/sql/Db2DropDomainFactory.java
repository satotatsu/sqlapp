/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;
import java.util.*;
import com.sqlapp.data.db.dialect.db2.util.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Domain;

public class Db2DropDomainFactory extends SimpleSqlFactory<Domain, Db2SqlBuilder> {
	@Override public List<SqlOperation> createSql(Domain type) {
		Db2CreateDomainFactory.validate(type, getDialect());
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, createSqlBuilder()._add("DROP TYPE " + Db2CreationContext.qualified(type.getSchemaName(), type.getName())), SqlType.DROP, type);
		return result;
	}
}
