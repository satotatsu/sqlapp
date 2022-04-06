/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-virtica.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.virtica;

import static com.sqlapp.data.db.datatype.DataType.LONGVARBINARY;
import static com.sqlapp.data.db.datatype.DataType.UUID;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.virtica.metadata.VirticaCatalogReader;
import com.sqlapp.data.db.dialect.virtica.sql.VirticaSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlSplitter;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

/**
 * Virtica固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class Virtica extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -8635862003765087520L;
	private static final long SIZE_MAX = 65000;
	private static final long SIZE_MAX2 = 32000000;

	protected Virtica(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar(SIZE_MAX).setDefaultLength(1);
		// VARCHAR
		getDbDataTypes().addVarchar(SIZE_MAX).setDefaultLength(1);
		// LONGVARCHAR(非推奨)
		getDbDataTypes().addLongVarchar("LONG VARCHAR", SIZE_MAX2)
				.setDefaultLength(1).setCreateFormat("LONG VARCHAR");
		// UUID
		getDbDataTypes().addUUID("BINARY(16)")
				.setLiteral("'", "'")
				.setFormats("BINARY\\s*\\(\\s*16\\s*\\)\\s*")
				.setAsBinaryType();
		// BOOLEAN
		getDbDataTypes().addBoolean();
		// BINARY
		getDbDataTypes()
				.addBinary("BINARY", SIZE_MAX).setLiteral("X'", "'")
				.setDefaultValueLiteral("X'0'")
				.setSizeSarrogation(16, UUID);
		// VARBINARY
		getDbDataTypes()
				.addVarBinary("VARBINARY", SIZE_MAX).setLiteral("X'", "'")
				.setDefaultValueLiteral("X'0'");
		// LONGVARBINARY(非推奨)
		getDbDataTypes().addVarBinary("LONG VARCHAR FOR BIT DATA", 32700)
				.setDefaultLength(32700)
				.setCreateFormat("LONG VARCHAR FOR BIT DATA")
				.setFormats("LONG VARCHAR FOR BIT DATA").setLiteral("X'", "'")
				.setDefaultValueLiteral("X'0'")
				.setDeprecated(getDbDataTypes().getDbType(LONGVARBINARY));
		// BIGINT
		getDbDataTypes().addBigInt().addFormats("INTEGER")
			.addFormats("INT").addFormats("INT8").addFormats("SMALLINT");
		// Double
		getDbDataTypes().addDouble().addFormats("DOUBLE PRECISION")
			.addFormats("FLOAT").addFormats("REAL").addFormats("FLOAT\\s*\\(.*\\)\\s*");
		// Date
		getDbDataTypes().addDate().setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentDateFunction());
		// Time
		getDbDataTypes().addTime().setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentTimeFunction());
		// Time WITH TIMEZONE
		getDbDataTypes().addTimeWithTimeZone().setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentTimeFunction());
		// Timestamp
		getDbDataTypes().addTimestamp().setLiteral("'", "'").addFormats("DATETIME").addFormats("SMALLDATETIME")
				.setDefaultValueLiteral(getCurrentTimestampFunction());
		// Timestamp WITH TIMEZONE
		getDbDataTypes().addTimestampWithTimeZoneType().setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentTimestampFunction());
		// Decimal
		getDbDataTypes().addDecimal().setDefaultPrecision(37)
				.setDefaultScale(15).setMaxPrecision(31).setMaxScale(31);
		// Numeric
		getDbDataTypes().addNumeric().setDefaultPrecision(37)
				.setDefaultScale(15).setMaxPrecision(31).setMaxScale(31);
		// INTERVAL
		getDbDataTypes().addInterval();
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
		return new VirticaCatalogReader(this);
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
		return new VirticaSqlFactoryRegistry(this);
	}
	
	@Override
	public VirticaSqlBuilder createSqlBuilder(){
		return new VirticaSqlBuilder(this);
	}
	
	@Override
	public VirticaSqlSplitter createSqlSplitter(){
		return new VirticaSqlSplitter(this);
	}
	
	@Override
	protected String doQuote(final String target){
		final StringBuilder builder = new StringBuilder(target.length() + 2);
		builder.append(getOpenQuote()).append(target.replace("\"", "\"\"")).append(getCloseQuote());
		return builder.toString();
	}
}
