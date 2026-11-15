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
import com.sqlapp.data.db.dialect.oracle.metadata.Oracle12cCatalogReader;
import com.sqlapp.data.db.dialect.oracle.sql.Oracle12cOperationFactoryRegistry;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.Table;

/**
 * Oracle固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class Oracle12c extends Oracle11gR2 {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 3964617907829424615L;

	/**
	 * コンストラクタ
	 */
	protected Oracle12c(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param nextVersionDialectSupplier 次のバージョンのDialect
	 * @param maxStringSize              MAX_STRING_SIZE
	 */
	protected Oracle12c(final Supplier<Dialect> nextVersionDialectSupplier, final OracleMaxStringSize maxStringSize) {
		super(nextVersionDialectSupplier, maxStringSize);
	}

	/**
	 * MAX_STRING_SIZE=EXTENDEDは12cから
	 */
	@Override
	public boolean supportsExtendedMaxStringSize() {
		return true;
	}

	@Override
	public String getIdentityInsertDefaultValue(final Column column) {
		if (column.getIdentityGenerationType() == IdentityGenerationType.ByDefault) {
			return "default";
		}
		return null;
	}

	@Override
	public String[] getGeneratedKeyColumnNames(final Table table, final Column identityColumn) {
		return new String[] { identityColumn.getName() };
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new Oracle12cCatalogReader(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.Dialect#createDbOperationFactory()
	 */
	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Oracle12cOperationFactoryRegistry(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.Dialect#supportsStandardOffsetFetchRows()
	 */
	@Override
	public boolean supportsStandardOffsetFetchRows() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.Dialect#supportsIdentity()
	 */
	@Override
	public boolean supportsIdentity() {
		return true;
	}

}
