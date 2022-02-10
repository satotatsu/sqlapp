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
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * スキーマの変更Operation
 * 
 * @author tatsuo satoh
 * 
 * @param <S>
 */
public abstract class AbstractAlterSchemaFactory<S extends AbstractSqlBuilder<?>>
		extends SimpleAlterSqlFactory<Schema, S> {

	private Set<String> ALTER_COLLECTION_ORDERS = CommonUtils.linkedSet();

	public AbstractAlterSchemaFactory() {
		initialize();
	}

	protected void initialize() {
		addCollectionOrder(SchemaObjectProperties.CONSTANTS.getLabel());
		addCollectionOrder(SchemaObjectProperties.SEQUENCES.getLabel());
		addCollectionOrder(SchemaObjectProperties.OPERATORS.getLabel());
		addCollectionOrder(SchemaObjectProperties.OPERATOR_CLASSES.getLabel());
		addCollectionOrder(SchemaObjectProperties.PACKAGES.getLabel());
		addCollectionOrder(SchemaObjectProperties.TYPES.getLabel());
		addCollectionOrder(SchemaObjectProperties.DOMAINS.getLabel());
		addCollectionOrder(SchemaObjectProperties.FUNCTIONS.getLabel());
		addCollectionOrder(SchemaObjectProperties.PROCEDURES.getLabel());
		addCollectionOrder(SchemaObjectProperties.EXTERNAL_TABLES.getLabel());
		addCollectionOrder(SchemaObjectProperties.DB_LINKS.getLabel());
		addCollectionOrder(SchemaObjectProperties.TABLES.getLabel());
		addCollectionOrder(SchemaObjectProperties.TABLE_LINKS.getLabel());
		addCollectionOrder(SchemaObjectProperties.VIEWS.getLabel());
		addCollectionOrder(SchemaObjectProperties.MVIEW_LOGS.getLabel());
		addCollectionOrder(SchemaObjectProperties.MVIEWS.getLabel());
		addCollectionOrder(SchemaObjectProperties.RULES.getLabel());
		addCollectionOrder(SchemaObjectProperties.TRIGGERS.getLabel());
		addCollectionOrder(SchemaObjectProperties.PACKAGE_BODIES.getLabel());
		addCollectionOrder(SchemaObjectProperties.TYPE_BODIES.getLabel());
		addCollectionOrder(SchemaObjectProperties.SYNONYMS.getLabel());
		addCollectionOrder(SchemaObjectProperties.DIMENSIONS.getLabel());
		addCollectionOrder(SchemaObjectProperties.XML_SCHEMAS.getLabel());
		addCollectionOrder(SchemaObjectProperties.EVENTS.getLabel());
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
		addAlterSchema(difference, allDiff, sqlList);
		for (String name : getAlterCollectionOrder()) {
			DbObjectDifferenceCollection collectionDiff = (DbObjectDifferenceCollection) allDiff
					.get(name);
			if (collectionDiff != null) {
				addAlterSchemaObjects(name, collectionDiff, sqlList);
			}
		}
		SqlFactory<Schema> setPathOperationOperation = this
				.getSqlFactoryRegistry().getSqlFactory(
						(Schema) difference.getTarget(),
						SqlType.SET_SEARCH_PATH_TO_SCHEMA);
		if (setPathOperationOperation != null && !CommonUtils.isEmpty(sqlList)) {
			Options operationOption = this.getOptions().clone();
			if (operationOption.isSetSearchPathToSchema()) {
				setPathOperationOperation.setOptions(operationOption);
				sqlList.addAll(0, setPathOperationOperation
						.createSql((Schema) difference.getTarget()));
			}
		}
		return sqlList;
	}

	protected void addAlterSchema(DbObjectDifference difference,
			Map<String, Difference<?>> allDiff, List<SqlOperation> sqlList) {
		DbObjectDifference characterSetDiff = (DbObjectDifference) allDiff
				.get(SchemaProperties.CHARACTER_SET.getLabel());
		DbObjectDifference collationDiff = (DbObjectDifference) allDiff
				.get(SchemaProperties.COLLATION.getLabel());
		DbObjectDifference characterSemanticsDiff = (DbObjectDifference) allDiff
				.get(SchemaProperties.CHARACTER_SEMANTICS.getLabel());
		addAlterSchema(difference, allDiff, characterSetDiff, collationDiff,
				characterSemanticsDiff, sqlList);
	}

	protected void addAlterSchema(DbObjectDifference difference,
			Map<String, Difference<?>> allDiff,
			DbObjectDifference characterSetDiff,
			DbObjectDifference collationDiff,
			DbObjectDifference characterSemanticsDiff, List<SqlOperation> sqlList) {
	}

	protected void addAlterSchemaObjects(String propertyName,
			DbObjectDifferenceCollection collectionDiff, List<SqlOperation> sqlList) {
		if (SchemaObjectProperties.TABLES.getLabel().equals(propertyName)) {
			addAlterTables(collectionDiff, sqlList);
		} else {
			addAlterSchemaObjects(collectionDiff, sqlList);
		}
	}

	protected void addAlterTables(DbObjectDifferenceCollection collectionDiff,
			List<SqlOperation> sqlList) {
		addAlterSchemaObjects(collectionDiff, sqlList);
	}

	protected void addAlterSchemaObjects(
			DbObjectDifferenceCollection collectionDiff, List<SqlOperation> sqlList) {
		List<SqlOperation> ret = createDiffSql(collectionDiff.getList());
		sqlList.addAll(ret);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<SqlOperation> createSql(Schema obj) {
		return Collections.EMPTY_LIST;
	}

}
