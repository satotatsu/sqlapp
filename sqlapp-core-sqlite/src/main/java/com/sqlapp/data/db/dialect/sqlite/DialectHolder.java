/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.db.dialect.sqlite;

import com.sqlapp.data.db.dialect.Dialect;

public class DialectHolder {
	public final static Dialect sqlite3531 = new SqliteVersions.Sqlite3531(() -> null);
	public final static Dialect sqlite353 = new SqliteVersions.Sqlite353(() -> sqlite3531);
	public final static Dialect sqlite346 = new SqliteVersions.Sqlite346(() -> sqlite353);
	public final static Dialect sqlite337 = new SqliteVersions.Sqlite337(() -> sqlite346);
	public final static Dialect sqlite335 = new SqliteVersions.Sqlite335(() -> sqlite337);
	public final static Dialect sqlite332 = new SqliteVersions.Sqlite332(() -> sqlite335);
	public final static Dialect sqlite331 = new SqliteVersions.Sqlite331(() -> sqlite332);
	public final static Dialect sqlite327 = new SqliteVersions.Sqlite327(() -> sqlite331);
	public final static Dialect sqlite325 = new SqliteVersions.Sqlite325(() -> sqlite327);
	public final static Dialect sqlite324 = new SqliteVersions.Sqlite324(() -> sqlite325);
	public final static Dialect sqlite318 = new SqliteVersions.Sqlite318(() -> sqlite324);
	public final static Dialect sqlite315 = new SqliteVersions.Sqlite315(() -> sqlite318);
	public final static Dialect sqlite39 = new SqliteVersions.Sqlite39(() -> sqlite315);
	public final static Dialect sqlite388 = new SqliteVersions.Sqlite388(() -> sqlite39);
	public final static Dialect sqlite383 = new SqliteVersions.Sqlite383(() -> sqlite388);
	public final static Dialect sqlite382 = new SqliteVersions.Sqlite382(() -> sqlite383);
	public final static Dialect sqlite380 = new SqliteVersions.Sqlite380(() -> sqlite382);
	public final static Dialect sqlite3716 = new SqliteVersions.Sqlite3716(() -> sqlite380);
	public final static Dialect sqlite376 = new SqliteVersions.Sqlite376(() -> sqlite3716);
	public final static Dialect sqliteLegacy = new Sqlite(() -> sqlite376);
	/**
	 * Unversioned callers retain modern capabilities. Resolve a connection for a
	 * concrete target.
	 */
	public final static Dialect defaultDialect = sqlite3531;
}
