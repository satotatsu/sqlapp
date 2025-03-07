/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.datatype;

import static com.sqlapp.util.CommonUtils.min;
import static com.sqlapp.util.CommonUtils.toInteger;
import static com.sqlapp.util.StringUtils.getGroupString;

import java.util.regex.Matcher;

import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;
import com.sqlapp.util.CommonUtils;

public abstract class AbstractScaleType<T extends DbDataType<T>> extends
		DbDataType<T> implements ScaleProperties<T> {

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
		this.setCreateFormat(getCreateFormat(typeName + "(", ")"));
		this.setFixedScale(true);
		this.addFormats(typeName);
		addScaleFormat(typeName);
	}

	/**
	 * 精度を含む型の作成フォーマット
	 * 
	 * @param start
	 */
	public String getCreateFormat(String start) {
		return start + SCALE_REPLACE;
	}

	/**
	 * 精度を含む型の作成フォーマット
	 * 
	 * @param start
	 * @param end
	 */
	public String getCreateFormat(String start, String end) {
		return start + SCALE_REPLACE + end;
	}

	/**
	 * 精度
	 */
	private int scale = 0;
	/**
	 * デフォルトの精度
	 */
	private Integer defaultScale = null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.datatype.ScaleProperties#getScale()
	 */
	@Override
	public int getScale() {
		return scale;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.datatype.ScaleProperties#setScale(int)
	 */
	@Override
	public T setScale(final int scale) {
		this.scale = scale;
		return this.instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.datatype.ScaleProperties#getDefaultScale()
	 */
	@Override
	public Integer getDefaultScale() {
		return defaultScale;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.datatype.ScaleProperties#setDefaultScale(java.lang
	 * .Integer)
	 */
	@Override
	public T setDefaultScale(Integer defaultScale) {
		this.defaultScale = defaultScale;
		return this.instance();
	}

	/**
	 * 小数点の右に使用できる最大桁数
	 */
	private Integer maxScale = null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.datatype.ScaleProperties#getMaxScale()
	 */
	@Override
	public Integer getMaxScale() {
		return maxScale;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.datatype.ScaleProperties#setMaxScale(java.lang.Integer
	 * )
	 */
	@Override
	public T setMaxScale(Integer maxScale) {
		this.maxScale = maxScale;
		return this.instance();
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
			if (this.getDefaultScale() != null) {
				column.setScale(this.getDefaultScale());
			}
		}
		if (matcher.groupCount() > 0) {
			String val = getGroupString(matcher, 1);
			Integer size = toInteger(val);
			if (size != null) {
				column.setScale(min(size, this.getMaxScale()));
			} else {
				if (this.getDefaultScale() != null) {
					column.setScale(getDefaultScale());
				}
			}
		} else{
			if (!CommonUtils.eqIgnoreCase(column.getDataTypeName(), this.getTypeName())){
				SchemaUtils.setDataTypeNameInternal(this.getTypeName(), column);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.datatype.LengthProperties#getLength(java.lang.Long)
	 */
	@Override
	public Integer getScale(Integer scale) {
		return getProperNumber(this.getMaxScale(), this.getDefaultScale(),
				scale);
	}

	/**
	 * 桁のフォーマットの追加
	 * 
	 * @param dataTypeName
	 */
	@SuppressWarnings("unchecked")
	public T addScaleFormat(String dataTypeName) {
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
		if (!(obj instanceof AbstractScaleType)) {
			return false;
		}
		AbstractScaleType<?> objValue = (AbstractScaleType<?>) obj;
		if (!CommonUtils.eq(this.getScale(), objValue.getScale())) {
			return false;
		}
		if (!CommonUtils.eq(this.getDefaultScale(), objValue.getDefaultScale())) {
			return false;
		}
		if (!CommonUtils.eq(this.getMaxScale(), objValue.getMaxScale())) {
			return false;
		}
		return true;
	}
}
