/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.util.TypeInformation;
import com.sqlapp.data.db.dialect.phoenix.metadata.PhoenixCatalogReader;
import com.sqlapp.data.db.dialect.phoenix.sql.PhoenixSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.util.CommonUtils;

/**
 * Phoenix固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class Phoenix extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -8635862003765087520L;
	private static final long SIZE_MAX = LEN_2GB - 1;

	/**
	 * コンストラクタ
	 * 
	 * @param nextVersionDialectSupplier 次のバージョンのDialect
	 */
	protected Phoenix(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar(SIZE_MAX, type -> {
			type.setDefaultLength(1).setCharset("UTF-8");
		});
		// VARCHAR
		getDbDataTypes().addVarchar(SIZE_MAX, type -> {
			type.setCharset("UTF-8");
		});
		// BOOLEAN
		getDbDataTypes().addBoolean();
		// BINARY
		getDbDataTypes().addBinary(SIZE_MAX, type -> {
		});
		// VARBINARY
		getDbDataTypes().addVarBinary(SIZE_MAX, type -> {
		});
		// TINYINT
		getDbDataTypes().addTinyInt(type -> {
		});
		// UNSIGNED TINYINT
		getDbDataTypes().addUTinyInt("UNSIGNED_TINYINT", type -> {
			type.setType(Byte.class);
		});
		// SMALLINT
		getDbDataTypes().addSmallInt(type -> {
		});
		// UNSIGNED SMALLINT
		getDbDataTypes().addUSmallInt("UNSIGNED_SMALLINT", type -> {
			type.setType(Short.class);
		});
		// INT
		getDbDataTypes().addInt("INTEGER", type -> {
		});
		// UNSIGNED INT
		getDbDataTypes().addUInt("UNSIGNED_INT", type -> {
			type.setType(Integer.class);
		});
		// BIGINT
		getDbDataTypes().addBigInt(type -> {
		});
		// UNSIGNED_LONG
		getDbDataTypes().addUBigInt("UNSIGNED_LONG", type -> {
			type.setType(Long.class);
		});
		// REAL
		getDbDataTypes().addReal("FLOAT", type -> {
		});
		// Double
		getDbDataTypes().addDouble(type -> {
		});
		// Date
		getDbDataTypes().addDate(type -> {
			type.setLiteral("'", "'").setDefaultValueLiteral(getCurrentDateFunction());
		});
		// Time
		getDbDataTypes().addTime(type -> {
			type.setLiteral("'", "'").setDefaultValueLiteral(getCurrentTimeFunction());
		});
		// Timestamp
		getDbDataTypes().addTimestamp(type -> {
			type.setLiteral("'", "'").setDefaultValueLiteral(getCurrentTimestampFunction());
		});
		// Decimal
		getDbDataTypes().addDecimal(type -> {
			type.setDefaultPrecision(19).setDefaultScale(5).setMaxPrecision(38).setMaxScale(38);
		});
	}

	/**
	 * DB名
	 */
	@Override
	public String getProductName() {
		return "Apache Phoenix";
	}

	/** Retains native encodings while using the shared scalar/array model. */
	@Override
	public java.util.Optional<TypeInformation> matchDbType(final DataType dataType, final String productDataType,
			final Long length, final Integer scale) {
		if (productDataType == null)
			return super.matchDbType(dataType, null, length, scale);
		String name = productDataType.strip().toUpperCase(java.util.Locale.ROOT);
		final var array = arrayType(name);
		final boolean isArray = array.matches();
		if (isArray)
			name = array.group(1).strip();
		final String base = switch (name) {
		case "UNSIGNED_FLOAT" -> "FLOAT";
		case "UNSIGNED_DOUBLE" -> "DOUBLE";
		case "UNSIGNED_DATE" -> "DATE";
		case "UNSIGNED_TIME" -> "TIME";
		case "UNSIGNED_TIMESTAMP" -> "TIMESTAMP";
		default -> name;
		};
		if (!isArray && base.equals(name))
			return super.matchDbType(dataType, productDataType, length, scale);
		final var result = super.matchDbType(null, base, length, scale);
		final String nativeName = name;
		result.ifPresent(info -> {
			if (!nativeName.equals(base))
				info.setDataTypeName(nativeName);
			if (isArray) {
				if (info.getDataType().orElse(DataType.OTHER) == DataType.OTHER || base.equals("VARBINARY"))
					throw new IllegalArgumentException("Unsupported Phoenix ARRAY element type: " + nativeName);
				info.setArrayDimension(1);
			}
		});
		return result;
	}

	private static java.util.regex.Matcher arrayType(String name) {
		return java.util.regex.Pattern
				.compile("^(.+?)\\s*(?:\\s+ARRAY(?:\\s*\\[(?<capacity>[^\\[\\]]*)\\])?|\\[(?<size>[^\\[\\]]*)\\])$")
				.matcher(name);
	}

	@Override
	public boolean setDbType(final DataType dataType, final String productDataType, final Long length,
			final Integer scale, final com.sqlapp.data.schemas.properties.DataTypeLengthProperties<?> column) {
		Integer capacity = null;
		if (productDataType != null) {
			final var array = arrayType(productDataType.strip().toUpperCase(java.util.Locale.ROOT));
			if (array.matches()) {
				final String text = array.group("capacity") != null ? array.group("capacity") : array.group("size");
				if (text != null && !text.isBlank()) {
					try {
						capacity = Integer.valueOf(text.strip());
					} catch (NumberFormatException e) {
						throw new IllegalArgumentException("Phoenix ARRAY capacity exceeds the Schema integer range",
								e);
					}
					if (capacity <= 0)
						throw new IllegalArgumentException("Phoenix ARRAY capacity must be positive");
				}
			}
		}
		final boolean matched = super.setDbType(dataType, productDataType, length, scale, column);
		if (matched && capacity != null) {
			if (!(column instanceof com.sqlapp.data.schemas.properties.ArrayDimensionUpperBoundProperty<?> bound))
				throw new IllegalArgumentException("Target Schema object cannot retain Phoenix ARRAY capacity");
			bound.setArrayDimensionUpperBound(capacity);
		}
		return matched;
	}

	/**
	 * DB製品名(シンプル名)
	 */
	@Override
	public String getSimpleName() {
		return "Phoenix";
	}

	@Override
	public boolean supportsWith() {
		return true;
	}

	@Override
	public String getSelectDummyTableName() {
		return null;
	}

	@Override
	public String getCurrentDateFunction() {
		return "CURRENT_DATE()";
	}

	@Override
	public String getCurrentDateTimeFunction() {
		// Phoenix DATE includes time to millisecond precision.
		return getCurrentDateFunction();
	}

	@Override
	public String getCurrentTimeFunction() {
		return "CURRENT_TIME()";
	}

	@Override
	public String getCurrentTimestampFunction() {
		return "CAST(CURRENT_DATE() AS TIMESTAMP)";
	}

	@Override
	public boolean supportsSequencePreallocation() {
		return true;
	}

	@Override
	public List<Object> expandSequenceValues(final Sequence sequence, final List<Object> values, final int count) {
		if (values.size() != 1 || count <= 1) {
			return values;
		}
		final BigInteger first = new BigInteger(values.get(0).toString());
		final BigInteger increment = sequence.getIncrementBy() != null ? sequence.getIncrementBy() : BigInteger.ONE;
		final List<Object> result = CommonUtils.list(count);
		for (int i = 0; i < count; i++) {
			result.add(first.add(increment.multiply(BigInteger.valueOf(i))).longValueExact());
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new PhoenixCatalogReader(this);
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
		return new PhoenixSqlFactoryRegistry(this);
	}

	@Override
	public PhoenixSqlBuilder createSqlBuilder() {
		return new PhoenixSqlBuilder(this);
	}
}
