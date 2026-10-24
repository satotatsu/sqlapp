/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Synonym;

public class Db2DropSynonymFactory extends AbstractDropNamedObjectFactory<Synonym, Db2SqlBuilder> {
	@Override
	protected void addDropObject(Synonym obj, Db2SqlBuilder builder) {
		String type = obj.getSpecifics().get("ALIAS_TYPE");
		if (type == null) {
			type = "TABLE";
		}
		if (!java.util.Set.of("TABLE", "SEQUENCE", "MODULE").contains(type)) {
			throw new IllegalArgumentException("Unsupported Db2 alias target type: " + type);
		}
		builder.drop().space()._add("ALIAS").name(obj, getOptions().isDecorateSchemaName());
		builder.for_().space()._add(type);
	}
}
