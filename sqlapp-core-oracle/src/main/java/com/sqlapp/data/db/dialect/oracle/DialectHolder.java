/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle;

import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * OracleのDialect。12c以降はMAX_STRING_SIZE=STANDARD(既定)とEXTENDEDのDialectを持つ。
 */
public class DialectHolder {

	public final static Dialect oracle26aiDialect = new Oracle26ai(() -> null);
	public final static Dialect oracle23aiDialect = new Oracle23ai(() -> oracle26aiDialect);
	/**
	 * @deprecated use {@link #oracle23aiDialect}.
	 */
	@Deprecated
	public final static Dialect oracle23cDialect = oracle23aiDialect;
	public final static Dialect oracle21cDialect = new Oracle21c(() -> oracle23aiDialect);
	public final static Dialect oracle19cDialect = new Oracle19c(() -> oracle21cDialect);
	public final static Dialect oracle18cDialect = new Oracle18c(() -> oracle19cDialect);
	public final static Dialect oracle12cDialect = new Oracle12c(() -> oracle18cDialect);
	public final static Dialect oracle11gR2Dialect = new Oracle11gR2(() -> oracle12cDialect);
	public final static Dialect oracle11gDialect = new Oracle11g(() -> oracle11gR2Dialect);
	public final static Dialect oracle10gDialect = new Oracle10g(() -> oracle11gDialect);
	public final static Dialect defaultDialect = new Oracle(() -> oracle10gDialect);

	// MAX_STRING_SIZE=EXTENDED
	public final static Dialect oracle26aiExtendedDialect = new Oracle26ai(() -> null, OracleMaxStringSize.EXTENDED);
	public final static Dialect oracle23aiExtendedDialect = new Oracle23ai(() -> oracle26aiExtendedDialect,
			OracleMaxStringSize.EXTENDED);
	public final static Dialect oracle21cExtendedDialect = new Oracle21c(() -> oracle23aiExtendedDialect,
			OracleMaxStringSize.EXTENDED);
	public final static Dialect oracle19cExtendedDialect = new Oracle19c(() -> oracle21cExtendedDialect,
			OracleMaxStringSize.EXTENDED);
	public final static Dialect oracle18cExtendedDialect = new Oracle18c(() -> oracle19cExtendedDialect,
			OracleMaxStringSize.EXTENDED);
	public final static Dialect oracle12cExtendedDialect = new Oracle12c(() -> oracle18cExtendedDialect,
			OracleMaxStringSize.EXTENDED);

	private static final List<Dialect> STANDARD_DIALECTS = List.of(oracle12cDialect, oracle18cDialect, oracle19cDialect,
			oracle21cDialect, oracle23aiDialect, oracle26aiDialect);

	private static final List<Dialect> EXTENDED_DIALECTS = List.of(oracle12cExtendedDialect, oracle18cExtendedDialect,
			oracle19cExtendedDialect, oracle21cExtendedDialect, oracle23aiExtendedDialect, oracle26aiExtendedDialect);

	/**
	 * 同じバージョンで指定したMAX_STRING_SIZEのDialectを返します。
	 * 
	 * @param dialect       OracleのDialect
	 * @param maxStringSize MAX_STRING_SIZE
	 * @return Dialect
	 * @throws IllegalArgumentException 12c未満でEXTENDEDを指定した場合
	 */
	public static Oracle getDialect(final Dialect dialect, final OracleMaxStringSize maxStringSize) {
		Objects.requireNonNull(maxStringSize, "maxStringSize");
		if (!(dialect instanceof Oracle)) {
			throw new IllegalArgumentException("Not an Oracle dialect: " + dialect);
		}
		final Oracle oracle = (Oracle) dialect;
		if (oracle.getMaxStringSize() == maxStringSize) {
			return oracle;
		}
		final List<Dialect> from = oracle.getMaxStringSize() == OracleMaxStringSize.STANDARD ? STANDARD_DIALECTS
				: EXTENDED_DIALECTS;
		final List<Dialect> to = maxStringSize == OracleMaxStringSize.STANDARD ? STANDARD_DIALECTS : EXTENDED_DIALECTS;
		for (int i = 0; i < from.size(); i++) {
			if (from.get(i).getClass() == oracle.getClass()) {
				return (Oracle) to.get(i);
			}
		}
		throw new IllegalArgumentException("MAX_STRING_SIZE=" + maxStringSize + " requires Oracle 12c or later: "
				+ dialect.getClass().getSimpleName());
	}

}
