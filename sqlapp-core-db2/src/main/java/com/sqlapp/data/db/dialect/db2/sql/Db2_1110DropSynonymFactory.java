/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.schemas.Synonym;

public class Db2_1110DropSynonymFactory extends Db2DropSynonymFactory {
	@Override
	protected void addDropObject(Synonym alias, Db2SqlBuilder builder) {
		builder._add("DROP " + Db2_1110CreateSynonymFactory.designator(alias, getOptions().isDecorateSchemaName())
				+ " FOR " + Db2_1110CreateSynonymFactory.kind(alias));
	}
}
