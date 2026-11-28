/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-phoenix.
 *
 * sqlapp-core-phoenix is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-phoenix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-phoenix.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.phoenix;

import com.sqlapp.data.db.dialect.Dialect;

public class DialectHolder {
	public final static Dialect defaultDialect5_3_1 = new Phoenix5_3_1(() -> null);
	public final static Dialect defaultDialect5_3 = new Phoenix5_3(() -> defaultDialect5_3_1);
	public final static Dialect defaultDialect4_9 = new Phoenix4_9(() -> defaultDialect5_3);
	public final static Dialect defaultDialect4_8 = new Phoenix4_8(() -> defaultDialect4_9);
	public final static Dialect defaultDialect4_7 = new Phoenix4_7(() -> defaultDialect4_8);
	public final static Dialect defaultDialect4_3 = new Phoenix4_3(() -> defaultDialect4_7);
	public final static Dialect defaultDialect = new Phoenix(() -> defaultDialect4_3);
}
