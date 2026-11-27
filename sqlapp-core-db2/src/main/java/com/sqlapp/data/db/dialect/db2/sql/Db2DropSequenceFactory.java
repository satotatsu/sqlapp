/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Sequence;

public class Db2DropSequenceFactory extends AbstractDropNamedObjectFactory<Sequence, Db2SqlBuilder> {
	@Override
	protected void addDropObject(Sequence sequence, Db2SqlBuilder builder) {
		builder._add("DROP SEQUENCE "
				+ Db2_1110CreateSequenceFactory.name(sequence, getOptions().isDecorateSchemaName()) + " RESTRICT");
	}
}
