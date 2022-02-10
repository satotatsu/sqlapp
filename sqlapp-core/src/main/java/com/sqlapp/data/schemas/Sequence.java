/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.schemas;

import java.math.BigInteger;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.properties.CacheProperty;
import com.sqlapp.data.schemas.properties.CacheSizeProperty;
import com.sqlapp.data.schemas.properties.CycleProperty;
import com.sqlapp.data.schemas.properties.DataTypeProperties;
import com.sqlapp.data.schemas.properties.IncrementByProperty;
import com.sqlapp.data.schemas.properties.LastValueProperty;
import com.sqlapp.data.schemas.properties.MaxValueProperty;
import com.sqlapp.data.schemas.properties.MinValueProperty;
import com.sqlapp.data.schemas.properties.PrecisionProperty;
import com.sqlapp.data.schemas.properties.ScaleProperty;
import com.sqlapp.data.schemas.properties.SequenceOrderProperty;
import com.sqlapp.data.schemas.properties.StartValueProperty;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * シーケンス
 * 
 * @author satoh
 * 
 */
public final class Sequence extends AbstractSchemaObject<Sequence> implements
		HasParent<SequenceCollection>, DataTypeProperties<Sequence>,PrecisionProperty<Sequence> 
		,ScaleProperty<Sequence>
	,MinValueProperty<Sequence>
	,MaxValueProperty<Sequence>
	,IncrementByProperty<Sequence>
	,CacheSizeProperty<Sequence>
	,StartValueProperty<Sequence>
	,LastValueProperty<Sequence>
	,CycleProperty<Sequence>
	,CacheProperty<Sequence>
	,SequenceOrderProperty<Sequence>
{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2636864307288766192L;
	/**
	 * java.sql.Types(VARCHAR,CHAR…)
	 */
	private DataType dataType = null;
	/**
	 * DB固有の型
	 */
	private String dataTypeName = null;
	/** 最小値 */
	private BigInteger minValue = null;
	/** 最大値 */
	private BigInteger maxValue = null;
	/** ステップ */
	private BigInteger incrementBy = null;
	/** キャッシュサイズ */
	private Integer cacheSize = null;
	/** 開始番号 */
	private BigInteger startValue = null;
	/** 最終採番済み番号 */
	private BigInteger lastValue = null;
	/**
	 * コンバータ
	 */
	private static final Converter<BigInteger> converter = Converters
			.getDefault().getConverter(BigInteger.class);
	/** サイクル */
	private boolean cycle = (Boolean)SchemaProperties.CYCLE.getDefaultValue();
	/** キャッシュ */
	private boolean cache = (Boolean)SchemaProperties.CACHE.getDefaultValue();
	/** 順番 */
	private boolean order = (Boolean)SchemaProperties.SEQUENCE_ORDER.getDefaultValue();
	/** DECIMAL指定時の桁 */
	private Integer precision = null;
	/** DECIMAL指定時の精度 */
	private Integer scale = null;

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.PrecisionProperty#getPrecision()
	 */
	@Override
	public Integer getPrecision() {
		return precision;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.PrecisionProperty#setPrecision(java.lang.Number)
	 */
	@Override
	public Sequence setPrecision(Number precision) {
		this.precision = Converters.getDefault().convertObject(precision, Integer.class);
		return this;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.PrecisionProperty#setPrecision(int)
	 */
	@Override
	public Sequence setPrecision(int precision) {
		this.precision = precision;
		return this;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.ScaleProperty#getScale()
	 */
	@Override
	public Integer getScale() {
		return scale;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.ScaleProperty#setScale(int)
	 */
	@Override
	public Sequence setScale(int scale) {
		this.scale = scale;
		return instance();
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.ScaleProperty#setScale(java.lang.Number)
	 */
	@Override
	public Sequence setScale(Number scale) {
		this.scale = Converters.getDefault().convertObject(scale, Integer.class);
		return this;
	}

	/**
	 * コンストラクタ
	 */
	public Sequence() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public Sequence(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<Sequence> newInstance(){
		return ()->new Sequence();
	}

	/**
	 * シーケンスの次の番号を取得します
	 * 
	 */
	public BigInteger nextValueFor() {
		if (this.lastValue == null) {
			this.lastValue = this.startValue;
		} else {
			this.lastValue = this.startValue.add(this.incrementBy);
		}
		return this.lastValue;
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.DATA_TYPE, this.dataType);
		builder.add(SchemaProperties.DATA_TYPE_NAME, this.dataTypeName);
		builder.add(SchemaProperties.PRECISION, this.precision);
		builder.add(SchemaProperties.MIN_VALUE, this.getMinValue());
		builder.add(SchemaProperties.MAX_VALUE, this.getMaxValue());
		builder.add(SchemaProperties.INCREMENT_BY, this.getIncrementBy());
		builder.add(SchemaProperties.CACHE_SIZE, this.getCacheSize());
		builder.add(SchemaProperties.START_VALUE, this.getStartValue());
		builder.add(SchemaProperties.LAST_VALUE, this.getLastValue());
		if (this.cycle) {
			builder.add(SchemaProperties.CYCLE, this.isCycle());
		}
		builder.add(SchemaProperties.CACHE, this.isCache());
		if (this.order) {
			builder.add(SchemaProperties.SEQUENCE_ORDER, this.isOrder());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Sequence)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		Sequence val = (Sequence) obj;
		if (!equals(SchemaProperties.DATA_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DATA_TYPE_NAME
				, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getDataTypeName(), val.getDataTypeName()))) {
			return false;
		}
		if (!equals(SchemaProperties.PRECISION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.INCREMENT_BY, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.MAX_VALUE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.MIN_VALUE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.CACHE_SIZE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.START_VALUE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.LAST_VALUE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.CACHE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.CYCLE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SEQUENCE_ORDER, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.DATA_TYPE.getLabel(), this.getDataType());
		stax.writeAttribute(SchemaProperties.DATA_TYPE_NAME.getLabel(), this.getDataTypeName());
		stax.writeAttribute(SchemaProperties.PRECISION.getLabel(), this.getPrecision());
		stax.writeAttribute(SchemaProperties.MIN_VALUE.getLabel(), getMinValue());
		stax.writeAttribute(SchemaProperties.MAX_VALUE.getLabel(), getMaxValue());
		stax.writeAttribute(SchemaProperties.INCREMENT_BY.getLabel(), getIncrementBy());
		stax.writeAttribute(SchemaProperties.CACHE_SIZE.getLabel(), getCacheSize());
		stax.writeAttribute(SchemaProperties.START_VALUE.getLabel(), getStartValue());
		stax.writeAttribute(SchemaProperties.LAST_VALUE.getLabel(), getLastValue());
		if (this.cycle) {
			stax.writeAttribute(SchemaProperties.CYCLE.getLabel(), isCycle());
		}
		stax.writeAttribute(SchemaProperties.CACHE.getLabel(), isCache());
		stax.writeAttribute(SchemaProperties.SEQUENCE_ORDER.getLabel(), isOrder());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbTypeProperties#getDbType()
	 */
	@Override
	public DataType getDataType() {
		return dataType;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbTypeProperties#setDbType(com.sqlapp.data.db
	 * .datatype.Types)
	 */
	@Override
	public Sequence setDataType(DataType dbType) {
		this.dataType = dbType;
		this.setMinValue(this.getMinValue());
		this.setMaxValue(this.getMaxValue());
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbTypeProperties#getDbTypeName()
	 */
	@Override
	public String getDataTypeName() {
		return dataTypeName;
	}

	@Override
	public Integer getCacheSize() {
		return cacheSize;
	}

	@Override
	public BigInteger getMinValue() {
		return minValue;
	}

	@Override
	public Sequence setMinValue(BigInteger minValue) {
		this.minValue = getLimitValue(minValue);
		return instance();
	}

	@Override
	public BigInteger getMaxValue() {
		return maxValue;
	}

	@Override
	public Sequence setMaxValue(BigInteger maxValue) {
		this.maxValue = getLimitValue(maxValue);
		return instance();
	}

	@Override
	public BigInteger getIncrementBy() {
		return incrementBy;
	}

	@Override
	public Sequence setIncrementBy(BigInteger incrementBy) {
		this.incrementBy = incrementBy;
		return instance();
	}

	@Override
	public boolean isCycle() {
		return cycle;
	}

	@Override
	public Sequence setCycle(boolean cycle) {
		this.cycle = cycle;
		return instance();
	}

	@Override
	public boolean isCache() {
		return cache;
	}

	@Override
	public Sequence setCache(boolean cache) {
		this.cache = cache;
		return instance();
	}

	@Override
	public boolean isOrder() {
		return order;
	}

	@Override
	public Sequence setOrder(boolean order) {
		this.order = order;
		return this;
	}

	@Override
	public Sequence setCacheSize(Integer cacheSize) {
		this.cacheSize = cacheSize;
		return instance();
	}

	@Override
	public BigInteger getLastValue() {
		return lastValue;
	}

	@Override
	public Sequence setLastValue(BigInteger lastValue) {
		this.lastValue = lastValue;
		return instance();
	}

	/**
	 * @return the startValue
	 */
	@Override
	public BigInteger getStartValue() {
		return startValue;
	}

	/**
	 * @param startValue
	 *            the startValue to set
	 */
	@Override
	public Sequence setStartValue(BigInteger startValue) {
		this.startValue = startValue;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public SequenceCollection getParent() {
		return (SequenceCollection) super.getParent();
	}

	private BigInteger getLimitValue(BigInteger value) {
		if (value == null) {
			return null;
		}
		BigInteger min = getDbTypeMinValue(this.getDataType(), value);
		BigInteger max = getDbTypeMaxValue(this.getDataType(), value);
		if (min != null && max != null) {
			if (min.compareTo(value) > 0) {
				return min;
			} else {
				if (max.compareTo(value) < 0) {
					return max;
				}
			}
		}
		return value;
	}

	private static BigInteger getDbTypeMinValue(DataType type, BigInteger value) {
		if (value == null) {
			return null;
		}
		if (type == null) {
			return value;
		}
		BigInteger convValue = null;
		switch (type) {
		case TINYINT:
			convValue = TINYINT_MIN;
			break;
		case SMALLINT:
			convValue = SMALLINT_MIN;
			break;
		case INT:
			convValue = INT_MIN;
			break;
		case BIGINT:
			convValue = LONG_MIN;
			break;
		case UTINYINT:
			convValue = BigInteger.ZERO;
			break;
		case USMALLINT:
			convValue = BigInteger.ZERO;
			break;
		case UINT:
			convValue = BigInteger.ZERO;
			break;
		case UBIGINT:
			convValue = BigInteger.ZERO;
			break;
		default:
		}
		if (convValue == null) {
			return value;
		}
		return convValue;
	}

	/**
	 * TINYINTのBigIntegerの最小値
	 */
	private static final BigInteger TINYINT_MIN = converter
			.convertObject(Byte.MIN_VALUE);
	/**
	 * SMALLINTのBigIntegerの最小値
	 */
	private static final BigInteger SMALLINT_MIN = converter
			.convertObject(Short.MIN_VALUE);
	/**
	 * INTのBigIntegerの最小値
	 */
	private static final BigInteger INT_MIN = converter
			.convertObject(Integer.MIN_VALUE);
	/**
	 * BIGINTのBigIntegerの最小値
	 */
	private static final BigInteger LONG_MIN = converter
			.convertObject(Long.MIN_VALUE);

	private static BigInteger getDbTypeMaxValue(DataType type, BigInteger value) {
		if (value == null) {
			return null;
		}
		if (type == null) {
			return value;
		}
		BigInteger convValue = null;
		switch (type) {
		case TINYINT:
			convValue = TINYINT_MAX;
			break;
		case SMALLINT:
			convValue = SMALLINT_MAX;
			break;
		case INT:
			convValue = INT_MAX;
			break;
		case BIGINT:
			convValue = LONG_MAX;
			break;
		case UTINYINT:
			convValue = UTINYINT_MAX;
			break;
		case USMALLINT:
			convValue = USMALLINT_MAX;
			break;
		case UINT:
			convValue = UINT_MAX;
			break;
		case UBIGINT:
			convValue = ULONG_MAX;
			break;
		default:
		}
		if (convValue == null) {
			return value;
		}
		return convValue;
	}

	/**
	 * TINYINTのBigIntegerの最大値
	 */
	private static final BigInteger TINYINT_MAX = converter
			.convertObject(Byte.MAX_VALUE);
	/**
	 * SMALLINTのBigIntegerの最大値
	 */
	private static final BigInteger SMALLINT_MAX = converter
			.convertObject(Short.MAX_VALUE);
	/**
	 * INTのBigInteger最大値
	 */
	private static final BigInteger INT_MAX = converter
			.convertObject(Integer.MAX_VALUE);
	/**
	 * BIGINTのBigIntegerの最大値
	 */
	private static final BigInteger LONG_MAX = converter
			.convertObject(Long.MAX_VALUE);
	/**
	 * UTINYINTのBigIntegerの最大値
	 */
	private static final BigInteger UTINYINT_MAX = converter
			.convertObject((1L + Byte.MAX_VALUE) * 2L - 1L);
	/**
	 * USMALLINTのBigIntegerの最大値
	 */
	private static final BigInteger USMALLINT_MAX = converter
			.convertObject((1L + Short.MAX_VALUE) * 2L - 1L);
	/**
	 * UINTのBigInteger最大値
	 */
	private static final BigInteger UINT_MAX = converter
			.convertObject((1L + Integer.MAX_VALUE) * 2L - 1L);
	/**
	 * UBIGINTのBigIntegerの最大値
	 */
	private static final BigInteger ULONG_MAX = UINT_MAX.add(BigInteger.ONE)
			.multiply(BigInteger.valueOf(2)).subtract(BigInteger.ONE);

}
