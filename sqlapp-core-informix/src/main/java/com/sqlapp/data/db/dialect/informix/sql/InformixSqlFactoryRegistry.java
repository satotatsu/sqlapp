/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.ForeignKeyConstraint;

/** SQL factory registry for Informix-specific syntax. */
public class InformixSqlFactoryRegistry extends SimpleSqlFactoryRegistry {
	public InformixSqlFactoryRegistry(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Table.class, SqlType.CREATE, InformixCreateTableFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Trigger.class, SqlType.CREATE, InformixCreateTriggerFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Function.class, SqlType.CREATE, InformixCreateFunctionFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Procedure.class, SqlType.CREATE,
				InformixCreateProcedureFactory.class);
		registerSqlFactory(Index.class, SqlType.CREATE, InformixCreateIndexFactory.class);
		registerSqlFactory(CheckConstraint.class, SqlType.CREATE, InformixCreateCheckConstraintFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.NotNullConstraint.class, SqlType.CREATE,
				InformixCreateNotNullConstraintFactory.class);
		registerSqlFactory(ForeignKeyConstraint.class, SqlType.CREATE, InformixCreateForeignKeyConstraintFactory.class);
		registerSqlFactory(Sequence.class, SqlType.CREATE, InformixCreateSequenceFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Synonym.class, SqlType.CREATE, InformixCreateSynonymFactory.class);
		registerSqlFactory(UniqueConstraint.class, SqlType.CREATE, InformixCreateUniqueConstraintFactory.class);
	}
}
