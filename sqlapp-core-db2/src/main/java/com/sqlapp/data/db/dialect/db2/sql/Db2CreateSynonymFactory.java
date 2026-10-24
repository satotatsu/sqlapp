/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSynonymFactory;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.util.CommonUtils;

/**
 * Recreates table, sequence and module aliases from the shared Synonym model.
 */
public class Db2CreateSynonymFactory extends AbstractCreateSynonymFactory<Db2SqlBuilder> {
	@Override
	protected void addCreateObject(Synonym obj, Db2SqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
			return;
		}
		if (CommonUtils.isEmpty(obj.getObjectName()) || obj.getDbLinkName() != null) {
			throw new IllegalArgumentException("Db2 alias requires a local target object: " + obj.getName());
		}
		String type = obj.getSpecifics().get("ALIAS_TYPE");
		if (type == null) {
			type = "TABLE";
		}
		if (!java.util.Set.of("TABLE", "SEQUENCE", "MODULE").contains(type)) {
			throw new IllegalArgumentException("Unsupported Db2 alias target type: " + type);
		}
		builder.create().space()._add("ALIAS").name(obj, getOptions().isDecorateSchemaName());
		builder.for_().space()._add(type).space();
		if (obj.getObjectSchemaName() != null) {
			builder.name(obj.getObjectSchemaName())._add(".");
		}
		builder.name(obj.getObjectName());
	}
}
