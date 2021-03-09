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
package com.sqlapp.data.schemas.function;

import java.io.Serializable;
import java.util.function.Predicate;

import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;
import com.sqlapp.util.TripleKeyMap;

public class RowCollectionNamePredicate implements Predicate<RowCollection>,Serializable {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6341437679279254394L;
	/**
	 * includeフィルター
	 */
	private String[] includes = null;
	/**
	 * excludesフィルター
	 */
	private String[] excludes = null;

	private final TripleKeyMap<String, String, String, ObjectNameHolder> includeMap = CommonUtils
			.tripleKeyMap();

	private final TripleKeyMap<String, String, String, ObjectNameHolder> excludeMap = CommonUtils
			.tripleKeyMap();

	public RowCollectionNamePredicate(final String[] includes, final String[] excludes) {
		this.includes = includes;
		this.excludes = excludes;
		initialize(includes, excludes);
	}

	protected void initialize(final String[] includes, final String[] excludes) {
		if (includes != null) {
			for (final String arg : includes) {
				final ObjectNameHolder nameHolder = new ObjectNameHolder(arg);
				includeMap.put(nameHolder.catalogName, nameHolder.schemaName,
						nameHolder.objectName, nameHolder);
			}
		}
		if (excludes != null) {
			for (final String arg : excludes) {
				final ObjectNameHolder nameHolder = new ObjectNameHolder(arg);
				excludeMap.put(nameHolder.catalogName, nameHolder.schemaName,
						nameHolder.objectName, nameHolder);
			}
		}
	}

	@Override
	public boolean test(final RowCollection obj) {
		final String catalogName = null;
		final String schemaName = obj.getParent().getSchemaName();
		final String name = obj.getParent().getName();
		return match(catalogName, schemaName, name);
	}

	private boolean match(final String catalogName, final String schemaName, final String name) {
		ObjectNameHolder nameHolder = excludeMap.get(catalogName, schemaName,
				name);
		if (nameHolder != null) {
			return false;
		}
		if (includeMap.size() == 0) {
			return true;
		}
		nameHolder = includeMap.get(catalogName, schemaName, name);
		if (nameHolder != null) {
			return true;
		}
		return false;
	}

	static class ObjectNameHolder {
		ObjectNameHolder(final String name) {
			final String[] splits = name.split("\\.");
			if (splits.length == 1) {
				schemaName = null;
			} else if (splits.length == 2) {
				schemaName = CommonUtils.first(splits);
			} else {
				throw new IllegalArgumentException("name=" + name);
			}
			objectName = CommonUtils.last(splits);
		}

		String catalogName=null;
		String schemaName;
		String objectName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final ToStringBuilder builder = new ToStringBuilder(this.getClass());
		toString(builder);
		return builder.toString();
	}

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	protected void toString(final ToStringBuilder builder) {
		builder.add("includes", includes);
		builder.add("excludes", excludes);
	}
}
