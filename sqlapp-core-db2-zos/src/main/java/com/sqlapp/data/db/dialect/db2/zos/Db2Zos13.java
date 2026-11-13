/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.zos;

import com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.dialect.db2.zos.metadata.Db2Zos13CatalogReader;

/**
 * Experimental Db2 for z/OS 13.0 basic table/key support; not verified against
 * a server.
 */
public class Db2Zos13 extends AbstractDb2HostDialect {
	private static final long serialVersionUID = 1L;

	public Db2Zos13() {
		super();
	}

	@Override
	public Platform getPlatform() {
		return Platform.ZOS;
	}

	@Override
	public String getProductName() {
		return "Db2 for z/OS";
	}

	@Override
	public String getSimpleName() {
		return "db2-zos";
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new Db2Zos13CatalogReader(this);
	}
}
