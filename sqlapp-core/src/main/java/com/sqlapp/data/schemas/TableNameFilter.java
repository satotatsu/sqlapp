/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.schemas;

import java.util.Set;
import java.util.function.Predicate;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.PropertyUtils;
import com.sqlapp.util.ToStringBuilder;

/**
 * TableNameFilter
 * 
 * @author tatsuo satoh
 * 
 */
public class TableNameFilter implements Predicate<DbCommonObject<?>> {

	/**
	 * includeフィルター
	 */
	private String[] includes = null;
	/**
	 * excludesフィルター
	 */
	private String[] excludes = null;
	/**
	 * includeフィルター
	 */
	private Set<String> includesSet = null;
	/**
	 * excludesフィルター
	 */
	private Set<String> excludesSet = null;

	public TableNameFilter() {

	}

	@Override
	public boolean test(DbCommonObject<?> obj) {
		if (obj instanceof Catalog) {
			return true;
		}
		if (obj instanceof Schema) {
			return true;
		}
		if (obj instanceof SchemaCollection) {
			return true;
		}
		if (obj instanceof TableCollection) {
			return true;
		}
		String schemaName = getTableName(obj);
		return match(schemaName);
	}

	private String getTableName(DbCommonObject<?> obj) {
		if (obj instanceof Table) {
			return (Table.class.cast(obj)).getName();
		}
		if (obj instanceof RowCollection) {
			return getTableName((RowCollection.class.cast(obj)).getParent());
		}
		return (String) SchemaProperties.TABLE_NAME.getValue(obj);
	}

	private boolean match(String name) {
		if (!CommonUtils.isEmpty(excludesSet) && find(name, excludesSet)) {
			return false;
		}
		if (includesSet == null) {
			return true;
		}
		if (!CommonUtils.isEmpty(includesSet) && find(name, includesSet)) {
			return true;
		}
		return false;
	}

	private boolean find(String schemaName, Set<String> set) {
		if (set == null) {
			return false;
		}
		return set.contains(schemaName);
	}

	/**
	 * @param includes the includes to set
	 */
	public void setInclude(final String... includes) {
		this.includes = PropertyUtils.convertArray(includes);
		if (this.includes != null) {
			includesSet = CommonUtils.upperSet(includes);
		} else {
			includesSet = null;
		}
	}

	/**
	 * @param excludes the excludeSchemas to set
	 */
	public void setExclude(final String... excludes) {
		this.excludes = PropertyUtils.convertArray(excludes);
		if (this.excludes != null) {
			excludesSet = CommonUtils.upperSet(excludes);
		} else {
			excludesSet = null;
		}
	}

	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder(this.getClass());
		builder.add("includes", includes);
		builder.add("excludes", excludes);
		return builder.toString();
	}
}
