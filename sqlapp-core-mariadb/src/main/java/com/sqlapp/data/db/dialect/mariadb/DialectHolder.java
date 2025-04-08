/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mariadb.
 *
 * sqlapp-core-mariadb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mariadb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mariadb.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mariadb;

public class DialectHolder {

	public final static Mariadb10_27 mariadb10_27Dialect = new Mariadb10_27(() -> null);
	public final static Mariadb10_25 mariadb10_25Dialect = new Mariadb10_25(() -> mariadb10_27Dialect);
	public final static Mariadb10_00 mariadb10_00Dialect = new Mariadb10_00(() -> mariadb10_25Dialect);
	public final static Mariadb defaultDialect = new Mariadb(() -> mariadb10_00Dialect);

}
