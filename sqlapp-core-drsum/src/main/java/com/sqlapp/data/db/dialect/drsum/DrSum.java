/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.drsum;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.DefaultCase;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.drsum.metadata.DrSumCatalogReader;
import com.sqlapp.data.db.dialect.drsum.sql.DrSumSqlFactoryRegistry;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

/**
 * Experimental WingArc1st Dr.Sum (Dr.Sum Server 5.x) dialect.
 * <p>
 * Built from the Dr.Sum 5.7 SQL reference; not verified against a server.
 * Dr.Sum characteristics reflected here:
 * <ul>
 * <li>One database per connection and no schema hierarchy.</li>
 * <li>Identifiers are case-sensitive and quoted with {@code []} (or back
 * quotes).</li>
 * <li>Every numeric type except REAL is stored as NUMERIC; INTEGER, SMALLINT and
 * TINYINT become NUMERIC(33,0). CHAR is stored as VARCHAR.</li>
 * <li>Table VARCHAR columns accept 1 to 255 characters; an unsized VARCHAR holds
 * up to 65,535 characters.</li>
 * <li>Constraints are NOT NULL, UNIQUE, DEFAULT (literal only) and the named
 * multi-column COMPOUND KEY. PRIMARY KEY, FOREIGN KEY and CHECK do not
 * exist.</li>
 * <li>Search indexes cover exactly one column.</li>
 * </ul>
 */
public class DrSum extends Dialect {

	private static final long serialVersionUID = 1L;

	/** Maximum length of a sized VARCHAR column in a table definition. */
	public static final int MAX_TABLE_VARCHAR_LENGTH = 255;
	/** Maximum length of an unsized VARCHAR. */
	public static final int MAX_VARCHAR_LENGTH = 65535;
	/** Maximum NUMERIC precision. */
	public static final int MAX_NUMERIC_PRECISION = 33;
	/** Maximum NUMERIC scale in a table definition. */
	public static final int MAX_NUMERIC_SCALE = 31;
	/** Maximum fractional second digits of TIME, TIMESTAMP and INTERVAL. */
	public static final int MAX_FRACTIONAL_SECONDS = 6;
	/** Maximum identifier length. */
	public static final int MAX_IDENTIFIER_LENGTH = 63;

	protected DrSum(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	protected void registerDataType() {
		// Sized VARCHAR (1-255 characters in a table definition).
		getDbDataTypes().addVarchar(MAX_TABLE_VARCHAR_LENGTH, type -> {
			type.addColumnTypeMatcher("CHAR");
			type.addColumnTypeMatcher("WVARCHAR");
			type.addColumnTypeMatcher("VARYING CHARACTER");
			type.setLiteral("'", "'");
		});
		// Unsized VARCHAR (up to 65,535 characters).
		getDbDataTypes().addLongVarchar("VARCHAR", MAX_VARCHAR_LENGTH, type -> {
			type.setCreateFormat("VARCHAR");
			type.setLiteral("'", "'");
		});
		// Integer types are accepted in CREATE TABLE and stored as NUMERIC(33,0).
		getDbDataTypes().addInt(type -> {
			type.setCreateFormat("INTEGER");
		});
		// Dr.Sum has no BIGINT keyword; NUMERIC(19,0) holds every BIGINT value.
		getDbDataTypes().addBigInt(type -> {
			type.setPetternColumnTypeMatcher("NUMERIC\\s*\\(\\s*19\\s*,\\s*0\\s*\\)");
			type.setCreateFormat("NUMERIC(19,0)");
		});
		// REAL, FLOAT and DOUBLE are the same REAL type.
		getDbDataTypes().addReal(type -> {
			type.addColumnTypeMatcher("FLOAT");
		});

		getDbDataTypes().addDouble(type -> {
			type.setColumnTypeMatcher("DOUBLE");
			type.setCreateFormat("DOUBLE");
		});
		getDbDataTypes().addDecimal(type -> {
			type.setMaxPrecision(MAX_NUMERIC_PRECISION).setMaxScale(MAX_NUMERIC_SCALE);
		});
		getDbDataTypes().addNumeric(type -> {
			type.setMaxPrecision(MAX_NUMERIC_PRECISION).setMaxScale(MAX_NUMERIC_SCALE);
		});
		getDbDataTypes().addDate(type -> {
			type.setLiteral("DATE '", "'");
		});
		getDbDataTypes().addTime(type -> {
			type.setMaxPrecision(MAX_FRACTIONAL_SECONDS).setDefaultPrecision(0);
			type.setLiteral("TIME '", "'");
		});
		getDbDataTypes().addTimestamp(type -> {
			type.setMaxPrecision(MAX_FRACTIONAL_SECONDS).setDefaultPrecision(0);
			type.setLiteral("TIMESTAMP '", "'");
		});
		// INTERVAL is a day-to-second interval ('DD HH:MI:SS.FF').
		getDbDataTypes().addIntervalDayToSecond(type -> {
			type.setColumnTypeMatcher("INTERVAL");
			type.setCreateFormat("INTERVAL");
			type.setLiteral("INTERVAL '", "'");
		});
	}

	@Override
	public String getProductName() {
		return "Dr.Sum";
	}

	@Override
	public String getSimpleName() {
		return "drsum";
	}

	@Override
	public char getOpenQuote() {
		return '[';
	}

	@Override
	public char getCloseQuote() {
		return ']';
	}

	@Override
	public DefaultCase getDefaultCase() {
		return DefaultCase.NonConvert;
	}

	@Override
	public boolean storesMixedCaseIdentifiers() {
		return true;
	}

	@Override
	public boolean supportsDefaultValueFunction() {
		return false;
	}

	@Override
	public boolean supportsDropCascade() {
		return false;
	}

	@Override
	public boolean supportsCascadeDelete() {
		return false;
	}

	@Override
	public boolean supportsCascadeUpdate() {
		return false;
	}

	@Override
	public boolean supportsIndexNameTableScope() {
		return false;
	}

	@Override
	public boolean isDdlRollbackable() {
		return false;
	}

	@Override
	public String getSelectDummyTableName() {
		return "__dw__";
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new DrSumCatalogReader(this);
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new DrSumSqlFactoryRegistry(this);
	}

	@Override
	public boolean equals(final Object obj) {
		return super.equals(obj);
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}
}
