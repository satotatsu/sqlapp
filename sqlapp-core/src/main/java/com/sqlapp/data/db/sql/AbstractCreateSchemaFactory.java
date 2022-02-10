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

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.AbstractSchemaObjectCollection;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * スキーマ生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractCreateSchemaFactory<S extends AbstractSqlBuilder<?>>
		extends SimpleSqlFactory<Schema, S> {

	private static final Set<String> createCollectionOrder = CommonUtils.set();

	public AbstractCreateSchemaFactory() {
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
		addCollectionOrder(SchemaObjectProperties.PACKAGE_BODIES.getLabel());
		addCollectionOrder(SchemaObjectProperties.TYPE_BODIES.getLabel());
		addCollectionOrder(SchemaObjectProperties.TRIGGERS.getLabel());
		addCollectionOrder(SchemaObjectProperties.SYNONYMS.getLabel());
		addCollectionOrder(SchemaObjectProperties.DIMENSIONS.getLabel());
		addCollectionOrder(SchemaObjectProperties.XML_SCHEMAS.getLabel());
		addCollectionOrder(SchemaObjectProperties.EVENTS.getLabel());
	}

	protected void addCollectionOrder(final String name) {
		createCollectionOrder.add(name);
	}

	protected Set<String> getCreateCollectionOrder() {
		return createCollectionOrder;
	}

	@Override
	public List<SqlOperation> createSql(final Schema schema) {
		final List<SqlOperation> sqlList = list();
		addCreateSchema(schema, sqlList);
		final Map<String, Object> map = schema.toMap();
		for (final String name : getCreateCollectionOrder()) {
			final AbstractSchemaObjectCollection<?> sc = (AbstractSchemaObjectCollection<?>) map
					.get(name);
			if (sc != null) {
				addCreateSchemaObjectCollection(name, sc, sqlList);
			}
		}
		return sqlList;
	}

	protected void addCreateSchema(final Schema schema,
			final List<SqlOperation> sqlList) {
		final S builder = createSqlBuilder();
		addCreateObject(schema, builder);
		addSql(sqlList, builder, SqlType.CREATE, schema);
	}

	protected void addCreateObject(final Schema obj, final S builder) {
		builder.create().schema();
		builder.name(obj);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void addCreateSchemaObjectCollection(final String propertyName,
			final AbstractSchemaObjectCollection<?> sc, final List<SqlOperation> sqlList) {
		final AbstractSchemaObject<?> s=CommonUtils.first(sc);
		if (s==null){
			return;
		}
		final SqlFactory<?> sqlFactory=this.getSqlFactoryRegistry().getSqlFactory(s, SqlType.CREATE);
		if (sqlFactory instanceof AbstractSqlFactory){
			List<AbstractSchemaObject<?>> sorted=CommonUtils.list((List<AbstractSchemaObject<?>>)sc);
			sorted=((AbstractSqlFactory)sqlFactory).sort(sorted);
			addCreateSortedSchemaObjectCollection(propertyName, sorted, sqlList);
		} else{
			addCreateSchemaObjectCollection(propertyName, sc, sqlList);
		}
	}
	
	protected void addCreateSortedSchemaObjectCollection(final String propertyName,
		final List<? extends AbstractSchemaObject<?>> sc, final List<SqlOperation> sqlList) {
		for(final AbstractSchemaObject<?> schemaObject:sc){
			@SuppressWarnings("rawtypes")
			final
			SqlFactory sqlFactory=this.getSqlFactoryRegistry().getSqlFactory(schemaObject, SqlType.CREATE);
			@SuppressWarnings("unchecked")
			final
			List<SqlOperation> operations=sqlFactory.createSql(schemaObject);
			sqlList.addAll(operations);
		}
	}

}
