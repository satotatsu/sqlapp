/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.vertica.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

public class VerticaSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public VerticaSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(com.sqlapp.data.schemas.Schema.class, SqlType.CREATE, VerticaCreateSchemaFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Projection.class, SqlType.DROP, VerticaDropProjectionFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Projection.class, SqlType.ALTER,
				VerticaAlterProjectionFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.UniqueConstraint.class, SqlType.CREATE,
				VerticaCreateUniqueConstraintFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.CheckConstraint.class, SqlType.CREATE,
				VerticaCreateCheckConstraintFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Index.class, SqlType.CREATE, VerticaCreateIndexFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Index.class, SqlType.DROP, VerticaDropIndexFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE, VerticaCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.DROP, VerticaDropTableFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE_TEMPORARY, VerticaCreateTemporaryTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE_TEMPORARY, VerticaTruncateTemporaryTableFactory.class);
		registerSqlFactory(Sequence.class, SqlType.CREATE, VerticaCreateSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.ALTER, VerticaAlterSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.DROP, VerticaDropSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.SEQUENCE_NEXT_VALUES, VerticaSequenceNextValuesFactory.class);
		registerSqlFactory(View.class, SqlType.CREATE, VerticaCreateViewFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Function.class, SqlType.CREATE, VerticaCreateFunctionFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Function.class, SqlType.DROP, VerticaDropFunctionFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Procedure.class, SqlType.CREATE,
				VerticaCreateProcedureFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Procedure.class, SqlType.DROP, VerticaDropProcedureFactory.class);
		registerSqlFactory(View.class, SqlType.DROP, VerticaDropViewFactory.class);
	}

}
