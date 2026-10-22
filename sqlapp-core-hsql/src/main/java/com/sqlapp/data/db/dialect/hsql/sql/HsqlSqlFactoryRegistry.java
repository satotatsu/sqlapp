/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Catalog;

public class HsqlSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public HsqlSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Schema.class, SqlType.CREATE, HsqlCreateSchemaFactory.class);
		registerSqlFactory(Catalog.class, SqlType.CREATE, HsqlCreateCatalogFactory.class);
		for (Class<?> privilege : java.util.List.of(com.sqlapp.data.schemas.ObjectPrivilege.class,
				com.sqlapp.data.schemas.ColumnPrivilege.class, com.sqlapp.data.schemas.RoutinePrivilege.class)) {
			registerSqlFactory(privilege, SqlType.CREATE, HsqlGrantPrivilegeFactory.class);
			registerSqlFactory(privilege, SqlType.GRANT, HsqlGrantPrivilegeFactory.class);
			registerSqlFactory(privilege, SqlType.REVOKE, HsqlRevokePrivilegeFactory.class);
		}
		registerSqlFactory(Trigger.class, SqlType.CREATE, HsqlCreateTriggerFactory.class);
		registerSqlFactory(Domain.class, SqlType.CREATE, HsqlCreateDomainFactory.class);
		registerSqlFactory(Domain.class, SqlType.ALTER, HsqlAlterDomainFactory.class);
		registerSqlFactory(Type.class, SqlType.CREATE, HsqlCreateTypeFactory.class);
		registerSqlFactory(Type.class, SqlType.ALTER, HsqlAlterTypeFactory.class);
		registerSqlFactory(Synonym.class, SqlType.CREATE, HsqlCreateSynonymFactory.class);
		registerSqlFactory(Synonym.class, SqlType.ALTER, HsqlAlterSynonymFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE, HsqlCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE_TEMPORARY, HsqlCreateTemporaryTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE_TEMPORARY, HsqlTruncateTemporaryTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER, HsqlAlterTableFactory.class);
		registerSqlFactory(Procedure.class, SqlType.CREATE, HsqlCreateProcedureFactory.class);
		registerSqlFactory(Function.class, SqlType.CREATE, HsqlCreateFunctionFactory.class);
		//
		registerSqlFactory(Sequence.class, SqlType.CREATE, HsqlCreateSequenceFactory.class);
	}
}
