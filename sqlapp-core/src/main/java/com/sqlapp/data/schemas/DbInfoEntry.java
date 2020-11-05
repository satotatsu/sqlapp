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

import static com.sqlapp.util.CommonUtils.compare;

import java.io.Serializable;
import java.util.Map;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;

public final class DbInfoEntry implements Serializable,
		Comparable<DbInfoEntry>, Cloneable {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 2480796638330528618L;
	private String productName = null;

	private Map<String, String> keyValues = CommonUtils.treeMap();

	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder(this.getClass());
		builder.add("productName", productName);
		builder.add("keyValues", keyValues);
		return builder.toString();
	}

	/**
	 * @return the keyValues
	 */
	public Map<String, String> getKeyValues() {
		return keyValues;
	}

	/**
	 * @param keyValues
	 *            the keyValues to set
	 */
	public void setKeyValues(Map<String, String> keyValues) {
		this.keyValues = keyValues;
	}

	/**
	 * @return the productName
	 */
	public String getProductName() {
		return productName;
	}

	/**
	 * @param productName
	 *            the productName to set
	 */
	public void setProductName(String productName) {
		this.productName = productName;
	}

	@Override
	public int compareTo(DbInfoEntry o) {
		int ret = compare(this.productName, o.productName);
		if (ret != 0) {
			return ret;
		}
		return 0;
	}

	@Override
	public DbInfoEntry clone() {
		try {
			return (DbInfoEntry) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new Error(e.toString());
		}
	}
}
