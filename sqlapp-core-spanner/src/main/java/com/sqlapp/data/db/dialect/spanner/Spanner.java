/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-spanner.
 *
 * sqlapp-core-spanner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-spanner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-spanner.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.spanner;

import java.util.function.Function;
import java.util.function.Supplier;

import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.spanner.db.datatype.util.SpannerArrayColumnTypeMatcher;
import com.sqlapp.data.db.dialect.spanner.metadata.SpannerCatalogReader;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlSplitter;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

/**
 * Spanner
 * 
 * @author SATOH
 * 
 */
public class Spanner extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -8635862003765087520L;
	private static final long SIZE_MAX = 2621440;
	private static final long SIZE_MAX2 = 10485760;

	protected static Function<ColumnTypeMatcher, ColumnTypeMatcher> columnTypeMatcherConverter = (
			matcher) -> new SpannerArrayColumnTypeMatcher(matcher);

	/**
	 * コンストラクタ
	 */
	protected Spanner(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// VARCHAR
		getDbDataTypes().addVarchar("STRING", SIZE_MAX - 1, type -> {
			type.setDefaultLength(1);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// VARCHAR
		getDbDataTypes().addVarchar("STRING(MAX)", SIZE_MAX, type -> {
			type.setDefaultLength(SIZE_MAX);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// BOOLEAN
		getDbDataTypes().addBoolean("BOOL", type -> {
			type.addColumnTypeMatcher("BOOLEAN");
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// VARBINARY
		getDbDataTypes().addVarBinary("BYTES", SIZE_MAX2 - 1, type -> {
			type.setLiteral("X'", "'").setDefaultValueLiteral("X'0'");
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// VARBINARY
		getDbDataTypes().addVarBinary("BYTES(MAX)", SIZE_MAX2, type -> {
			type.setCreateFormat("BYTES(MAX)").setDefaultLength(SIZE_MAX2).setLiteral("X'", "'")
					.setDefaultValueLiteral("X'0'");
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// BIGINT
		getDbDataTypes().addBigInt("INT64", type -> {
		});
		// Date
		getDbDataTypes().addDate(type -> {
			type.setLiteral("'", "'").setDefaultValueLiteral(getCurrentDateFunction());
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Timestamp
		getDbDataTypes().addTimestamp("TIMESTAMP", type -> {
			type.setLiteral("'", "'").setDefaultValueLiteral(getCurrentTimestampFunction());
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// FLOAT64
		getDbDataTypes().addDouble("FLOAT64", type -> {
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
	}

	/**
	 * DB名
	 */
	@Override
	public String getProductName() {
		return "Virtica";
	}

	/**
	 * DB製品名(シンプル名)
	 */
	@Override
	public String getSimpleName() {
		return "virtica";
	}

	@Override
	public boolean supportsWith() {
		return true;
	}

	/**
	 * 現在日付の取得関数
	 */
	@Override
	public String getCurrentDateFunction() {
		return "CURRENT_DATE";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new SpannerCatalogReader(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#hashCode()
	 */
	@Override
	public int hashCode() {
		return this.getProductName().hashCode();
	}

	/**
	 * 同値判定
	 */
	@Override
	public boolean equals(final Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		return true;
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new SpannerSqlFactoryRegistry(this);
	}

	@Override
	public SpannerSqlBuilder createSqlBuilder() {
		return new SpannerSqlBuilder(this);
	}

	@Override
	public SpannerSqlSplitter createSqlSplitter() {
		return new SpannerSqlSplitter(this);
	}

	@Override
	protected String doQuote(final String target) {
		final StringBuilder builder = new StringBuilder(target.length() + 2);
		builder.append(getOpenQuote()).append(target.replace("\"", "\"\"")).append(getCloseQuote());
		return builder.toString();
	}
}
