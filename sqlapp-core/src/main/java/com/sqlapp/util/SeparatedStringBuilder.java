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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.data.schemas.properties.SpecificNameProperty;

/**
 * セパレータで区切られた文字のビルダクラス
 */
public class SeparatedStringBuilder implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6627317237113926725L;

	public SeparatedStringBuilder() {
	}

	/**
	 * 
	 * @param separator
	 *            セパレータ(';'など)
	 */
	public SeparatedStringBuilder(final String separator) {
		this.separator = separator;
	}

	/**
	 * 文字列のセパレータ
	 */
	private String separator = ";";
	/**
	 * 空文字の無視
	 */
	private boolean ignoreEmptyString = true;
	private boolean _trim = false;
	/**
	 * 重複値の除外
	 */
	private boolean _exceptSameValue = false;
	/**
	 * 結合する文字の前に追加する文字
	 */
	private String openQuate = null;

	/**
	 * 結合する文字の後ろに追加する文字
	 */
	private String closeQuate = null;
	/**
	 * 開始位置に追加する文字列
	 */
	private String start = null;
	/**
	 * 終了位置に追加する文字列
	 */
	private String end = null;

	/**
	 * 要素の数
	 * 
	 */
	public int getCount() {
		return this.elements.size();
	}

	private List<String> elements = new ArrayList<String>();

	/**
	 * 要素自身
	 * 
	 */
	public List<String> getElements() {
		return this.elements;
	}

	/**
	 * 要素の追加
	 * 
	 * @param obj
	 */
	public SeparatedStringBuilder add(final Object obj) {
		if (obj == null) {
			addElement(null);
		} else {
			addElement(obj.toString());
		}
		return this;
	}

	/**
	 * 要素の追加
	 * 
	 * @param args
	 */
	public SeparatedStringBuilder add(final Object... args) {
		if (args != null) {
			for (Object arg : args) {
				add(arg);
			}
		}
		return this;
	}

	/**
	 * 要素の追加
	 * 
	 * @param val
	 */
	private SeparatedStringBuilder addElement(final String val) {
		if (isIgnoreEmptyString() && (val == null || val.length() == 0)) {
			return this;
		}
		String trimValue = val;
		if (_trim && val != null) {
			trimValue = val.trim();
		}
		if (this._exceptSameValue) {
			if (!elements.contains(trimValue)) {
				elements.add(trimValue);
			}
		} else {
			elements.add(trimValue);
		}
		return this;
	}

	/**
	 * 要素の追加
	 * 
	 * @param values
	 *            追加する文字列(配列)
	 */
	public SeparatedStringBuilder add(final String... values) {
		if (values == null) {
			return this;
		}
		for (String val : values) {
			addElement(val);
		}
		return this;
	}

	/**
	 * 要素の追加
	 * 
	 * @param values
	 *            追加する文字列(リスト)
	 */
	public SeparatedStringBuilder add(final Collection<?> values) {
		if (values == null) {
			return this;
		}
		for (Object val : values) {
			if (val != null) {
				addElement(val.toString());
			} else {
				addElement(null);
			}
		}
		return this;
	}

	/**
	 * 名称を追加します
	 * 
	 * @param list
	 *            追加するオブジェクトのコレクション
	 */
	public <T extends NameProperty<? super T>> SeparatedStringBuilder addNames(
			final Collection<T> list) {
		for (T t : list) {
			if (t instanceof SpecificNameProperty){
				String name=((SpecificNameProperty<?>)t).getSpecificName();
				if (!CommonUtils.isEmpty(name)) {
					this.add(name);
					continue;
				}
			}
			this.add(t.getName());
		}
		return this;
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param columns
	 *            追加するカラムの配列
	 */
	public SeparatedStringBuilder addNames(
			final Column... columns) {
		for (Column column : columns) {
			this.add(column.getName());
		}
		return this;
	}

	/**
	 * 書式指定しての要素の追加
	 * 
	 * @param format
	 * @param args
	 */
	public SeparatedStringBuilder addFormat(final String format, Object... args) {
		return add(String.format(format, args));
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		if (!isEmpty(start)) {
			builder.append(start);
		}
		int size = elements.size();
		boolean addElement = false;
		for (int i = 0; i < size; i++) {
			String val = elements.get(i);
			if (addElement) {
				builder.append(this.separator);
			}
			if (!isEmpty(openQuate)) {
				builder.append(openQuate);
			}
			builder.append(val);
			addElement = true;
			if (!isEmpty(closeQuate)) {
				builder.append(closeQuate);
			}
		}
		if (!isEmpty(end)) {
			builder.append(end);
		}
		return builder.toString();
	}

	public int size() {
		return elements.size();
	}

	public boolean isIgnoreEmptyString() {
		return ignoreEmptyString;
	}

	public SeparatedStringBuilder setIgnoreEmptyString(boolean ignoreEmptyString) {
		this.ignoreEmptyString = ignoreEmptyString;
		return this;
	}

	public String getOpenQuate() {
		return openQuate;
	}

	public SeparatedStringBuilder setOpenQuate(final String openQuate) {
		this.openQuate = openQuate;
		return this;
	}

	public String getCloseQuate() {
		return closeQuate;
	}

	public SeparatedStringBuilder setCloseQuate(final String closeQuate) {
		this.closeQuate = closeQuate;
		return this;
	}

	/**
	 * @param separator
	 *            the separator to set
	 */
	public SeparatedStringBuilder setSeparator(String separator) {
		this.separator = separator;
		return this;
	}

	public String getStart() {
		return start;
	}

	public SeparatedStringBuilder setStart(final String start) {
		this.start = start;
		return this;
	}

	public String getEnd() {
		return end;
	}

	public SeparatedStringBuilder setEnd(final String end) {
		this.end = end;
		return this;
	}
	

	/**
	 * @return the _exceptSameValue
	 */
	public boolean isExceptSameValue() {
		return _exceptSameValue;
	}

	/**
	 * @param _exceptSameValue
	 *            the _exceptSameValue to set
	 */
	public SeparatedStringBuilder setExceptSameValue(boolean _exceptSameValue) {
		this._exceptSameValue = _exceptSameValue;
		return this;
	}
}
