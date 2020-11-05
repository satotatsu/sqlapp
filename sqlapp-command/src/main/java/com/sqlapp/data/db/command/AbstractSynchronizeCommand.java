/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.metadata.MetadataReader;
import com.sqlapp.data.db.metadata.MetadataReaderUtils;
import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.DbObjectCollection;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.EqualsHandler;
import com.sqlapp.data.schemas.ExcludeFilterEqualsHandler;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.CatalogNameProperty;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * スキーマ同期コマンド
 * 
 * @author tatsuo satoh
 * 
 */
public abstract class AbstractSynchronizeCommand extends
		AbstractFile2DataSourceCommand<DbObjectDifference> {

	private EqualsHandler equalsHandler = new ExcludeFilterEqualsHandler(
			SchemaProperties.CREATED_AT.getLabel(), SchemaProperties.LAST_ALTERED_AT.getLabel(),
			SchemaObjectProperties.ROWS.getLabel());

	/**
	 * @return the equalsHandler
	 */
	public EqualsHandler getEqualsHandler() {
		return equalsHandler;
	}

	/**
	 * @param equalsHandler
	 *            the equalsHandler to set
	 */
	public void setEqualsHandler(EqualsHandler equalsHandler) {
		this.equalsHandler = equalsHandler;
	}

	@Override
	protected List<DbObjectDifference> getTarget(
			List<DbCommonObject<?>> totalObjects, Connection connection) {
		List<DbObjectDifference> diffList = CommonUtils.list();
		for (DbCommonObject<?> object : totalObjects) {
			MetadataReader<?, ?> reader = MetadataReaderUtils
					.getMetadataReader(this.getDialect(), SchemaUtils
							.getSingularName(object.getClass().getSimpleName()));
			String catalogName = null;
			if (object instanceof CatalogNameProperty) {
				catalogName = ((CatalogNameProperty<?>) object)
						.getCatalogName();
			}
			SimpleBeanUtils.setValueCI(reader,
					SchemaProperties.CATALOG_NAME.getLabel(), catalogName);
			if (object instanceof DbObject) {
				@SuppressWarnings("rawtypes")
				DbObjectDifference diff = getDiff((DbObject) object, reader,
						connection);
				diffList.add(diff);
			} else {
				@SuppressWarnings("rawtypes")
				List<DbObjectDifference> ret = getDiff(
						(DbObjectCollection) object, reader, connection);
				diffList.addAll(ret);
			}
		}
		return diffList;

	}

	protected List<DbObjectDifference> getDiff(DbObjectCollection<?> objects,
			MetadataReader<?, ?> reader, Connection connection) {
		List<DbObjectDifference> diffList = CommonUtils.list();
		for (DbObject<?> obj : objects) {
			DbObjectDifference diff = getDiff(obj, reader, connection);
			diffList.add(diff);
		}
		return diffList;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected DbObjectDifference getDiff(DbObject obj, MetadataReader reader,
			Connection connection) {
		if (obj instanceof SchemaNameProperty) {
			SimpleBeanUtils.setValue(reader, SchemaProperties.SCHEMA_NAME.getLabel(),
					((SchemaNameProperty) obj).getSchemaName());
		}
		List<DbObject> originals = reader.getAllFull(connection);
		DbObject original = CommonUtils.first(originals);
		DbObjectDifference diff = original.diff(obj, getEqualsHandler());
		return diff;
	}

	@Override
	protected void handle(DbObjectDifference diff,
			SqlFactoryRegistry sqlFactoryRegistry, Connection connection) throws Exception {
		SqlFactory<?> sqlFactory = sqlFactoryRegistry.getSqlFactory(diff,
				SqlType.ALTER);
		Options sqlOptions = sqlFactory.getOptions()
				.clone();
		sqlFactory.setOptions(sqlOptions);
		List<SqlOperation> sqlTexts = sqlFactory.createDiffSql(diff);
		getSqlExecutor().execute(sqlTexts);
	}

}
