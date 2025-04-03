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

import static com.sqlapp.util.CommonUtils.eq;

import com.sqlapp.data.db.datatype.util.PrecisionScaleColumnTypeMatcher;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;
import com.sqlapp.util.CommonUtils;

public abstract class AbstractPrecisionScaleType<T extends DbDataType<T>> extends DbDataType<T>
		implements PrecisionProperties<T>, ScaleProperties<T> {

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
		this.setFixedScale(true);
		this.setCreateFormat(typeName + "(", ",", ")");
		this.addColumnTypeMatcher(typeName);
	}

	/**
	 * カラムの一致判定を追加します
	 * 
	 * @param カラムの一致判定一覧
	 * @return this
	 */
	public T addColumnTypeMatcher(String typeName) {
		this.addColumnTypeMatcher(new PrecisionScaleColumnTypeMatcher(typeName));
		return instance();
	}

	/**
	 * カラムの一致判定を設定します
	 * 
	 * @param prefix prefix
	 * @param middle middle
	 * @param suffix suffix
	 * @return this
	 */
	public T setColumnTypeMatcher(String prefix, final String middle, String suffix) {
		this.setColumnTypeMatcher(new PrecisionScaleColumnTypeMatcher(prefix, middle, suffix));
		return instance();
	}

	/**
	 * カラムの一致判定を追加します
	 * 
	 * @param start  start
	 * @param middle middle
	 * @param end    end
	 * @return this
	 */
	public T addColumnTypeMatcher(String prefix, final String middle, String suffix) {
		this.addColumnTypeMatcher(new PrecisionScaleColumnTypeMatcher(prefix, middle, suffix));
		return instance();
	}

	/**
	 * 桁、精度を含む型の作成フォーマット
	 * 
	 * @param start  start
	 * @param middle middle
	 * @param end    end
	 * @return this
	 */
	public T setCreateFormat(final String start, final String middle, final String end) {
		this.setCreateFormat(start + PRECISION_REPLACE + middle + SCALE_REPLACE + end);
		return instance();
	}

	@Override
	public boolean matchLength(DataTypeLengthProperties<?> column) {
		if (column.getLength() != null && this.getMaxPrecision() != null) {
			if (this.getMaxPrecision().longValue() - column.getLength().longValue() >= 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * デフォルトの桁
	 */
	private Integer defaultPrecision = null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.datatype.PrecisionProperties#getDefaultPrecision()
	 */
	@Override
	public Integer getDefaultPrecision() {
		return defaultPrecision;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.datatype.PrecisionProperties#setDefaultPrecision(java
	 * .lang.Integer)
	 */
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
	 * @see com.sqlapp.data.db.datatype.PrecisionProperties#setMaxPrecision(java.
	 * lang.Integer)
	 */
	@Override
	public T setMaxPrecision(Integer maxPrecision) {
		this.maxPrecision = maxPrecision;
		return this.instance();
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
	 * @see com.sqlapp.data.db.datatype.ScaleProperties#setDefaultScale(java.lang
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
	 * com.sqlapp.data.db.datatype.ScaleProperties#setMaxScale(java.lang.Integer )
	 */
	@Override
	public T setMaxScale(Integer maxScale) {
		this.maxScale = maxScale;
		return this.instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.datatype.PrecisionProperties#getPrecision(java.lang
	 * .Integer)
	 */
	@Override
	public Integer getPrecision(Number precision) {
		return getProperNumber(this.getMaxPrecision(), this.getDefaultPrecision(), precision);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.datatype.LengthProperties#getLength(java.lang.Long)
	 */
	@Override
	public Integer getScale(Integer scale) {
		return getProperNumber(this.getMaxScale(), this.getDefaultScale(), scale);
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
		if (!(obj instanceof AbstractPrecisionScaleType)) {
			return false;
		}
		AbstractPrecisionScaleType<?> objValue = (AbstractPrecisionScaleType<?>) obj;
		if (!eq(this.getDefaultPrecision(), objValue.getDefaultPrecision())) {
			return false;
		}
		if (!eq(this.getMaxPrecision(), objValue.getMaxPrecision())) {
			return false;
		}
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
