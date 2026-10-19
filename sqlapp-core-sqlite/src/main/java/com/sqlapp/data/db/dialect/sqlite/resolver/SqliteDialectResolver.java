/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlite.
 *
 * sqlapp-core-sqlite is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlite is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlite.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlite.resolver;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.resolver.VersionResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;

/**
 * Dialect resolver for SQLite
 * 
 * @author satoh
 * 
 */
public class SqliteDialectResolver extends ProductNameDialectResolver {

	public SqliteDialectResolver() {
		super("SQLite", new SQLiteVersionResolver());
	}

	/**
	 * SQLite用のバージョンResolver
	 * 
	 * @author satoh
	 * 
	 */
	static class SQLiteVersionResolver implements VersionResolver {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * コンストラクタ
		 */
		public SQLiteVersionResolver() {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.sqlapp.data.db.dialect.resolver.VersionResolver#getDialect(int, int,
		 * java.lang.Integer)
		 */
		@Override
		public Dialect getDialect(int majorVersion, int minorVersion, Integer revision) {
			if (majorVersion > 3)
				return DialectHolder.defaultDialect;
			if (majorVersion < 3)
				return DialectHolder.sqliteLegacy;
			if (minorVersion >= 37)
				return DialectHolder.sqlite337;
			if (minorVersion >= 35)
				return DialectHolder.sqlite335;
			if (minorVersion >= 32)
				return DialectHolder.sqlite332;
			if (minorVersion >= 31)
				return DialectHolder.sqlite331;
			if (minorVersion >= 25)
				return DialectHolder.sqlite325;
			if (minorVersion >= 24)
				return DialectHolder.sqlite324;
			if (minorVersion >= 15)
				return DialectHolder.sqlite315;
			if (minorVersion >= 9)
				return DialectHolder.sqlite39;
			if (minorVersion == 8) {
				if (revision != null && revision >= 3)
					return DialectHolder.sqlite383;
				if (revision != null && revision >= 2)
					return DialectHolder.sqlite382;
				return DialectHolder.sqlite380;
			}
			return DialectHolder.sqliteLegacy;
		}

	}

}
