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
package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * Catalogの変更Operation
 * 
 * @author tatsuo satoh
 * 
 * @param <S>
 */
public abstract class AbstractAlterCatalogFactory<S extends AbstractSqlBuilder<?>>
		extends SimpleAlterSqlFactory<Schema, S> {

	private Set<String> ALTER_COLLECTION_ORDERS = CommonUtils.set();

	public AbstractAlterCatalogFactory() {
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
		ALTER_COLLECTION_ORDERS.add(name);
	}

	protected Set<String> getAlterCollectionOrder() {
		return ALTER_COLLECTION_ORDERS;
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		List<SqlOperation> sqlList = list();
		Map<String, Difference<?>> allDiff = difference.toDifference()
				.getChangedProperties(this.getDialect());
		for (String name : getAlterCollectionOrder()) {
			DbObjectDifferenceCollection collectionDiff = (DbObjectDifferenceCollection) allDiff
					.get(name);
			if (collectionDiff != null) {
				addAlterObjects(name, collectionDiff, sqlList);
			}
		}
		return sqlList;
	}

	protected void addAlterObjects(String propertyName,
			DbObjectDifferenceCollection collectionDiff, List<SqlOperation> sqlList) {
		addAlterObjects(collectionDiff, sqlList);
	}

	protected void addAlterObjects(DbObjectDifferenceCollection collectionDiff,
			List<SqlOperation> sqlList) {
		List<SqlOperation> ret = createDiffSql(collectionDiff.getList());
		sqlList.addAll(ret);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<SqlOperation> createSql(Schema obj) {
		return Collections.EMPTY_LIST;
	}

}
