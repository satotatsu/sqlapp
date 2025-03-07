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

package com.sqlapp.data.db.metadata;

import java.util.List;

import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.SchemaObjectNameHolder;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.properties.CatalogNameProperty;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.data.schemas.properties.SpecificNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;
import com.sqlapp.util.ToStringBuilder;

/**
 * オブジェクトの名称でreaderをフィルターするクラス
 * 
 * @author tatsuo satoh
 * 
 */
public class ObjectNameReaderPredicate implements ReadDbObjectPredicate {
	/**
	 * includeスキーマフィルター
	 */
	private String[] includeSchemas = null;
	/**
	 * excludesスキーマフィルター
	 */
	private String[] excludeSchemas = null;
	/**
	 * includeフィルター
	 */
	private String[] includes = null;
	/**
	 * excludesフィルター
	 */
	private String[] excludes = null;

	private DoubleKeyMap<String, String, SchemaNameHolder> includeSchemaMap = CommonUtils
			.doubleKeyMap();

	private DoubleKeyMap<String, String, SchemaNameHolder> excludeSchemaMap = CommonUtils
			.doubleKeyMap();

	private List<SchemaObjectNameHolder> includeList = CommonUtils.list();

	private List<SchemaObjectNameHolder> excludeList = CommonUtils.list();

	public ObjectNameReaderPredicate(String[] includeSchemas,
			String[] excludeSchemas, String[] includes, String[] excludes) {
		this.includeSchemas = includeSchemas;
		this.excludeSchemas = excludeSchemas;
		this.includes = includes;
		this.excludes = excludes;
		initialize(includeSchemas, excludeSchemas, includes, excludes);
	}

	protected void initialize(String[] includeSchemas, String[] excludeSchemas,
			String[] includes, String[] excludes) {
		if (includeSchemas != null) {
			for (String arg : includeSchemas) {
				SchemaNameHolder nameHolder = new SchemaNameHolder(arg);
				includeSchemaMap.put(nameHolder.catalogName,
						nameHolder.schemaName, nameHolder);
			}
		}
		if (excludeSchemas != null) {
			for (String arg : excludeSchemas) {
				SchemaNameHolder nameHolder = new SchemaNameHolder(arg);
				excludeSchemaMap.put(nameHolder.catalogName,
						nameHolder.schemaName, nameHolder);
			}
		}
		if (includes != null) {
			includeList.clear();
			for (String arg : includes) {
				SchemaObjectNameHolder nameHolder = new SchemaObjectNameHolder(arg);
				includeList.add(nameHolder);
			}
		}
		if (excludes != null) {
			excludeList.clear();
			for (String arg : excludes) {
				SchemaObjectNameHolder nameHolder = new SchemaObjectNameHolder(arg);
				excludeList.add(nameHolder);
			}
		}
	}

	@Override
	public boolean test(DbObject<?> dbObject, MetadataReader<?, ?> reader) {
		String catalogName = null;
		String schemaName = null;
		String specificName = null;
		String name = null;
		if (!(dbObject instanceof AbstractNamedObject)) {
			return true;
		}
		if (dbObject instanceof Schema) {
			return match((Schema) dbObject);
		}
		if (dbObject instanceof CatalogNameProperty) {
			CatalogNameProperty<?> nameProperty = (CatalogNameProperty<?>) dbObject;
			catalogName = nameProperty.getCatalogName();
		}
		if (dbObject instanceof SchemaNameProperty) {
			SchemaNameProperty<?> nameProperty = (SchemaNameProperty<?>) dbObject;
			schemaName = nameProperty.getSchemaName();
		}
		if (dbObject instanceof SpecificNameProperty) {
			SpecificNameProperty<?> nameProperty = (SpecificNameProperty<?>) dbObject;
			specificName = nameProperty.getSpecificName();
		}
		if (dbObject instanceof NameProperty) {
			NameProperty<?> nameProperty = (NameProperty<?>) dbObject;
			name = nameProperty.getName();
		}
		return match(catalogName, schemaName, specificName, name);
	}

	private boolean match(Schema schema) {
		String catalogName = null;
		String schemaName = schema.getName();
		SchemaNameHolder nameHolder = excludeSchemaMap.get(catalogName,
				schemaName);
		if (nameHolder != null) {
			return false;
		}
		if (includeSchemaMap.size() == 0) {
			return true;
		}
		nameHolder = includeSchemaMap.get(catalogName, schemaName);
		if (nameHolder != null) {
			return true;
		}
		return false;
	}

	private boolean match(String catalogName, String schemaName,
			String specificName, String name) {
		if (excludeList.size() > 0) {
			if (find(catalogName, schemaName, specificName, excludeList)) {
				return false;
			}
			if (find(catalogName, schemaName, name, excludeList)) {
				return false;
			}
		}
		if (includeList.size() == 0) {
			return true;
		} else {
			if (find(catalogName, schemaName, specificName, includeList)) {
				return true;
			}
			if (find(catalogName, schemaName, name, includeList)) {
				return true;
			}
		}
		return false;
	}

	private boolean find(String catalogName, String schemaName, String name,
			List<SchemaObjectNameHolder> list) {
		for (SchemaObjectNameHolder objectNameHolder : list) {
			if (!CommonUtils.eq(objectNameHolder.getObjectName(), name)) {
				continue;
			}
			if (objectNameHolder.getSchemaName() == null
					|| CommonUtils.eq(objectNameHolder.getSchemaName(),
							schemaName)) {
				if (objectNameHolder.getCatalogName() == null
						|| CommonUtils.eq(objectNameHolder.getCatalogName(),
								catalogName)) {
					return true;
				} else {
					return false;
				}
			} else {
				continue;
			}
		}
		return false;
	}

	static class SchemaNameHolder {
		SchemaNameHolder(String name) {
			String[] splits = name.split("\\.");
			if (splits.length == 1) {
				catalogName = null;
			} else if (splits.length == 2) {
				// catalogName = CommonUtils.first(splits);
			} else {
				throw new IllegalArgumentException("name=" + name);
			}
			schemaName = CommonUtils.last(splits);
		}

		String catalogName;
		String schemaName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder(this.getClass());
		toString(builder);
		return builder.toString();
	}

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	protected void toString(ToStringBuilder builder) {
		builder.add("includeSchemas", includeSchemas);
		builder.add("excludeSchemas", excludeSchemas);
		builder.add("includes", includes);
		builder.add("excludes", excludes);
	}

}
