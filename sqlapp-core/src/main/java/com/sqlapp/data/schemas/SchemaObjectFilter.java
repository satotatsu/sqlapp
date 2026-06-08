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

package com.sqlapp.data.schemas;

import java.util.List;
import java.util.function.Predicate;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;

/**
 * スキーマオブジェクトのFilter
 * 
 * @author tatsuo satoh
 * 
 */
public class SchemaObjectFilter implements Predicate<AbstractSchemaObject<?>> {

	/**
	 * includeフィルター
	 */
	private String[] includes = null;
	/**
	 * excludesフィルター
	 */
	private String[] excludes = null;

	private List<SchemaObjectNameHolder> includeList = null;

	private List<SchemaObjectNameHolder> excludeList = null;

	@Override
	public boolean test(AbstractSchemaObject<?> obj) {
		return match(obj.getCatalogName(), obj.getSchemaName(), obj.getName());
	}

	private boolean match(String catalogName, String schemaName, String name) {
		if (!CommonUtils.isEmpty(excludeList) && find(catalogName, schemaName, name, excludeList)) {
			System.out.println("exclude. catalogName=" + catalogName + ", schemaName=" + schemaName + ", name=" + name
					+ " exclude=" + excludeList);
			return false;
		}
		if (includeList == null) {
			return true;
		}
		if (!CommonUtils.isEmpty(includeList) && find(catalogName, schemaName, name, includeList)) {
			System.out.println("include. catalogName=" + catalogName + ", schemaName=" + schemaName + ", name=" + name
					+ " include=" + includeList);
			return true;
		}
		System.out.println("no match catalogName=" + catalogName + ", schemaName=" + schemaName + ", name=" + name
				+ ", include=" + includeList + ", exclude=" + includeList);
		return false;
	}

	private boolean find(String catalogName, String schemaName, String name, List<SchemaObjectNameHolder> list) {
		for (SchemaObjectNameHolder objectNameHolder : list) {
			if (objectNameHolder.match(catalogName, schemaName, name)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the includes
	 */
	public String[] getIncludes() {
		return includes;
	}

	/**
	 * @param includes the includes to set
	 */
	public void setIncludes(String... includes) {
		if (includes != null) {
			includeList = CommonUtils.list();
			for (String arg : includes) {
				SchemaObjectNameHolder nameHolder = new SchemaObjectNameHolder(arg);
				includeList.add(nameHolder);
			}
		} else {
			includeList = null;
		}
		this.includes = includes;
	}

	/**
	 * @return the excludes
	 */
	public String[] getExcludes() {
		return excludes;
	}

	/**
	 * @param excludes the excludes to set
	 */
	public void setExcludes(String... excludes) {
		if (excludes != null) {
			excludeList = CommonUtils.list();
			for (String arg : excludes) {
				SchemaObjectNameHolder nameHolder = new SchemaObjectNameHolder(arg);
				excludeList.add(nameHolder);
			}
		} else {
			excludeList = null;
		}
		this.excludes = excludes;
	}

	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder(this.getClass());
		builder.add("includes", includes);
		builder.add("excludes", excludes);
		return builder.toString();
	}

}
