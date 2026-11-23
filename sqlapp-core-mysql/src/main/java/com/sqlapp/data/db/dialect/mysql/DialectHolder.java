/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql;

import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;

public class DialectHolder {
	public final static Dialect mysql900Dialect = new MySql900(() -> null);
	public final static Dialect mysql840Dialect = new MySql840(() -> mysql900Dialect);
	public final static Dialect mysql8019Dialect = new MySql8019(() -> mysql840Dialect);
	public final static Dialect mysql801Dialect = new MySql801(() -> mysql8019Dialect);
	public final static Dialect mysql800Dialect = new MySql800(() -> mysql801Dialect);
	public final static Dialect mysql570Dialect = new MySql570(() -> mysql800Dialect);
	public final static Dialect mysql565Dialect = new MySql565(() -> mysql570Dialect);
	public final static Dialect mysql564Dialect = new MySql564(() -> mysql565Dialect);
	public final static Dialect defaultDialect = new MySql(() -> mysql564Dialect);

	// Numeric variants retain their own next-version chain.
	private static final MySql mysql900DialectNumeric = numeric(new MySql900(() -> null));
	private static final MySql mysql840DialectNumeric = numeric(new MySql840(() -> mysql900DialectNumeric));
	private static final MySql mysql8019DialectNumeric = numeric(new MySql8019(() -> mysql840DialectNumeric));
	private static final MySql mysql801DialectNumeric = numeric(new MySql801(() -> mysql8019DialectNumeric));
	private static final MySql mysql800DialectNumeric = numeric(new MySql800(() -> mysql801DialectNumeric));
	private static final MySql mysql570DialectNumeric = numeric(new MySql570(() -> mysql800DialectNumeric));
	private static final MySql mysql565DialectNumeric = numeric(new MySql565(() -> mysql570DialectNumeric));
	private static final MySql mysql564DialectNumeric = numeric(new MySql564(() -> mysql565DialectNumeric));
	private static final MySql defaultDialectNumeric = numeric(new MySql(() -> mysql564DialectNumeric));

	private static MySql numeric(final MySql dialect) {
		dialect.useNumericTinyInt1Mapping();
		return dialect;
	}

	/** Resolves the same version with an isolated TINYINT(1) interpretation. */
	public static MySql getDialect(final Dialect dialect, final MySqlTinyInt1Mapping mapping) {
		Objects.requireNonNull(mapping, "mapping");
		if (!(dialect instanceof MySql current) || !"MySQL".equals(current.getProductName()))
			throw new IllegalArgumentException("Unexpected dialect: " + dialect);
		if (current.getTinyInt1Mapping() == mapping)
			return current;
		final List<Dialect> original = List.of(mysql900Dialect, mysql840Dialect, mysql8019Dialect, mysql801Dialect,
				mysql800Dialect, mysql570Dialect, mysql565Dialect, mysql564Dialect, defaultDialect);
		final List<MySql> numeric = List.of(mysql900DialectNumeric, mysql840DialectNumeric, mysql8019DialectNumeric,
				mysql801DialectNumeric, mysql800DialectNumeric, mysql570DialectNumeric, mysql565DialectNumeric,
				mysql564DialectNumeric, defaultDialectNumeric);
		for (int i = 0; i < original.size(); i++) {
			if (original.get(i).getClass() == current.getClass())
				return mapping == MySqlTinyInt1Mapping.NUMERIC ? numeric.get(i) : (MySql) original.get(i);
		}
		throw new IllegalArgumentException("Unsupported dialect version: " + current.getClass().getName());
	}

}
