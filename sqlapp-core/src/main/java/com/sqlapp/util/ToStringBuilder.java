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
package com.sqlapp.util;

import static com.sqlapp.util.BinaryUtils.toHexString;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Time;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbInfo;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.properties.ISchemaProperty;

/**
 * toStringを出力するするためのビルダー
 * 
 * @author satoh
 * 
 */
public final class ToStringBuilder implements Serializable {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5691665855745766445L;
	/**
	 * クラス名
	 */
	private String className = null;
	private SeparatedStringBuilder builder = new SeparatedStringBuilder(", ");

	private String openQuate = "[";
	private String closeQuate = "]";

	private boolean exceptEmpty = true;

	/**
	 * コンストラクタ
	 */
	public ToStringBuilder() {
		this.className = "";
	}

	/**
	 * コンストラクタ
	 * 
	 * @param className
	 */
	public ToStringBuilder(final String className) {
		this.className = className;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param clazz
	 *            クラス
	 */
	public ToStringBuilder(final Class<?> clazz) {
		this.className = clazz.getSimpleName();
	}

	/**
	 * コンストラクタ
	 * 
	 * @param obj
	 */
	public ToStringBuilder(final Object obj) {
		this.className = obj.getClass().getSimpleName();
	}

	/**
	 * プロパティの追加
	 * 
	 * @param propertyName
	 * @param text
	 */
	public ToStringBuilder add(final String propertyName, final List<?> text) {
		if (exceptEmpty) {
			if (isEmpty(text)) {
				return this;
			}
		}
		SeparatedStringBuilder sep = new SeparatedStringBuilder("\n");
		sep.setStart("(").setEnd(")");
		sep.add(text);
		addElement("\n" + propertyName, sep.toString());
		return this;
	}

	/**
	 * 値の追加
	 * 
	 * @param value
	 */
	public ToStringBuilder add(final Object value) {
		if (exceptEmpty) {
			if (isEmpty(value)) {
				return this;
			}
		}
		builder.add(value);
		return this;
	}

	/**
	 * カラム名の追加
	 * 
	 */
	public ToStringBuilder addColumnNames(final Column... columns) {
		if (exceptEmpty) {
			if (isEmpty(columns)) {
				return this;
			}
		}
		SeparatedStringBuilder cols = new SeparatedStringBuilder(", ");
		cols.setStart("(").setEnd(")");
		cols.addNames(columns);
		addElement(SchemaProperties.COLUMN_NAME.getLabel(), cols.toString());
		return this;
	}

	/**
	 * カラム名の追加
	 * 
	 */
	public ToStringBuilder addColumnNames(final Collection<Column> columns) {
		return addColumnNames(SchemaProperties.COLUMN_NAME.getLabel(), columns);
	}

	/**
	 * カラム名を追加します
	 * 
	 * @param propertyName
	 * @param columns
	 */
	public ToStringBuilder addColumnNames(final String propertyName,
			final Collection<Column> columns) {
		if (exceptEmpty) {
			if (isEmpty(columns)) {
				return this;
			}
		}
		String val = getColumnNames(propertyName, columns);
		add(val);
		return this;
	}

	/**
	 * カラム名の取得
	 * 
	 * @param propertyName
	 * @param columns
	 */
	public static String getColumnNames(final String propertyName,
			final Collection<Column> columns) {
		if (isEmpty(columns)) {
			return null;
		}
		SeparatedStringBuilder cols = new SeparatedStringBuilder(", ");
		cols.setStart(propertyName + "(").setEnd(")");
		cols.addNames(columns);
		return cols.toString();
	}

	/**
	 * カラム名の追加
	 * 
	 * @param propertyName
	 * @param columns
	 */
	public ToStringBuilder addColumnNames(final String propertyName,
			final Column... columns) {
		if (exceptEmpty) {
			if (isEmpty(columns)) {
				return this;
			}
		}
		String val = getColumnNames(propertyName, columns);
		add(val);
		return this;
	}

	/**
	 * カラム名の取得
	 * 
	 * @param propertyName
	 * @param columns
	 */
	public static String getColumnNames(final String propertyName,
			final Column... columns) {
		if (isEmpty(columns)) {
			return null;
		}
		SeparatedStringBuilder cols = new SeparatedStringBuilder(", ");
		cols.setStart(propertyName + "=(").setEnd(")");
		cols.addNames(columns);
		return cols.toString();
	}

	/**
	 * プロパティの追加
	 * 
	 * @param props
	 * @param value
	 */
	public ToStringBuilder add(final ISchemaProperty props, final Object value) {
		Object obj=props.getValue(value);
		return add(props.getLabel(), obj!=null?obj:value);
	}
	
	/**
	 * プロパティの追加
	 * 
	 * @param propertyName
	 * @param value
	 */
	public ToStringBuilder add(final String propertyName, final Object value) {
		if (exceptEmpty) {
			if (isEmpty(value)) {
				return this;
			}
		}
		if (value.getClass().isArray()) {
			if (value instanceof byte[]) {
				return add(propertyName, (byte[]) value);
			}
			String text = Arrays.toString((Object[]) value);
			addElement(propertyName, text);
		} else {
			addElement(propertyName, value);
		}
		return this;
	}

	/**
	 * プロパティの追加
	 * 
	 * @param propertyName
	 * @param value
	 */
	public ToStringBuilder add(final String propertyName, final byte[] value) {
		if (exceptEmpty) {
			if (isEmpty(value)) {
				return this;
			}
		}
		addElement(propertyName, toHexString(value));
		return this;
	}

	/**
	 * プロパティの追加
	 * 
	 * @param propertyName
	 * @param value
	 */
	public ToStringBuilder add(final String propertyName, final DbInfo value) {
		if (exceptEmpty) {
			if (isEmpty(value)) {
				return this;
			}
		}
		addElement(propertyName, value);
		return this;
	}

	/**
	 * プロパティの追加
	 * 
	 * @param propertyName
	 * @param value
	 */
	public ToStringBuilder add(final String propertyName, final long value) {
		if (exceptEmpty) {
			if (value == 0) {
				return this;
			}
		}
		addElement(propertyName, value);
		return this;
	}

	/**
	 * プロパティの追加
	 * 
	 * @param propertyName
	 * @param value
	 */
	public ToStringBuilder add(final String propertyName, final BigDecimal value) {
		if (exceptEmpty) {
			if (value == null || BigDecimal.ZERO.compareTo(value) == 0) {
				return this;
			}
		}
		addElement(propertyName, value);
		return this;
	}

	/**
	 * プロパティの追加
	 * 
	 * @param propertyName
	 * @param value
	 */
	public ToStringBuilder add(final String propertyName, final String value) {
		if (exceptEmpty) {
			if (isEmpty(value)) {
				return this;
			}
		}
		addElement(propertyName, value);
		return this;
	}

	/**
	 * プロパティの追加
	 * 
	 * @param props
	 * @param value
	 */
	public ToStringBuilder add(final ISchemaProperty props, final String value) {
		add(props.getLabel(), value);
		return this;
	}

	/**
	 * プロパティの追加
	 * 
	 * @param propertyName
	 * @param values
	 */
	public ToStringBuilder add(final String propertyName,
			final Collection<?> values) {
		if (exceptEmpty) {
			if (isEmpty(values)) {
				return this;
			}
		}
		SeparatedStringBuilder builder = new SeparatedStringBuilder(", ");
		builder.setStart(propertyName + "=(").setEnd(")");
		builder.add(values);
		addElement(propertyName, builder.toString());
		return this;
	}

	/**
	 * プロパティの追加
	 * 
	 * @param propertyName
	 * @param value
	 */
	public ToStringBuilder add(String propertyName, Date value) {
		if (exceptEmpty) {
			if (isEmpty(value)) {
				return this;
			}
		}
		addElement(propertyName, DateUtils.format(value));
		return this;
	}

	/**
	 * プロパティの追加
	 * 
	 * @param propertyName
	 * @param value
	 */
	public ToStringBuilder add(String propertyName, Time value) {
		if (exceptEmpty) {
			if (isEmpty(value)) {
				return this;
			}
		}
		addElement(propertyName, DateUtils.format(value));
		return this;
	}

	/**
	 * プロパティの追加
	 * 
	 * @param propertyName
	 * @param value
	 */
	public ToStringBuilder add(String propertyName, Calendar value) {
		if (exceptEmpty) {
			if (isEmpty(value)) {
				return this;
			}
		}
		addElement(propertyName, DateUtils.format(value));
		return this;
	}

	private void addElement(String propertyName, Object value) {
		builder.add(propertyName + "=" + value);
	}

	/**
	 * プロパティの追加
	 * 
	 * @param propertyName
	 * @param value
	 */
	public ToStringBuilder add(String propertyName, Boolean value) {
		if (exceptEmpty && isEmpty(value)) {
			return this;
		}
		if (value.booleanValue()) {
			addElement(propertyName, value);
		}
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		String val = builder.toString();
		StringBuilder result = new StringBuilder(this.className.length()
				+ val.length() + 2);
		result.append(this.className).append(openQuate).append(val)
				.append(closeQuate);
		return result.toString();
	}

	public String toStringSimple() {
		String val = builder.toString();
		StringBuilder result = new StringBuilder();
		result.append(openQuate).append(val).append(closeQuate);
		return result.toString();
	}

	/**
	 * @param openQuate
	 *            the openQuate to set
	 */
	public ToStringBuilder setOpenQuate(String openQuate) {
		this.openQuate = openQuate;
		return this;
	}

	/**
	 * @param closeQuate
	 *            the closeQuate to set
	 */
	public ToStringBuilder setCloseQuate(String closeQuate) {
		this.closeQuate = closeQuate;
		return this;
	}

	/**
	 * @param separator
	 *            the separator to set
	 */
	public ToStringBuilder setSeparator(String separator) {
		builder.setSeparator(separator);
		return this;
	}
}
