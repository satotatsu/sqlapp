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

import java.util.List;
import java.util.function.Predicate;

import com.sqlapp.util.CommonUtils;

/**
 * オブジェクト名のFilter
 * 
 * @author tatsuo satoh
 * 
 */
public class NamedObjectFilter implements Predicate<AbstractNamedObject<?>> {

	/**
	 * includeフィルター
	 */
	private String[] includes = null;
	/**
	 * excludesフィルター
	 */
	private String[] excludes = null;

	private List<ObjectNameHolder> includeList = CommonUtils.list();

	private List<ObjectNameHolder> excludeList = CommonUtils.list();

	@Override
	public boolean test(AbstractNamedObject<?> obj) {
		return match(obj.getName());
	}

	private boolean match(String name) {
		if (excludeList.size() > 0 && find(name, excludeList)) {
			return false;
		}
		if (includeList.size() > 0 && find(name, includeList)) {
			return true;
		}
		return false;
	}

	private boolean find(String name, List<ObjectNameHolder> list) {
		for (ObjectNameHolder objectNameHolder : list) {
			if (!objectNameHolder.match(null, null, name)) {
				continue;
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
				ObjectNameHolder nameHolder = new ObjectNameHolder(arg);
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
				ObjectNameHolder nameHolder = new ObjectNameHolder(arg);
				excludeList.add(nameHolder);
			}
		}
		this.excludes = excludes;
	}

}
