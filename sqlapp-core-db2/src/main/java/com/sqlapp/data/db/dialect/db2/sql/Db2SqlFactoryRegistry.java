/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.ColumnPrivilege;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.RoleMember;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.State;

public class Db2SqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public Db2SqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Catalog.class, SqlType.CREATE, Db2CreateCatalogFactory.class);
		registerSqlFactory(Catalog.class, SqlType.ALTER, Db2AlterCatalogFactory.class);
		registerSqlFactory(Schema.class, SqlType.CREATE, Db2CreateSchemaFactory.class);
		registerSqlFactory(Schema.class, SqlType.ALTER, Db2AlterSchemaFactory.class);
		registerSqlFactory(UniqueConstraint.class, SqlType.CREATE, Db2CreateUniqueConstraintFactory.class);
		registerSqlFactory(Mview.class, SqlType.CREATE, Db2CreateMviewFactory.class);
		registerSqlFactory(Mview.class, SqlType.DROP, Db2DropMviewFactory.class);
		registerSqlFactory(Mview.class, SqlType.ALTER, Db2AlterMviewFactory.class);
		registerSqlFactory(Function.class, SqlType.CREATE, Db2CreateFunctionFactory.class);
		registerSqlFactory(Function.class, SqlType.DROP, Db2DropFunctionFactory.class);
		registerSqlFactory(Procedure.class, SqlType.DROP, Db2DropProcedureFactory.class);
		registerSqlFactory(Role.class, SqlType.CREATE, Db2CreateRoleFactory.class);
		registerSqlFactory(RoleMember.class, SqlType.CREATE, Db2GrantRoleMemberFactory.class);
		registerSqlFactory(RoleMember.class, SqlType.GRANT, Db2GrantRoleMemberFactory.class);
		registerSqlFactory(RoleMember.class, SqlType.REVOKE, Db2RevokeRoleMemberFactory.class);
		registerSqlFactory(ObjectPrivilege.class, SqlType.CREATE, Db2GrantObjectPrivilegeFactory.class);
		registerSqlFactory(ColumnPrivilege.class, SqlType.CREATE, Db2GrantColumnPrivilegeFactory.class);
		registerSqlFactory(RoutinePrivilege.class, SqlType.CREATE, Db2GrantRoutinePrivilegeFactory.class);
		registerSqlFactory(Index.class, SqlType.CREATE, Db2CreateIndexFactory.class);
		registerSqlFactory(Synonym.class, SqlType.CREATE, Db2CreateSynonymFactory.class);
		registerSqlFactory(Synonym.class, SqlType.DROP, Db2DropSynonymFactory.class);
		registerSqlFactory(ObjectPrivilege.class, SqlType.GRANT, Db2GrantObjectPrivilegeFactory.class);
		registerSqlFactory(ObjectPrivilege.class, SqlType.REVOKE, Db2RevokeObjectPrivilegeFactory.class);
		registerSqlFactory(ColumnPrivilege.class, SqlType.GRANT, Db2GrantColumnPrivilegeFactory.class);
		registerSqlFactory(ColumnPrivilege.class, SqlType.REVOKE, Db2RevokeColumnPrivilegeFactory.class);
		registerSqlFactory(RoutinePrivilege.class, SqlType.GRANT, Db2GrantRoutinePrivilegeFactory.class);
		registerSqlFactory(RoutinePrivilege.class, SqlType.REVOKE, Db2RevokeRoutinePrivilegeFactory.class);
		// Table
		registerSqlFactory(Table.class, SqlType.CREATE, Db2CreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE_TEMPORARY, DB2CreateTemporaryTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE, Db2TruncateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE_TEMPORARY, DB2TruncateTemporaryTableFactory.class);
		registerSqlFactory(Table.class, SqlType.LOCK, Db2LockTableFactory.class);
		// Sequence
		registerSqlFactory(Sequence.class, SqlType.CREATE, Db2CreateSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.SEQUENCE_NEXT_VALUES, Db2SequenceNextValuesFactory.class);
		// Procedure
		registerSqlFactory(Procedure.class, SqlType.CREATE, Db2CreateProcedureFactory.class);
		// Partitioning
		registerSqlFactory(Partitioning.class, SqlType.CREATE, Db2CreatePartitioningFactory.class);
	}

	@Override
	protected void initializeAllStateSqls() {
		super.initializeAllStateSqls();
		registerSqlFactory(Schema.class, State.Modified, SqlType.ALTER);
		registerSqlFactory(Catalog.class, State.Modified, SqlType.ALTER);
		registerSqlFactory(Mview.class, State.Modified, SqlType.ALTER);
	}
}
