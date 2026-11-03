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
		return registry;
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
