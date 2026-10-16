/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.util.SimpleBeanUtils;

public class Oracle23aiSqlFactoryRegistry extends Oracle21cSqlFactoryRegistry {

	public Oracle23aiSqlFactoryRegistry(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Catalog.class, SqlType.CREATE, Oracle23aiCreateCatalogFactory.class);
		registerSqlFactory(Catalog.class, SqlType.ALTER, Oracle23aiAlterCatalogFactory.class);
		registerSqlFactory(Schema.class, SqlType.CREATE, Oracle23aiCreateSchemaFactory.class);
		registerSqlFactory(Schema.class, SqlType.ALTER, Oracle23aiAlterSchemaFactory.class);
		registerSqlFactory(Index.class, SqlType.CREATE, Oracle23aiCreateIndexFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE, Oracle23aiCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER, Oracle23aiAlterTableFactory.class);
		registerSqlFactory(Table.class, SqlType.DROP, Oracle23aiDropTableFactory.class);
		registerSqlFactory(Domain.class, SqlType.CREATE, Oracle23aiCreateDomainFactory.class);
		registerSqlFactory(Domain.class, SqlType.DROP, Oracle23aiDropDomainFactory.class);
		registerSqlFactory(Domain.class, SqlType.ALTER, Oracle23aiAlterDomainFactory.class);
		registerSqlFactory(View.class, SqlType.CREATE, Oracle23aiCreateViewFactory.class);
		registerSqlFactory(View.class, SqlType.DROP, Oracle23aiDropViewFactory.class);
	}
	@Override
	protected void initializeAllStateSqls() {
		super.initializeAllStateSqls();
		registerSqlFactory(Domain.class, State.Modified, SqlType.ALTER);
		registerSqlFactory(Schema.class, State.Modified, SqlType.ALTER);
		registerSqlFactory(Catalog.class, State.Modified, SqlType.ALTER);
	}
	@Override
	protected void setDialect(SqlFactory<?> factory, Object original) {
		Dialect inferred = original == null ? null : SimpleBeanUtils.getValue(original, "dialect");
		if (inferred == null || inferred.getClass() == Dialect.class) {
			factory.setDialect(getDialect());
		} else {
			super.setDialect(factory, original);
		}
	}
}
