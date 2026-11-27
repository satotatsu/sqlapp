/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Trigger;

public class Db2DropTriggerFactory extends AbstractDropNamedObjectFactory<Trigger, Db2SqlBuilder> {
	@Override
	protected void addDropObject(Trigger trigger, Db2SqlBuilder builder) {
		builder._add("DROP TRIGGER " + Db2CreateTriggerFactory.name(trigger, getOptions().isDecorateSchemaName()));
	}
}
