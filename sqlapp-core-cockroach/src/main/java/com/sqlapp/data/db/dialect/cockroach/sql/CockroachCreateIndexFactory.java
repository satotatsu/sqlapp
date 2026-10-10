/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres110CreateIndexFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.Index;
public class CockroachCreateIndexFactory extends Postgres110CreateIndexFactory {
	@Override public void addCreateObject(Index index,PostgresSqlBuilder builder) {
		if(index.getDefinition()!=null && !index.getDefinition().isEmpty()) builder._add(index.getDefinition());
		else super.addCreateObject(index,builder);
	}
}
