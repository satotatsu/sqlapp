/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.MySql800;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.View;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.RoleMember;

public class MySqlSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public MySqlSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	/**
	 * Table,View,Mview以外のSQLコマンドを登録します
	 */
	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		if ("MySQL".equalsIgnoreCase(getDialect().getProductName())) {
			registerSqlFactory(com.sqlapp.data.schemas.SchemaPrivilege.class, SqlType.GRANT,
					MySqlGrantSchemaPrivilegeFactory.class);
			registerSqlFactory(com.sqlapp.data.schemas.SchemaPrivilege.class, SqlType.REVOKE,
					MySqlRevokeSchemaPrivilegeFactory.class);
			registerSqlFactory(com.sqlapp.data.schemas.ObjectPrivilege.class, SqlType.GRANT,
					MySqlGrantObjectPrivilegeFactory.class);
			registerSqlFactory(com.sqlapp.data.schemas.ObjectPrivilege.class, SqlType.REVOKE,
					MySqlRevokeObjectPrivilegeFactory.class);
			registerSqlFactory(com.sqlapp.data.schemas.ColumnPrivilege.class, SqlType.GRANT,
					MySqlGrantColumnPrivilegeFactory.class);
			registerSqlFactory(com.sqlapp.data.schemas.ColumnPrivilege.class, SqlType.REVOKE,
					MySqlRevokeColumnPrivilegeFactory.class);
			registerSqlFactory(com.sqlapp.data.schemas.UserPrivilege.class, SqlType.GRANT,
					MySqlGrantUserPrivilegeFactory.class);
			registerSqlFactory(com.sqlapp.data.schemas.UserPrivilege.class, SqlType.REVOKE,
					MySqlRevokeUserPrivilegeFactory.class);
		}
		if (getDialect() instanceof MySql800 && "MySQL".equalsIgnoreCase(getDialect().getProductName())) {
			registerSqlFactory(Role.class, SqlType.CREATE, MySqlCreateRoleFactory.class);
			registerSqlFactory(Role.class, SqlType.DROP, MySqlDropRoleFactory.class);
			registerSqlFactory(RoleMember.class, SqlType.GRANT, MySqlGrantRoleMemberFactory.class);
			registerSqlFactory(RoleMember.class, SqlType.REVOKE, MySqlRevokeRoleMemberFactory.class);
		}
		registerSqlFactory(RoutinePrivilege.class, SqlType.GRANT, MySqlGrantRoutinePrivilegeFactory.class);
		registerSqlFactory(RoutinePrivilege.class, SqlType.REVOKE, MySqlRevokeRoutinePrivilegeFactory.class);
		// Schema
		registerSqlFactory(Schema.class, SqlType.CREATE, MySqlCreateSchemaFactory.class);
		registerSqlFactory(Schema.class, SqlType.ALTER, MySqlAlterSchemaFactory.class);
		registerSqlFactory(Schema.class, SqlType.SET_SEARCH_PATH_TO_SCHEMA, MySqlSetSearchPathToSchemaFactory.class);
		// Function
		registerSqlFactory(Function.class, SqlType.CREATE, MySqlCreateFunctionFactory.class);
		// Procedure
		registerSqlFactory(Procedure.class, SqlType.CREATE, MySqlCreateProcedureFactory.class);
		// Table
		registerSqlFactory(Table.class, SqlType.CREATE, MySqlCreateTableFactory.class);
		registerSqlFactory(View.class, SqlType.CREATE, MySqlCreateViewFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE_TEMPORARY, MySqlCreateTemporaryTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE_TEMPORARY, MySqlTruncateTemporaryTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER, MySqlAlterTableFactory.class);
		registerSqlFactory(Table.class, SqlType.DROP, MySqlDropTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ANALYZE, MySqlAnalyzeTableFactory.class);
		registerSqlFactory(Table.class, SqlType.OPTIMIZE, MySqlOptimizeTableFactory.class);
		registerSqlFactory(Table.class, SqlType.MERGE, MySqlMergeFactory.class);
		registerSqlFactory(Table.class, SqlType.LOCK, MySqlLockTableFactory.class);
		// UniqueConstraint
		registerSqlFactory(UniqueConstraint.class, SqlType.CREATE, MySqlCreateUniqueConstraintFactory.class);
		// CheckConstraint
		registerSqlFactory(CheckConstraint.class, SqlType.CREATE, MySqlCreateCheckConstraintFactory.class);
		// ForeignKeyConstraint
		registerSqlFactory(ForeignKeyConstraint.class, SqlType.CREATE, MySqlForeignKeyConstraintFactory.class);
		// Trigger
		registerSqlFactory(Trigger.class, SqlType.CREATE, MySqlCreateTriggerFactory.class);
		registerSqlFactory(Trigger.class, SqlType.DROP, MySqlDropTriggerFactory.class);
		// Index
		registerSqlFactory(Index.class, SqlType.CREATE, MySqlCreateIndexFactory.class);
		registerSqlFactory(Index.class, SqlType.DROP, MySqlDropIndexFactory.class);
		// Partitioning
		registerSqlFactory(Partitioning.class, SqlType.CREATE, MySqlCreatePartitioningFactory.class);
		//
		registerSqlFactory(Event.class, SqlType.CREATE, MySqlCreateEventFactory.class);
	}
}
