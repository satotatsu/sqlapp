/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-spanner.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.spanner;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.spanner.metadata.SpannerCatalogReader;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlSplitter;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;

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
		getDbDataTypes().setArrayDimensionHandler((matcher,column)->{
			final String dataTypeName=matcher.group("dataTypeName");
			final String upper=dataTypeName.toUpperCase();
			column.setArrayDimension(1);
			if (column instanceof DataTypeLengthProperties) {
				final DataTypeLengthProperties<?> col=(DataTypeLengthProperties<?>)column;
				if (upper.startsWith("STRING")) {
					final String length=matcher.group("length");
					if ("max".equalsIgnoreCase(length)) {
						col.setLength(SIZE_MAX);
					} else {
						col.setLength(Long.parseLong(length));
					}
				} if (upper.startsWith("BYTES")) {
					final String length=matcher.group("length").toUpperCase();
					if ("max".equalsIgnoreCase(length)) {
						col.setLength(SIZE_MAX2);
					} else {
						col.setLength(Long.parseLong(length));
					}
				}
			}
		});
		getDbDataTypes().setArrayPatternGenerator(dataTypeName->{
			final String upper=dataTypeName.toUpperCase();
			if (upper.startsWith("STRING")) {
				return "ARRAY<(?<dataTypeName>STRING\\(\\s*(?<length>max|[0-9]+)\\s*\\))>";
			} if (upper.startsWith("BYTES")) {
				return "ARRAY<(?<dataTypeName>BYTES\\(\\s*(?<length>max|[0-9]+)\\s*\\))>";
			}
			return "ARRAY<(?<dataTypeName>"+dataTypeName+")>";
		});
		// VARCHAR
		getDbDataTypes().addVarchar("STRING", SIZE_MAX-1).setDefaultLength(1);
		// VARCHAR
		getDbDataTypes().addVarchar("STRING(MAX)", SIZE_MAX).setFormats("STRING\\(\\s*MAX\\s*\\)")
			.setCreateFormat("STRING(MAX)").setDefaultLength(SIZE_MAX);
		// BOOLEAN
		getDbDataTypes().addBoolean("BOOL");
		// VARBINARY
		getDbDataTypes()
				.addVarBinary("BYTES", SIZE_MAX2-1).setLiteral("X'", "'")
				.setDefaultValueLiteral("X'0'");
		// VARBINARY
		getDbDataTypes().addVarBinary("BYTES(MAX)", SIZE_MAX2).setFormats("BYTES\\(\\s*MAX\\s*\\)")
			.setCreateFormat("BYTES(MAX)").setDefaultLength(SIZE_MAX2).setLiteral("X'", "'")
		.setDefaultValueLiteral("X'0'");
		// BIGINT
		getDbDataTypes().addBigInt("INT64");
		// Date
		getDbDataTypes().addDate().setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentDateFunction());
		// Timestamp
		getDbDataTypes().addTimestamp("TIMESTAMP").setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentTimestampFunction());
		// FLOAT64
		getDbDataTypes().addDouble("FLOAT64");
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
	public SpannerSqlBuilder createSqlBuilder(){
		return new SpannerSqlBuilder(this);
	}
	
	@Override
	public SpannerSqlSplitter createSqlSplitter(){
		return new SpannerSqlSplitter(this);
	}
	
	@Override
	protected String doQuote(final String target){
		final StringBuilder builder = new StringBuilder(target.length() + 2);
		builder.append(getOpenQuote()).append(target.replace("\"", "\"\"")).append(getCloseQuote());
		return builder.toString();
	}
}
