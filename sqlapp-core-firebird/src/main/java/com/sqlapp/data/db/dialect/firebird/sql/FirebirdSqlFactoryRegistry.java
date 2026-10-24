/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.RoleMember;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.ColumnPrivilege;
import com.sqlapp.data.schemas.RoutinePrivilege;

public class FirebirdSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public FirebirdSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Table.class, SqlType.ALTER, FirebirdAlterTableFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Domain.class, SqlType.ALTER, FirebirdAlterDomainFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.View.class, SqlType.ALTER, FirebirdAlterViewFactory.class);
		registerSqlFactory(Trigger.class, SqlType.ALTER, FirebirdAlterTriggerFactory.class);
		registerSqlFactory(Index.class, SqlType.ALTER, FirebirdAlterIndexFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Domain.class, SqlType.CREATE, FirebirdCreateDomainFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.View.class, SqlType.CREATE, FirebirdCreateViewFactory.class);
		for (Class<?> type : new Class<?>[] { Table.class, com.sqlapp.data.schemas.View.class,
				com.sqlapp.data.schemas.Column.class, com.sqlapp.data.schemas.Domain.class, Index.class, Trigger.class,
				Sequence.class, Procedure.class, com.sqlapp.data.schemas.Function.class,
				com.sqlapp.data.schemas.Package.class, Role.class })
			registerSqlFactory(type, SqlType.SET_COMMENT, FirebirdSetCommentFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.UniqueConstraint.class, SqlType.CREATE,
				FirebirdCreateUniqueConstraintFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.ForeignKeyConstraint.class, SqlType.CREATE,
				FirebirdCreateForeignKeyConstraintFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Function.class, SqlType.CREATE, FirebirdCreateFunctionFactory.class);
		registerSqlFactory(Role.class, SqlType.CREATE, FirebirdCreateRoleFactory.class);
		registerSqlFactory(Role.class, SqlType.ALTER, FirebirdAlterRoleFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.UserPrivilege.class, SqlType.GRANT,
				FirebirdGrantUserPrivilegeFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.UserPrivilege.class, SqlType.REVOKE,
				FirebirdRevokeUserPrivilegeFactory.class);
		registerSqlFactory(Role.class, SqlType.DROP, FirebirdDropRoleFactory.class);
		registerSqlFactory(RoleMember.class, SqlType.GRANT, FirebirdGrantRoleMemberFactory.class);
		registerSqlFactory(RoleMember.class, SqlType.REVOKE, FirebirdRevokeRoleMemberFactory.class);
		registerSqlFactory(ObjectPrivilege.class, SqlType.GRANT, FirebirdGrantObjectPrivilegeFactory.class);
		registerSqlFactory(ObjectPrivilege.class, SqlType.REVOKE, FirebirdRevokeObjectPrivilegeFactory.class);
		registerSqlFactory(ColumnPrivilege.class, SqlType.GRANT, FirebirdGrantColumnPrivilegeFactory.class);
		registerSqlFactory(ColumnPrivilege.class, SqlType.REVOKE, FirebirdRevokeColumnPrivilegeFactory.class);
		registerSqlFactory(RoutinePrivilege.class, SqlType.GRANT, FirebirdGrantRoutinePrivilegeFactory.class);
		registerSqlFactory(RoutinePrivilege.class, SqlType.REVOKE, FirebirdRevokeRoutinePrivilegeFactory.class);
		registerSqlFactory(Index.class, SqlType.CREATE, FirebirdCreateIndexFactory.class);
		registerSqlFactory(Procedure.class, SqlType.CREATE, FirebirdCreateProcedureFactory.class);
		registerSqlFactory(Procedure.class, SqlType.ALTER, FirebirdAlterProcedureFactory.class);
		// Table
		registerSqlFactory(Table.class, SqlType.CREATE, FirebirdCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE, FirebirdTruncateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.LOCK, FirebirdLockTableFactory.class);
		registerSqlFactory(Table.class, SqlType.INSERT_ROWS, FirebirdInsertRowsFactory.class);
		//
		registerSqlFactory(Sequence.class, SqlType.CREATE, FirebirdCreateSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.ALTER, FirebirdAlterSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.DROP, FirebirdDropSequenceFactory.class);
		//
		registerSqlFactory(Trigger.class, SqlType.CREATE, FirebirdCreateTriggerFactory.class);
	}
}
