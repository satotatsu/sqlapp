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
package com.sqlapp.data.db.datatype;

import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.min;
import static com.sqlapp.util.CommonUtils.toInteger;
import static com.sqlapp.util.StringUtils.getGroupString;

import java.util.regex.Matcher;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;
import com.sqlapp.util.CommonUtils;

public abstract class AbstractPrecisionType<T extends DbDataType<T>> extends
		DbDataType<T> implements PrecisionProperties<T> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 8982873757752848020L;

	/**
	 * 初期化
	 * 
	 * @param typeName
	 */
	protected void initialize(String typeName) {
		this.setFixedPrecision(true);
		this.setTypeName(typeName);
		this.setCreateFormat(typeName + "(", ")");
		this.addFormats(typeName);
		addPrecisionFormat(typeName);
	}

	/**
	 * 桁を含む型の作成フォーマット
	 * 
	 * @param start
	 * @param end
	 */
	public T setCreateFormat(final String start, final String end) {
		this.setCreateFormat(start + PRECISION_REPLACE + end);
		return instance();
	}

	@Override
	public boolean matchLength(DataTypeLengthProperties<?> column) {
		if (column.getLength()!=null&&this.getMaxPrecision()!=null) {
			if (this.getMaxPrecision().longValue()-column.getLength().longValue()>=0) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * デフォルトの桁
	 */
	private Integer defaultPrecision = null;

	@Override
	public Integer getDefaultPrecision() {
		return defaultPrecision;
	}

	@Override
	public T setDefaultPrecision(Integer defaultPrecision) {
		this.defaultPrecision = defaultPrecision;
		return this.instance();
	}

	/**
	 * 最大の桁
	 */
	private Integer maxPrecision = null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.datatype.PrecisionProperties#getMaxPrecision()
	 */
	@Override
	public Integer getMaxPrecision() {
		return maxPrecision;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.datatype.PrecisionProperties#setMaxPrecision(java.
	 * lang.Integer)
	 */
	@Override
	public T setMaxPrecision(Integer maxPrecision) {
		this.maxPrecision = maxPrecision;
		this.getParent()
				.addDataLength(
						this,
						Converters.getDefault().convertObject(maxPrecision,
								Long.class));
		return this.instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.datatype.PrecisionProperties#getPrecision(java.lang
	 * .Integer)
	 */
	@Override
	public Integer getPrecision(Number precision) {
		return getProperNumber(this.getMaxPrecision(),
				this.getDefaultPrecision(), precision);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.datatype.AbstractDbDataType#parseAndSet(java.util.
	 * regex.Matcher, com.sqlapp.schemas.DataTypeSetProperties)
	 */
	@Override
	protected void parseAndSet(Matcher matcher,
			DataTypeLengthProperties<?> column) {
		if (matcher.groupCount() == 0) {
			column.setLength(this.getDefaultPrecision());
		}
		if (matcher.groupCount() > 0) {
			String val = getGroupString(matcher, 1);
			Integer size = toInteger(val);
			if (size != null) {
				column.setLength(min(size, this.getMaxPrecision()));
			} else {
				column.setLength(this.getDefaultPrecision());
			}
		} else{
			if (!CommonUtils.eqIgnoreCase(column.getDataTypeName(), this.getTypeName())){
				SchemaUtils.setDataTypeNameInternal(this.getTypeName(), column);
			}
		}
	}

	/**
	 * 桁のフォーマットの追加
	 * 
	 * @param dataTypeName
	 */
	@SuppressWarnings("unchecked")
	@Override
	public T addPrecisionFormat(String dataTypeName) {
		this.addFormats(dataTypeName + "\\s*\\(\\s*([0-9]+)\\s*\\)\\s*", dataTypeName
				+ "\\s*");
		return (T) (this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof AbstractPrecisionType)) {
			return false;
		}
		AbstractPrecisionType<?> objValue = (AbstractPrecisionType<?>) obj;
		if (!eq(this.getDefaultPrecision(), objValue.getDefaultPrecision())) {
			return false;
		}
		if (!eq(this.getMaxPrecision(), objValue.getMaxPrecision())) {
			return false;
		}
		return true;
	}
}
