/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSynonymFactory;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Synonym;

/** Host alias grammar and IBM i member semantics differ from LUW aliases. */
public class Db2HostCreateSynonymFactory extends AbstractCreateSynonymFactory<Db2SqlBuilder> {
	/** DROP must address the alias itself rather than the referenced table. */
	public static class Drop extends AbstractDropNamedObjectFactory<Synonym, Db2SqlBuilder> {
		@Override
		protected void addDropObject(Synonym synonym, Db2SqlBuilder builder) {
			String kind = synonym.getSpecifics().get("DB2_HOST_NAME_KIND");
			if (kind != null && !"ALIAS".equals(kind))
				throw new IllegalArgumentException("Legacy/unknown host name kind is inventory only: " + kind);
			require(synonym.getName(), "alias name");
			boolean zos = "db2-zos".equals(getDialect().getSimpleName());
			String type = synonym.getSpecifics().getOrDefault("ALIAS_TYPE", "TABLE");
			if (!"TABLE".equals(type) && !(zos && "SEQUENCE".equals(type)))
				throw new IllegalArgumentException("Unsupported host alias target type: " + type);
			builder.drop().space()._add("ALIAS").name(synonym, getOptions().isDecorateSchemaName());
			if (zos)
				builder.for_().space()._add(type);
		}
	}

	@Override
	protected void addCreateObject(Synonym synonym, Db2SqlBuilder builder) {
		String kind = synonym.getSpecifics().get("DB2_HOST_NAME_KIND");
		if (kind != null && !"ALIAS".equals(kind))
			throw new IllegalArgumentException(
					"Legacy/unknown host name kind cannot be recreated as an alias: " + kind);
		if (!synonym.getDefinition().isEmpty()) {
			if (synonym.getDefinition().size() != 1 || synonym.getDefinition().get(0) == null
					|| synonym.getDefinition().get(0).indexOf('\0') >= 0
					|| !synonym.getDefinition().get(0).stripLeading().matches("(?is)CREATE\\s+ALIAS\\b.*"))
				throw new IllegalArgumentException("Host alias requires one complete CREATE ALIAS definition");
			builder._add(synonym.getDefinition());
			return;
		}
		require(synonym.getName(), "alias name");
		require(synonym.getObjectSchemaName(), "target schema");
		require(synonym.getObjectName(), "target name");
		String type = synonym.getSpecifics().getOrDefault("ALIAS_TYPE", "TABLE");
		boolean zos = "db2-zos".equals(getDialect().getSimpleName());
		if (!"TABLE".equals(type) && !(zos && "SEQUENCE".equals(type)))
			throw new IllegalArgumentException("Unsupported host alias target type: " + type);
		String member = synonym.getSpecifics().get("DB2_HOST_MEMBER_NAME");
		if (member != null && (zos || !"TABLE".equals(type)))
			throw new IllegalArgumentException("Members are supported only for IBM i table aliases");
		// *FIRST is the catalog representation when no member was specified.
		boolean explicitMember = member != null && !"*FIRST".equals(member);
		if (explicitMember && (member.isBlank() || member.startsWith("*")))
			throw new IllegalArgumentException(
					"Special member requires a verified CREATE definition; *LAST depends on IBM i PTF level");
		if (explicitMember && synonym.getDbLinkName() != null)
			throw new IllegalArgumentException("Remote IBM i member alias requires an explicit CREATE definition");
		if ("SEQUENCE".equals(type) && synonym.getDbLinkName() != null)
			throw new IllegalArgumentException("z/OS sequence aliases require a local sequence");
		builder.create().space()._add("ALIAS").name(synonym, getOptions().isDecorateSchemaName());
		builder.for_().space();
		if (zos && "SEQUENCE".equals(type))
			builder._add("SEQUENCE ");
		if (synonym.getDbLinkName() != null) {
			require(synonym.getDbLinkName(), "target location");
			builder.name(synonym.getDbLinkName())._add(".");
		}
		builder.name(synonym.getObjectSchemaName())._add(".").name(synonym.getObjectName());
		if (explicitMember)
			builder._add("(").name(member)._add(")");
	}

	private static void require(String value, String label) {
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
			throw new IllegalArgumentException("Host alias requires a valid " + label);
	}
}
