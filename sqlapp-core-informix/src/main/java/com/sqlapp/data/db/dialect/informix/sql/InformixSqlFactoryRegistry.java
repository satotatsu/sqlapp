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
		registerSqlFactory(com.sqlapp.data.schemas.Catalog.class, SqlType.ALTER, InformixAlterCatalogFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Catalog.class, SqlType.CREATE, InformixCreateCatalogFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Schema.class, SqlType.CREATE, InformixCreateSchemaFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.ObjectPrivilege.class, SqlType.CREATE,
				InformixTablePrivilegeFactories.GrantTable.class);
		registerSqlFactory(com.sqlapp.data.schemas.ObjectPrivilege.class, SqlType.GRANT,
				InformixTablePrivilegeFactories.GrantTable.class);
		registerSqlFactory(com.sqlapp.data.schemas.ObjectPrivilege.class, SqlType.DROP,
				InformixTablePrivilegeFactories.RevokeTable.class);
		registerSqlFactory(com.sqlapp.data.schemas.ObjectPrivilege.class, SqlType.REVOKE,
				InformixTablePrivilegeFactories.RevokeTable.class);
		registerSqlFactory(com.sqlapp.data.schemas.ColumnPrivilege.class, SqlType.CREATE,
				InformixTablePrivilegeFactories.GrantColumn.class);
		registerSqlFactory(com.sqlapp.data.schemas.ColumnPrivilege.class, SqlType.GRANT,
				InformixTablePrivilegeFactories.GrantColumn.class);
		registerSqlFactory(com.sqlapp.data.schemas.ColumnPrivilege.class, SqlType.DROP,
				InformixTablePrivilegeFactories.RevokeColumn.class);
		registerSqlFactory(com.sqlapp.data.schemas.ColumnPrivilege.class, SqlType.REVOKE,
				InformixTablePrivilegeFactories.RevokeColumn.class);
		registerSqlFactory(com.sqlapp.data.schemas.User.class, SqlType.CREATE, InformixUserFactories.Grant.class);
		registerSqlFactory(com.sqlapp.data.schemas.User.class, SqlType.GRANT, InformixUserFactories.Grant.class);
		registerSqlFactory(com.sqlapp.data.schemas.User.class, SqlType.DROP, InformixUserFactories.Revoke.class);
		registerSqlFactory(com.sqlapp.data.schemas.User.class, SqlType.REVOKE, InformixUserFactories.Revoke.class);
		registerSqlFactory(com.sqlapp.data.schemas.User.class, SqlType.ALTER, InformixUserFactories.Alter.class);
		registerSqlFactory(com.sqlapp.data.schemas.RoutinePrivilege.class, SqlType.CREATE,
				InformixRoutinePrivilegeFactories.Grant.class);
		registerSqlFactory(com.sqlapp.data.schemas.RoutinePrivilege.class, SqlType.GRANT,
				InformixRoutinePrivilegeFactories.Grant.class);
		registerSqlFactory(com.sqlapp.data.schemas.RoutinePrivilege.class, SqlType.DROP,
				InformixRoutinePrivilegeFactories.Revoke.class);
		registerSqlFactory(com.sqlapp.data.schemas.RoutinePrivilege.class, SqlType.REVOKE,
				InformixRoutinePrivilegeFactories.Revoke.class);
		registerSqlFactory(com.sqlapp.data.schemas.RoutinePrivilege.class, SqlType.ALTER,
				InformixGrantOptionFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.ObjectPrivilege.class, SqlType.ALTER,
				InformixGrantOptionFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.ColumnPrivilege.class, SqlType.ALTER,
				InformixGrantOptionFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Role.class, SqlType.CREATE, InformixRoleFactories.CreateRole.class);
		registerSqlFactory(com.sqlapp.data.schemas.Role.class, SqlType.DROP, InformixRoleFactories.DropRole.class);
		registerSqlFactory(com.sqlapp.data.schemas.RoleMember.class, SqlType.CREATE,
				InformixRoleFactories.GrantRole.class);
		registerSqlFactory(com.sqlapp.data.schemas.RoleMember.class, SqlType.GRANT,
				InformixRoleFactories.GrantRole.class);
		registerSqlFactory(com.sqlapp.data.schemas.RoleMember.class, SqlType.DROP,
				InformixRoleFactories.RevokeRole.class);
		registerSqlFactory(com.sqlapp.data.schemas.RoleMember.class, SqlType.REVOKE,
				InformixRoleFactories.RevokeRole.class);
		registerSqlFactory(com.sqlapp.data.schemas.RoleMember.class, SqlType.ALTER,
				InformixRoleFactories.AlterMembership.class);
		registerSqlFactory(Table.class, SqlType.CREATE, InformixCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER, InformixAlterTableFactory.class);
		registerSqlFactory(Table.class, SqlType.CREATE_TEMPORARY, InformixCreateTemporaryTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE_TEMPORARY, InformixTruncateTemporaryTableFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Type.class, SqlType.CREATE, InformixCreateTypeFactory.class);
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
		registerSqlFactory(Sequence.class, SqlType.SEQUENCE_NEXT_VALUES, InformixSequenceNextValuesFactory.class);
		registerSqlFactory(com.sqlapp.data.schemas.Synonym.class, SqlType.CREATE, InformixCreateSynonymFactory.class);
		registerSqlFactory(UniqueConstraint.class, SqlType.CREATE, InformixCreateUniqueConstraintFactory.class);
	}
}
