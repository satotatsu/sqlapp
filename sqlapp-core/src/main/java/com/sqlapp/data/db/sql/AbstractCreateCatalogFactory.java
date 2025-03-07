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

package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.AbstractDbObjectCollection;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * Catalog生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractCreateCatalogFactory<S extends AbstractSqlBuilder<?>>
		extends SimpleSqlFactory<Catalog, S> {

	private static final Set<String> createCollectionOrder = CommonUtils.linkedSet();

	public AbstractCreateCatalogFactory() {
		initialize();
	}

	protected void initialize() {
		addCollectionOrder(SchemaObjectProperties.TABLE_SPACES.getLabel());
		addCollectionOrder(SchemaObjectProperties.ROLES.getLabel());
		addCollectionOrder(SchemaObjectProperties.USERS.getLabel());
		addCollectionOrder(SchemaObjectProperties.DIRECTORIES.getLabel());
		addCollectionOrder(SchemaObjectProperties.PARTITION_FUNCTIONS.getLabel());
		addCollectionOrder(SchemaObjectProperties.PARTITION_SCHEMES.getLabel());
		addCollectionOrder(SchemaObjectProperties.ASSEMBLIES.getLabel());
		addCollectionOrder(SchemaObjectProperties.SCHEMAS.getLabel());
		addCollectionOrder(SchemaObjectProperties.SCHEMA_PRIVILEGES.getLabel());
		addCollectionOrder(SchemaObjectProperties.PUBLIC_DB_LINKS.getLabel());
		addCollectionOrder(SchemaObjectProperties.PUBLIC_SYNONYMS.getLabel());
		addCollectionOrder(SchemaObjectProperties.USER_PRIVILEGES.getLabel());
		addCollectionOrder(SchemaObjectProperties.OBJECT_PRIVILEGES.getLabel());
		addCollectionOrder(SchemaObjectProperties.ROLE_PRIVILEGES.getLabel());
		addCollectionOrder(SchemaObjectProperties.ROLE_MEMBERS.getLabel());
		addCollectionOrder(SchemaObjectProperties.COLUMN_PRIVILEGES.getLabel());
	}

	protected void addCollectionOrder(String name) {
		createCollectionOrder.add(name);
	}

	protected Set<String> getCreateCollectionOrder() {
		return createCollectionOrder;
	}

	@Override
	public List<SqlOperation> createSql(final Catalog catalog) {
		List<SqlOperation> sqlList = list();
		Map<String, Object> map = catalog.toMap();
		for (String name : getCreateCollectionOrder()) {
			AbstractDbObjectCollection<?> sc = (AbstractDbObjectCollection<?>) map
					.get(name);
			if (sc != null) {
				addCreateDbObjectCollection(name, sc, sqlList);
			}
		}
		return sqlList;
	}

	protected void addCreateDbObjectCollection(String propertyName,
			AbstractDbObjectCollection<?> sc, List<SqlOperation> sqlList) {
		for(AbstractDbObject<?> schemaObject:sc){
			@SuppressWarnings("rawtypes")
			SqlFactory sqlFactory=this.getSqlFactoryRegistry().getSqlFactory(schemaObject, SqlType.CREATE);
			@SuppressWarnings("unchecked")
			List<SqlOperation> operations=sqlFactory.createSql(schemaObject);
			sqlList.addAll(operations);
		}
	}

}
