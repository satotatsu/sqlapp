/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.db2.util.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Synonym;

/** Native aliases retain literal names and target-specific comments. */
public class Db2_1110CreateSynonymFactory extends Db2CreateSynonymFactory {
	@Override
	public List<SqlOperation> createSql(Synonym alias) {
		if (alias.getDefinition().isEmpty())
			return super.createSql(alias);
		List<SqlOperation> result = new ArrayList<>();
		for (String sql : Db2DefinitionSql.statements(alias.getDefinition(), getDialect()))
			addSql(result, createSqlBuilder()._add(sql), SqlType.CREATE, alias);
		addOtherDefinitions(alias, result);
		return result;
	}

	@Override
	protected void addCreateObject(Synonym alias, Db2SqlBuilder builder) {
		if (alias.getDbLinkName() != null)
			throw new IllegalArgumentException(
					"Db2 alias requires a local target; use complete CREATE definition for unsupported forms");
		String target = alias.getObjectSchemaName() == null ? Db2CreationContext.identifier(alias.getObjectName())
				: Db2CreationContext.qualified(alias.getObjectSchemaName(), alias.getObjectName());
		builder._add("CREATE " + designator(alias, getOptions().isDecorateSchemaName()) + " FOR " + kind(alias) + " "
				+ target);
	}

	@Override
	protected void addOtherDefinitions(Synonym alias, List<SqlOperation> result) {
		if (alias.getRemarks() != null && !alias.getRemarks().isEmpty())
			addSql(result,
					createSqlBuilder()._add("COMMENT ON " + designator(alias, getOptions().isDecorateSchemaName())
							+ " FOR " + kind(alias) + " IS ").sqlChar(alias.getRemarks()),
					SqlType.SET_COMMENT, alias);
	}

	static String kind(Synonym alias) {
		String kind = alias.getSpecifics().getOrDefault("ALIAS_TYPE", "TABLE");
		if (!Set.of("TABLE", "SEQUENCE", "MODULE").contains(kind))
			throw new IllegalArgumentException("Unsupported Db2 alias target type: " + kind);
		return kind;
	}

	static String designator(Synonym alias, boolean decorate) {
		String name = decorate && alias.getSchemaName() != null
				? Db2CreationContext.qualified(alias.getSchemaName(), alias.getName())
				: Db2CreationContext.identifier(alias.getName());
		return ("SYSPUBLIC".equals(alias.getSchemaName()) ? "PUBLIC " : "") + "ALIAS " + name;
	}
}
