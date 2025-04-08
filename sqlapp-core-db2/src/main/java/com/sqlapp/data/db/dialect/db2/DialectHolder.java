/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2;

public class DialectHolder {
	public final static Db2_1110 Db2_1110Dialect = new Db2_1110(() -> null);
	public final static Db2_1050 Db2_1050Dialect = new Db2_1050(() -> Db2_1110Dialect);
	public final static Db2_1010 Db2_1010Dialect = new Db2_1010(() -> Db2_1050Dialect);
	public final static Db2_980 Db2_980Dialect = new Db2_980(() -> Db2_1010Dialect);
	public final static Db2_970 Db2_970Dialect = new Db2_970(() -> Db2_980Dialect);
	public final static Db2_950 Db2_950Dialect = new Db2_950(() -> Db2_970Dialect);
	public final static Db2 defaultDialect = new Db2(() -> Db2_950Dialect);
}
