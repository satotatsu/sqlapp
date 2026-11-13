/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i;

import com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.dialect.db2.i.metadata.Db2I75CatalogReader;

/**
 * Experimental Db2 for i 7.5 basic table/key support; not verified against a
 * server. {@link Db2I74} and {@link Db2I76} share the same catalog resources.
 */
public class Db2I75 extends AbstractDb2HostDialect {
	private static final long serialVersionUID = 1L;

	public Db2I75() {
		super();
	}

	@Override
	protected void registerDataType() {
		super.registerDataType();
		if (supportsNativeBoolean())
			getDbDataTypes().addBoolean();
	}

	/** Native BOOLEAN was introduced in IBM i 7.5. Called during construction. */
	protected boolean supportsNativeBoolean() {
		return true;
	}

	@Override
	public Platform getPlatform() {
		return Platform.IBM_I;
	}

	@Override
	public String getProductName() {
		return "Db2 for i";
	}

	@Override
	public String getSimpleName() {
		return "db2-i";
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new Db2I75CatalogReader(this);
	}

	@Override
	protected long maxCharLength() {
		return 32766;
	}

	@Override
	protected long maxVarcharLength() {
		return 32740;
	}

	@Override
	protected long maxGraphicLength() {
		return 16383;
	}

	@Override
	protected long maxVargraphicLength() {
		return 16370;
	}

	@Override
	protected int maxDecimalPrecision() {
		return 63;
	}

	@Override
	public boolean supportsExplicitUpdateRule() {
		return true;
	}

	@Override
	public boolean supportsRuleOnUpdate(com.sqlapp.data.schemas.CascadeRule rule) {
		return rule == com.sqlapp.data.schemas.CascadeRule.None || rule == com.sqlapp.data.schemas.CascadeRule.Restrict;
	}

	@Override
	public boolean supportsRuleOnDelete(com.sqlapp.data.schemas.CascadeRule rule) {
		return super.supportsRuleOnDelete(rule) || rule == com.sqlapp.data.schemas.CascadeRule.SetDefault;
	}
}
