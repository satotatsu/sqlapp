/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.vertica;

import com.sqlapp.data.db.dialect.Dialect;

public class DialectHolder {
	public final static Dialect defaultDialect12_0_4 = new Vertica12_0_4(() -> null);
	public final static Dialect defaultDialect11_1_1 = new Vertica11_1_1(() -> defaultDialect12_0_4);
	public final static Dialect defaultDialect90 = new Vertica90(() -> defaultDialect11_1_1);
	public final static Dialect defaultDialect80 = new Vertica80(() -> defaultDialect90);
	public final static Dialect defaultDialect72 = new Vertica72(() -> defaultDialect80);
	public final static Dialect defaultDialect = new Vertica(() -> defaultDialect72);
}
