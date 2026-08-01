/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.metadata.Oracle23aiCatalogReader;
import com.sqlapp.data.db.dialect.oracle.sql.Oracle23aiSqlFactoryRegistry;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

/**
 * Oracle固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class Oracle23ai extends Oracle21c {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1499333627804019017L;

	/**
	 * コンストラクタ
	 */
	protected Oracle23ai(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	protected void registerDataType() {
		super.registerDataType();
		getDbDataTypes().addBoolean("BOOLEAN", type -> {
			type.setDefaultValueLiteral("FALSE");
		});
		getDbDataTypes().addVector();
		setIndexTypeName("VECTOR", com.sqlapp.data.schemas.IndexType.Vector);
	}

	@Override
	public String getSelectDummyTableName() {
		return null;
	}

	@Override
	public boolean supportsValues() {
		return true;
	}

	@Override
	public boolean supportsIdentitySequencePreallocation() {
		return true;
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new Oracle23aiCatalogReader(this);
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Oracle23aiSqlFactoryRegistry(this);
	}
}
