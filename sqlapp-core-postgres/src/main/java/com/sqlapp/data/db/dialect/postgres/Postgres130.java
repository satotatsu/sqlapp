/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.postgres;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.Postgres130CatalogReader;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

public class Postgres130 extends Postgres120 {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	protected Postgres130(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.Postgres83#registerDataType()
	 */
	@Override
	protected void registerDataType() {
		super.registerDataType();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.Postgres93#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new Postgres130CatalogReader(this);
	}
	
	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Postgres130SqlFactoryRegistry(this);
	}
}
