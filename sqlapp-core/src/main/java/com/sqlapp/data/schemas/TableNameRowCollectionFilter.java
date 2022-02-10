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

import java.util.function.Predicate;

/**
 * テーブル名のRowCollectionFilter
 * 
 * @author tatsuo satoh
 * 
 */
public class TableNameRowCollectionFilter implements Predicate<RowCollection> {

	private SchemaObjectFilter filter = new SchemaObjectFilter();

	public TableNameRowCollectionFilter() {

	}

	@Override
	public boolean test(RowCollection obj) {
		Table table = obj.getParent();
		return filter.test(table);
	}

	/**
	 * @return the includes
	 */
	public String[] getIncludes() {
		return filter.getIncludes();
	}

	/**
	 * @param includes
	 *            the includes to set
	 */
	public void setIncludes(String... includes) {
		filter.setIncludes(includes);
	}

	/**
	 * @return the excludes
	 */
	public String[] getExcludes() {
		return filter.getExcludes();
	}

	/**
	 * @param excludes
	 *            the excludes to set
	 */
	public void setExcludes(String... excludes) {
		filter.setExcludes(excludes);
	}
	
	/**
	 * @return the defaultInclude
	 */
	public boolean isDefaultInclude() {
		return filter.isDefaultInclude();
	}

	/**
	 * @param defaultInclude the defaultInclude to set
	 */
	public void setDefaultInclude(boolean defaultInclude) {
		this.filter.setDefaultInclude(defaultInclude);
	}

	@Override
	public String toString(){
		return filter.toString();
	}

}
