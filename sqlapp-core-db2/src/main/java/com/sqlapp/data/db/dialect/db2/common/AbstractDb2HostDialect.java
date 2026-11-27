/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import com.sqlapp.data.db.dialect.DefaultCase;
import com.sqlapp.data.db.dialect.db2.Db2;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.CascadeRule;

/**
 * Shared basic host support, deliberately independent of the LUW
 * registry/readers.
 */
public abstract class AbstractDb2HostDialect extends Db2 {

	private static final long serialVersionUID = 1L;

	protected AbstractDb2HostDialect() {
		super(null);
	}

	/** Host product family; selects platform-specific catalog and DDL grammar. */
	public enum Platform {
		ZOS, IBM_I
	}

	/**
	 * @return the host product family of this dialect
	 */
	public abstract Platform getPlatform();

	protected long maxCharLength() {
		return 255;
	}

	protected long maxVarcharLength() {
		return 32704;
	}

	protected long maxGraphicLength() {
		return 127;
	}

	protected long maxVargraphicLength() {
		return 16352;
	}

	protected int maxDecimalPrecision() {
		return 31;
	}

	@Override
	protected void registerDataType() {
		super.registerDataType();
		getDbDataTypes().addRowId(type -> type.setOctetSize(40L));
		getDbDataTypes().addChar(maxCharLength());
		getDbDataTypes().addVarchar(maxVarcharLength());
		getDbDataTypes().addNChar("GRAPHIC", maxGraphicLength(), type -> type.setLiteral("G'", "'"));
		getDbDataTypes().addNVarchar("VARGRAPHIC", maxVargraphicLength(), type -> type.setLiteral("G'", "'"));
		getDbDataTypes().addBinary("BINARY", maxCharLength(), type -> type.setLiteral("X'", "'"));
		getDbDataTypes().addVarBinary("VARBINARY", maxVarcharLength(), type -> type.setLiteral("X'", "'"));
		getDbDataTypes().addDecimal(type -> type.setDefaultPrecision(19).setDefaultScale(5)
				.setMaxPrecision(maxDecimalPrecision()).setMaxScale(maxDecimalPrecision()));
		getDbDataTypes().addNumeric(type -> type.setDefaultPrecision(19).setDefaultScale(5)
				.setMaxPrecision(maxDecimalPrecision()).setMaxScale(maxDecimalPrecision()));
	}

	@Override
	public DefaultCase getDefaultCase() {
		return DefaultCase.UpperCase;
	}

	@Override
	public boolean supportsIdentity() {
		return true;
	}

	/** z/OS has implicit NO ACTION for updates; IBM i exposes ON UPDATE. */
	public boolean supportsExplicitUpdateRule() {
		return false;
	}

	@Override
	public boolean supportsDropCascade() {
		return false;
	}

	@Override
	public boolean supportsRuleOnDelete(CascadeRule rule) {
		return rule == CascadeRule.None || rule == CascadeRule.Restrict || rule == CascadeRule.Cascade
				|| rule == CascadeRule.SetNull;
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		var registry = new SimpleSqlFactoryRegistry(this);
		registry.getOptions().setCreateIfNotExists(false);
		registry.registerSqlFactory(Table.class, SqlType.CREATE, Db2HostCreateTableFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.View.class, SqlType.CREATE,
				com.sqlapp.data.db.dialect.db2.sql.Db2CreateViewFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Sequence.class, SqlType.CREATE,
				Db2HostCreateSequenceFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Synonym.class, SqlType.CREATE,
				Db2HostCreateSynonymFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Synonym.class, SqlType.DROP,
				Db2HostCreateSynonymFactory.Drop.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Domain.class, SqlType.CREATE, Db2HostDomainFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Domain.class, SqlType.DROP,
				Db2HostDomainFactory.Drop.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Index.class, SqlType.CREATE,
				Db2HostCreateIndexFactory.class);
		registerAlterAndDrop(registry);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Constant.class, SqlType.CREATE,
				Db2HostVariables.Create.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Constant.class, SqlType.DROP, Db2HostVariables.Drop.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Constant.class, SqlType.ALTER,
				Db2HostAlterFactories.CommentOnly.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Sequence.class, SqlType.SEQUENCE_NEXT_VALUES,
				Db2HostSequenceNextValuesFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Mview.class, SqlType.CREATE,
				com.sqlapp.data.db.dialect.db2.sql.Db2CreateMviewFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Mview.class, SqlType.DROP,
				com.sqlapp.data.db.dialect.db2.sql.Db2DropMviewFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Mask.class, SqlType.CREATE, Db2HostCreateMaskFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Schema.class, SqlType.CREATE,
				Db2HostCreateSchemaFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Procedure.class, SqlType.CREATE,
				Db2HostDefinitionFactories.ProcedureCreate.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Function.class, SqlType.CREATE,
				Db2HostDefinitionFactories.FunctionCreate.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Trigger.class, SqlType.CREATE,
				Db2HostDefinitionFactories.TriggerCreate.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.ObjectPrivilege.class, SqlType.GRANT,
				Db2HostPrivilegeFactories.GrantObject.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.ObjectPrivilege.class, SqlType.CREATE,
				Db2HostPrivilegeFactories.GrantObject.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.ObjectPrivilege.class, SqlType.REVOKE,
				Db2HostPrivilegeFactories.RevokeObject.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.ColumnPrivilege.class, SqlType.GRANT,
				Db2HostPrivilegeFactories.GrantColumn.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.ColumnPrivilege.class, SqlType.REVOKE,
				Db2HostPrivilegeFactories.RevokeColumn.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.RoutinePrivilege.class, SqlType.GRANT,
				Db2HostPrivilegeFactories.GrantRoutine.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.RoutinePrivilege.class, SqlType.REVOKE,
				Db2HostPrivilegeFactories.RevokeRoutine.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.SchemaPrivilege.class, SqlType.CREATE,
				Db2HostPrivilegeFactories.GrantSchema.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.SchemaPrivilege.class, SqlType.GRANT,
				Db2HostPrivilegeFactories.GrantSchema.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.SchemaPrivilege.class, SqlType.REVOKE,
				Db2HostPrivilegeFactories.RevokeSchema.class);
		return registry;
	}

	/**
	 * Diff support: host-correct ALTER and DROP. Additions and removals reuse the
	 * registered CREATE/DROP factories; unsupported in-place changes fail.
	 */
	private static void registerAlterAndDrop(SimpleSqlFactoryRegistry registry) {
		registry.registerSqlFactory(Table.class, SqlType.ALTER, Db2HostAlterTableFactory.class);
		registry.registerSqlFactory(Table.class, SqlType.DROP,
				com.sqlapp.data.db.dialect.db2.sql.Db2DropTableFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Index.class, SqlType.ALTER,
				Db2HostAlterFactories.AlterIndex.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Index.class, SqlType.DROP,
				com.sqlapp.data.db.dialect.db2.sql.Db2DropIndexFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Sequence.class, SqlType.ALTER,
				Db2HostAlterFactories.AlterSequence.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Sequence.class, SqlType.DROP,
				com.sqlapp.data.db.dialect.db2.sql.Db2DropSequenceFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.View.class, SqlType.ALTER,
				Db2HostAlterFactories.CommentOnly.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.View.class, SqlType.DROP,
				com.sqlapp.data.db.dialect.db2.sql.Db2DropViewFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Mview.class, SqlType.ALTER,
				Db2HostAlterFactories.CommentOnly.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Trigger.class, SqlType.ALTER,
				Db2HostAlterFactories.CommentOnly.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Trigger.class, SqlType.DROP,
				com.sqlapp.data.db.dialect.db2.sql.Db2DropTriggerFactory.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Domain.class, SqlType.ALTER,
				Db2HostAlterFactories.CommentOnly.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Synonym.class, SqlType.ALTER,
				Db2HostAlterFactories.CommentOnly.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Mask.class, SqlType.ALTER,
				Db2HostAlterFactories.AlterMask.class);
		registry.registerSqlFactory(com.sqlapp.data.schemas.Mask.class, SqlType.DROP,
				Db2HostAlterFactories.DropMask.class);
	}

	@Override
	public Db2HostSqlBuilder createSqlBuilder() {
		return new Db2HostSqlBuilder(this);
	}

	@Override
	public boolean needQuote(String name) {
		return name != null && !name.isEmpty() && !isQuoted(name);
	}

	@Override
	public String quote(String name) {
		if (name == null || name.isEmpty() || isQuoted(name))
			return name;
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}
}
