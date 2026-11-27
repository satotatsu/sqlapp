/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.db.sql.AbstractDropSequenceFactory;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.schemas.Sequence;

public class PhoenixDropSequenceFactory extends AbstractDropSequenceFactory<PhoenixSqlBuilder> {
	@Override
	protected void addDropObject(Sequence sequence, PhoenixSqlBuilder builder) {
		builder.drop().sequence().ifExists(getOptions().isDropIfExists()).name(sequence,
				getOptions().isDecorateSchemaName());
	}
}
