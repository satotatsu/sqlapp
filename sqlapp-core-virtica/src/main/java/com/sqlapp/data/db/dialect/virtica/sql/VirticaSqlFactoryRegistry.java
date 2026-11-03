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

package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

public class VirticaSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public VirticaSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(com.sqlapp.data.schemas.Schema.class, SqlType.CREATE, VirticaCreateSchemaFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Projection.class, SqlType.DROP, VirticaDropProjectionFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Projection.class, SqlType.ALTER,
				VirticaAlterProjectionFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.UniqueConstraint.class, SqlType.CREATE,
				VirticaCreateUniqueConstraintFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.CheckConstraint.class, SqlType.CREATE,
				VirticaCreateCheckConstraintFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Index.class, SqlType.CREATE, VirticaCreateIndexFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Index.class, SqlType.DROP, VirticaDropIndexFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE, VirticaCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.DROP, VirticaDropTableFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE_TEMPORARY, VirticaCreateTemporaryTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE_TEMPORARY, VirticaTruncateTemporaryTableFactory.class);
		registerSqlFactory(Sequence.class, SqlType.CREATE, VirticaCreateSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.ALTER, VirticaAlterSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.DROP, VirticaDropSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.SEQUENCE_NEXT_VALUES, VirticaSequenceNextValuesFactory.class);
		registerSqlFactory(View.class, SqlType.CREATE, VirticaCreateViewFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Function.class, SqlType.CREATE, VirticaCreateFunctionFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Function.class, SqlType.DROP, VirticaDropFunctionFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Procedure.class, SqlType.CREATE,
				VirticaCreateProcedureFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Procedure.class, SqlType.DROP, VirticaDropProcedureFactory.class);
		registerSqlFactory(View.class, SqlType.DROP, VirticaDropViewFactory.class);
	}

}
