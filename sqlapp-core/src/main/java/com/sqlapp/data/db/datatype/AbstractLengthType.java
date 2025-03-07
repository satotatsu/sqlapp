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

import static com.sqlapp.util.CommonUtils.LEN_1GB;
import static com.sqlapp.util.CommonUtils.LEN_1KB;
import static com.sqlapp.util.CommonUtils.LEN_1MB;
import static com.sqlapp.util.CommonUtils.eq;

import java.util.regex.Matcher;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;
import com.sqlapp.data.schemas.properties.DataTypeSetProperties;
import com.sqlapp.util.ToStringBuilder;

public abstract class AbstractLengthType<T extends DbDataType<T>> extends
		DbDataType<T> implements LengthProperties<T> {

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
		this.setFixedLength(true);
		this.setTypeName(typeName);
		this.setCreateFormat(typeName + "(", ")");
		this.addFormats(typeName);
		addSizeFormat(typeName);
		for (String alias : this.getDataType().getAliasNames()) {
			this.addSizeFormat(alias);
		}
	}

	/**
	 * サイズを含む型の作成フォーマット
	 * 
	 * @param start
	 * @param end
	 */
	public T setCreateFormat(final String start, final String end) {
		this.setCreateFormat(start + LENGTH_REPLACE + end);
		return instance();
	}

	@Override
	public boolean matchLength(DataTypeLengthProperties<?> column) {
		if (column.getLength()!=null&&this.getMaxLength()!=null) {
			if (this.getMaxLength().compareTo(column.getLength())>=0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * デフォルトのサイズ
	 */
	private Long defaultLength = null;
	/**
	 * 最大のサイズ
	 */
	private Long maxLength = null;

	@Override
	protected void buildToString(ToStringBuilder builder){
		builder.add("defaultLength", defaultLength);
		builder.add("maxLength", maxLength);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.datatype.LengthProperties#getDefaultLength()
	 */
	@Override
	public Long getDefaultLength() {
		return defaultLength;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.datatype.LengthProperties#setDefaultLength(java.lang
	 * .Long)
	 */
	@Override
	public T setDefaultLength(Long defaultLength) {
		this.defaultLength = defaultLength;
		return this.instance();
	}

	public T setDefaultLength(Number defaultLength) {
		this.defaultLength = Converters.getDefault().convertObject(
				defaultLength, Long.class);
		return this.instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.datatype.LengthProperties#getMaxLength()
	 */
	@Override
	public Long getMaxLength() {
		return maxLength;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.datatype.LengthProperties#setMaxLength(java.lang.Long)
	 */
	@Override
	public T setMaxLength(Long maxLength) {
		this.maxLength = maxLength;
		return this.instance();
	}

	public T setMaxLength(long maxLength) {
		this.maxLength = maxLength;
		return this.instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.datatype.LengthProperties#getLength(java.lang.Long)
	 */
	@Override
	public long getLength(final Long length) {
		long ret = this.getDefaultLength() != null ? this.getDefaultLength()
				.longValue() : 0;
		if (length != null) {
			if (this.getMaxLength() != null) {
				ret = this.getMaxLength().longValue() > length.longValue() ? length
						.longValue() : this.getMaxLength();
			}
		}
		return ret;
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
			if (this.getDefaultLength() != null) {
				column.setLength(this.getDefaultLength());
			}
		}
		SchemaUtils.setDataTypeNameInternal(this.getTypeName(), column);
		if (matcher.groupCount() > 0) {
			Long size = getLong(matcher, 1);
			if (size != null) {
				if (matcher.groupCount() > 1) {
					String unit = matcher.group(2);
					if ("K".equalsIgnoreCase(unit)) {
						size = size * LEN_1KB;
					} else if ("M".equalsIgnoreCase(unit)) {
						size = size * LEN_1MB;
					} else if ("G".equalsIgnoreCase(unit)) {
						size = size * LEN_1GB;
					}
				}
				column.setLength(size);
			} else {
				if (getDefaultLength() != null) {
					column.setLength(this.getDefaultLength());
				}
			}
			if (matcher.groupCount() > 2) {
				String c = matcher.group(3);
				if (column instanceof DataTypeSetProperties) {
					((DataTypeSetProperties<?>) column)
							.setCharacterSemantics(CharacterSemantics.parse(c));
				}
			}
		}
	}

	/**
	 * サイズフォーマットを追加します
	 * 
	 * @param dataTypeName
	 */
	@Override
	public T addSizeFormat(String dataTypeName) {
		this.addFormats(
				dataTypeName
						+ "\\s*\\(\\s*([0-9]+)\\s*(K|M|G){0,1}\\s*(CHAR|BYTE|C|B){0,1}\\s*\\)\\s*",
						dataTypeName + "\\s*");
		return instance();
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
		if (!(obj instanceof AbstractLengthType)) {
			return false;
		}
		AbstractLengthType<?> objValue = (AbstractLengthType<?>) obj;
		if (!eq(this.getMaxLength(), objValue.getMaxLength())) {
			return false;
		}
		if (!eq(this.getDefaultLength(), objValue.getDefaultLength())) {
			return false;
		}

		return true;
	}
}
