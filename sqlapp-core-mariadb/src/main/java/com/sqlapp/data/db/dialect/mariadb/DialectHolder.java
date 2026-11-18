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

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.MySql;
import com.sqlapp.data.db.dialect.mysql.MySqlTinyInt1Mapping;

public class DialectHolder {

	public final static Mariadb12_10 mariadb12_10Dialect = new Mariadb12_10(() -> null);
	public final static Mariadb11_80 mariadb11_80Dialect = new Mariadb11_80(() -> mariadb12_10Dialect);
	public final static Mariadb11_50 mariadb11_50Dialect = new Mariadb11_50(() -> mariadb11_80Dialect);
	public final static Mariadb11_40 mariadb11_40Dialect = new Mariadb11_40(() -> mariadb11_50Dialect);
	public final static Mariadb10_50 mariadb10_50Dialect = new Mariadb10_50(() -> mariadb11_40Dialect);
	public final static Mariadb10_30 mariadb10_30Dialect = new Mariadb10_30(() -> mariadb10_50Dialect);
	public final static Mariadb10_27 mariadb10_27Dialect = new Mariadb10_27(() -> mariadb10_30Dialect);
	public final static Mariadb10_25 mariadb10_25Dialect = new Mariadb10_25(() -> mariadb10_27Dialect);
	public final static Mariadb10_20 mariadb10_20Dialect = new Mariadb10_20(() -> mariadb10_25Dialect);
	public final static Mariadb10_05 mariadb10_05Dialect = new Mariadb10_05(() -> mariadb10_20Dialect);
	public final static Mariadb10_00 mariadb10_00Dialect = new Mariadb10_00(() -> mariadb10_05Dialect);
	public final static Mariadb defaultDialect = new Mariadb(() -> mariadb10_00Dialect);

	// Numeric variants retain their own next-version chain.
	private static final MySql mariadb12_10DialectNumeric = numeric(() -> new Mariadb12_10(() -> null));
	private static final MySql mariadb11_80DialectNumeric = numeric(() -> new Mariadb11_80(() -> mariadb12_10DialectNumeric));
	private static final MySql mariadb11_50DialectNumeric = numeric(() -> new Mariadb11_50(() -> mariadb11_80DialectNumeric));
	private static final MySql mariadb11_40DialectNumeric = numeric(() -> new Mariadb11_40(() -> mariadb11_50DialectNumeric));
	private static final MySql mariadb10_50DialectNumeric = numeric(() -> new Mariadb10_50(() -> mariadb11_40DialectNumeric));
	private static final MySql mariadb10_30DialectNumeric = numeric(() -> new Mariadb10_30(() -> mariadb10_50DialectNumeric));
	private static final MySql mariadb10_27DialectNumeric = numeric(() -> new Mariadb10_27(() -> mariadb10_30DialectNumeric));
	private static final MySql mariadb10_25DialectNumeric = numeric(() -> new Mariadb10_25(() -> mariadb10_27DialectNumeric));
	private static final MySql mariadb10_20DialectNumeric = numeric(() -> new Mariadb10_20(() -> mariadb10_25DialectNumeric));
	private static final MySql mariadb10_05DialectNumeric = numeric(() -> new Mariadb10_05(() -> mariadb10_20DialectNumeric));
	private static final MySql mariadb10_00DialectNumeric = numeric(() -> new Mariadb10_00(() -> mariadb10_05DialectNumeric));
	private static final MySql defaultDialectNumeric = numeric(() -> new Mariadb(() -> mariadb10_00DialectNumeric));

	private static MySql numeric(final Supplier<? extends MySql> factory) {
		return Mariadb.configureNumeric(factory);
	}

	/** Resolves the same version with an isolated TINYINT(1) interpretation. */
	public static MySql getDialect(final Dialect dialect, final MySqlTinyInt1Mapping mapping) {
		Objects.requireNonNull(mapping, "mapping");
		if (!(dialect instanceof MySql current) || !"MariaDB".equals(current.getProductName())) throw new IllegalArgumentException("Unexpected dialect: " + dialect);
		if (current.getTinyInt1Mapping() == mapping) return current;
		final List<MySql> original = List.of(
				mariadb12_10Dialect,
				mariadb11_80Dialect,
				mariadb11_50Dialect,
				mariadb11_40Dialect,
				mariadb10_50Dialect,
				mariadb10_30Dialect,
				mariadb10_27Dialect,
				mariadb10_25Dialect,
				mariadb10_20Dialect,
				mariadb10_05Dialect,
				mariadb10_00Dialect,
				defaultDialect);
		final List<MySql> numeric = List.of(
				mariadb12_10DialectNumeric,
				mariadb11_80DialectNumeric,
				mariadb11_50DialectNumeric,
				mariadb11_40DialectNumeric,
				mariadb10_50DialectNumeric,
				mariadb10_30DialectNumeric,
				mariadb10_27DialectNumeric,
				mariadb10_25DialectNumeric,
				mariadb10_20DialectNumeric,
				mariadb10_05DialectNumeric,
				mariadb10_00DialectNumeric,
				defaultDialectNumeric);
		for (int i = 0; i < original.size(); i++) {
			if (original.get(i).getClass() == current.getClass())
				return mapping == MySqlTinyInt1Mapping.NUMERIC ? numeric.get(i) : original.get(i);
		}
		throw new IllegalArgumentException("Unsupported dialect version: " + current.getClass().getName());
	}

}
