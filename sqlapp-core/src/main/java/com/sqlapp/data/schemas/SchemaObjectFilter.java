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
	
	private boolean defaultInclude=false;

	private List<SchemaObjectNameHolder> includeList = CommonUtils.list();

	private List<SchemaObjectNameHolder> excludeList = CommonUtils.list();

	@Override
	public boolean test(AbstractSchemaObject<?> obj) {
		return match(obj.getCatalogName(), obj.getSchemaName(), obj.getName());
	}

	private boolean match(String catalogName, String schemaName, String name) {
		if (excludeList.size() > 0
				&& find(catalogName, schemaName, name, excludeList)) {
			return false;
		}
		if (includeList.size() > 0
				&& find(catalogName, schemaName, name, includeList)) {
			return true;
		}
		return isDefaultInclude();
	}

	private boolean find(String catalogName, String schemaName, String name,
			List<SchemaObjectNameHolder> list) {
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
	 * @param includes
	 *            the includes to set
	 */
	public void setIncludes(String... includes) {
		includeList.clear();
		if (includes != null) {
			for (String arg : includes) {
				SchemaObjectNameHolder nameHolder = new SchemaObjectNameHolder(arg);
				includeList.add(nameHolder);
			}
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
	 * @param excludes
	 *            the excludes to set
	 */
	public void setExcludes(String... excludes) {
		excludeList.clear();
		if (excludes != null) {
			for (String arg : excludes) {
				SchemaObjectNameHolder nameHolder = new SchemaObjectNameHolder(arg);
				excludeList.add(nameHolder);
			}
		}
		this.excludes = excludes;
	}

	/**
	 * @return the defaultInclude
	 */
	public boolean isDefaultInclude() {
		return defaultInclude;
	}

	/**
	 * @param defaultInclude the defaultInclude to set
	 */
	public void setDefaultInclude(boolean defaultInclude) {
		this.defaultInclude = defaultInclude;
	}

	@Override
	public String toString(){
		ToStringBuilder builder=new ToStringBuilder(this.getClass());
		builder.add("includes", includes);
		builder.add("excludes", excludes);
		return builder.toString();
	}

}
