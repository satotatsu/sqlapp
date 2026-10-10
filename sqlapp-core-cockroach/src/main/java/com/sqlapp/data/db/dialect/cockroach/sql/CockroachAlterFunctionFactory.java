/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Definition-backed replacement retains the routine identity and dependencies. */
public class CockroachAlterFunctionFactory extends SimpleSqlFactory<Function,PostgresSqlBuilder> {
	@Override public List<SqlOperation> createSql(Function function) {throw new UnsupportedOperationException("Function ALTER requires original.diff(target)");}
	@Override public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var before=difference.getOriginal(Function.class);var after=difference.getTarget(Function.class);
		if(!Objects.equals(before.getName(),after.getName()) || !Objects.equals(before.getSchemaName(),after.getSchemaName()) || !Objects.equals(before.getSpecificName(),after.getSpecificName()))
			throw new UnsupportedOperationException("Function identity/signature changes require an explicit CockroachDB migration");
		var changed=new HashSet<>(difference.getChangedProperties().keySet());changed.remove(SchemaProperties.DEFINITION.getLabel());
		if(!changed.isEmpty()) throw new UnsupportedOperationException("CockroachDB function changes require a complete target definition; unsupported fields: "+changed);
		if(difference.getChangedProperties().isEmpty()) return List.of();
		String ddl=String.join("\n",after.getDefinition());
		if(!ddl.matches("(?is)^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?FUNCTION\\s+.*")) throw new IllegalArgumentException("CockroachDB function replacement requires CREATE FUNCTION definition");
		ddl=ddl.replaceFirst("(?is)^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?FUNCTION\\s+","CREATE OR REPLACE FUNCTION ");
		var result=new ArrayList<SqlOperation>();addSql(result,createSqlBuilder()._add(ddl),SqlType.ALTER,after);return result;
	}
}
