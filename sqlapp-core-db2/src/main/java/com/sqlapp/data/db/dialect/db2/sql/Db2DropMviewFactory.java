/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Mview;

public class Db2DropMviewFactory extends AbstractDropNamedObjectFactory<Mview, Db2SqlBuilder> {
	@Override
	protected void addDropObject(Mview mview, Db2SqlBuilder builder) {
		builder.drop().table().name(mview, getOptions().isDecorateSchemaName());
	}
}
