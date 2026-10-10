/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;
import com.sqlapp.data.db.dialect.postgres.sql.PostgresCreateSequenceFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.Sequence;
public class CockroachCreateSequenceFactory extends PostgresCreateSequenceFactory {
	@Override protected void addCreateObject(Sequence sequence,PostgresSqlBuilder builder) {
		if(sequence.getDefinition()!=null && !sequence.getDefinition().isEmpty()) builder._add(sequence.getDefinition());
		else super.addCreateObject(sequence,builder);
	}
}
