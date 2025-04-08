/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql;

import com.sqlapp.data.db.dialect.Dialect;

public class DialectHolder {
	public final static Dialect defaultDialect2_4_0 = new Hsql2_4_0(() -> null);
	public final static Dialect defaultDialect2_3_4 = new Hsql2_3_4(() -> defaultDialect2_4_0);
	public final static Dialect defaultDialect2_3_0 = new Hsql2_3_0(() -> defaultDialect2_3_4);
	public final static Dialect defaultDialect2_2_0 = new Hsql2_2_0(() -> defaultDialect2_3_0);
	public final static Dialect defaultDialect2_1_0 = new Hsql2_1_0(() -> defaultDialect2_2_0);
	public final static Dialect defaultDialect2_0_0 = new Hsql2_0_0(() -> defaultDialect2_1_0);
	public final static Dialect defaultDialect = new Hsql(() -> defaultDialect2_0_0);
}
