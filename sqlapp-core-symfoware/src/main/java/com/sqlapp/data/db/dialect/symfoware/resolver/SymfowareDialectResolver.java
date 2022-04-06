/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-symfoware.
 *
 * sqlapp-core-symfoware is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-symfoware is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-symfoware.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.symfoware.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.resolver.VersionResolver;
import com.sqlapp.data.db.dialect.symfoware.Symfoware;

/**
 * Dialect resolver for Symfoware
 * 
 * @author satoh
 * 
 */
public class SymfowareDialectResolver extends ProductNameDialectResolver {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	public SymfowareDialectResolver() {
		super("Symfoware", new SymfowareVersionResolver());
	}

	/**
	 * Symfoware用のバージョンResolver
	 * 
	 * @author satoh
	 * 
	 */
	static class SymfowareVersionResolver implements VersionResolver {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		static class DialectHolder {
			final static Dialect defaultDialect = DialectUtils
					.getInstance(Symfoware.class);
		}

		/**
		 * コンストラクタ
		 */
		public SymfowareVersionResolver() {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * com.sqlapp.data.db.dialect.resolver.VersionResolver#getDialect(int,
		 * int, java.lang.Integer)
		 */
		@Override
		public Dialect getDialect(int majorVersion, int minorVersion,
				Integer revision) {
			return DialectHolder.defaultDialect;
		}

	}

}
