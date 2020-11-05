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

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.upperSet;

import java.util.Locale;
import java.util.Set;

/**
 * Orderの種類
 * 
 * @author satoh
 * 
 */
public enum Order implements EnumProperties {
	Asc("A", "ASC"), Desc("D", "DESC");
	private final Set<String> valueSet;

	private Order(String... values) {
		this.valueSet = upperSet(values);
		this.valueSet.add(this.toString());
	}

	/**
	 * 文字列からOrderの取得
	 * 
	 * @param ascOrDesc
	 */
	public static Order parse(String ascOrDesc) {
		if (isEmpty(ascOrDesc)) {
			return null;
		}
		for (Order order : Order.values()) {
			if (order.valueSet.contains(ascOrDesc)) {
				return order;
			}
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return this.toString().toUpperCase();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.EnumProperties#getDisplayName(java.util.Locale)
	 */
	@Override
	public String getDisplayName(Locale locale) {
		return getDisplayName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getSqlValue()
	 */
	@Override
	public String getSqlValue() {
		return getDisplayName();
	}
}
