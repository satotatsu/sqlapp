/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.MetadataReader;
import com.sqlapp.data.db.metadata.MetadataReaderUtils;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.ExcludeFilterEqualsHandler;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.properties.CatalogNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * データ同期コマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class SynchronizeDataCommand extends AbstractSynchronizeCommand {

	private final SqlType sqlType = SqlType.MERGE_BY_PK;

	public SynchronizeDataCommand() {
		this.setEqualsHandler(new ExcludeFilterEqualsHandler(
				SchemaProperties.CREATED_AT.getLabel(), SchemaProperties.LAST_ALTERED_AT.getLabel()));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.command.AbstractFile2DataSourceCommand#handle(java
	 * .util.List, java.sql.Connection)
	 */
	@Override
	protected void handle(final List<DbCommonObject<?>> totalObjects,
			final Connection connection, final Dialect dialect) throws Exception {
		for (final DbCommonObject<?> object : totalObjects) {
			final MetadataReader<?, ?> reader = MetadataReaderUtils
					.getMetadataReader(dialect, SchemaUtils
							.getSingularName(object.getClass().getSimpleName()));
			String catalogName = null;
			if (object instanceof CatalogNameProperty) {
				catalogName = ((CatalogNameProperty<?>) object)
						.getCatalogName();
			}
			SimpleBeanUtils.setValueCI(reader,
					SchemaProperties.CATALOG_NAME.getLabel(), catalogName);
			if (object instanceof Schema) {
				handle((Schema) object, connection, dialect);
			}
			if (object instanceof Catalog) {
				handle((Catalog) object, connection, dialect);
			}
			if (Table.class.equals(object.getClass())) {
				handle((Table) object, connection, dialect);
			}
		}
	}

	protected void handle(final Catalog obj, final Connection connection, final Dialect dialect) throws Exception {
		List<Table> tables = CommonUtils.list();
		for (final Schema schema : obj.getSchemas()) {
			tables.addAll(schema.getTables());
		}
		tables = SchemaUtils.getNewSortedTableList(tables,
				Table.TableOrder.CREATE.getComparator());
		for (final Table table : tables) {
			handle(table, connection, dialect);
		}
	}

	protected void handle(final Schema obj, final Connection connection, final Dialect dialect) throws Exception {
		final List<Table> tables = SchemaUtils.getNewSortedTableList(obj.getTables(),
				Table.TableOrder.CREATE.getComparator());
		for (final Table table : tables) {
			handle(table, connection, dialect);
		}
	}

	protected void handle(final Table obj, final Connection connection, final Dialect dialect) throws Exception {
		final SqlFactory<Table> sqlFactory = this.getSqlFactoryRegistry(dialect)
				.getSqlFactory(obj, sqlType);
		final List<SqlOperation> sqls = sqlFactory.createSql(obj);
		this.getSqlExecutor().execute(sqls);
	}

}
