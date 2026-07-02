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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.command.properties.ObjectTargetProperty;
import com.sqlapp.data.db.command.properties.OnlyCurrentCatalogProperty;
import com.sqlapp.data.db.command.properties.OnlyCurrentSchemaProperty;
import com.sqlapp.data.db.command.properties.PropertyUtils;
import com.sqlapp.data.db.command.properties.SchemaTargetProperty;
import com.sqlapp.data.db.command.properties.TableOptionsProperty;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.ObjectNameReaderPredicate;
import com.sqlapp.data.db.metadata.ReadDbObjectPredicate;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.sql.ConnectionSqlExecutor;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.AbstractSchemaObjectCollection;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.DbObjectCollection;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * DROPコマンド
 * 
 * @author tatsuo satoh
 * 
 */
@Getter
@Setter
public class DropObjectsCommand extends AbstractSchemaDataSourceCommand implements SchemaTargetProperty,
		ObjectTargetProperty, OnlyCurrentCatalogProperty, OnlyCurrentSchemaProperty, TableOptionsProperty {
	/**
	 * ダンプに含めるスキーマ
	 */
	private String[] includeSchemas = null;
	/**
	 * ダンプから除くスキーマ
	 */
	private String[] excludeSchemas = null;
	/**
	 * ダンプに含めるテーブル
	 */
	private String[] includeObjects = null;
	/**
	 * ダンプから除くテーブル
	 */
	private String[] excludeObjects = null;
	/**
	 * 現在のカタログのみを対象とするフラグ
	 */
	private boolean onlyCurrentCatalog = true;
	/**
	 * 現在のスキーマのみを対象とするフラグ
	 */
	private boolean onlyCurrentSchema = false;
	/**
	 * オブジェクトのDROPを実施
	 */
	private boolean dropObjects = false;
	/**
	 * テーブルのDROPを実施
	 */
	private boolean dropTables = false;

	private String preDropTableSql;

	private String afterDropTableSql;

	protected SchemaReader getSchemaReader(final Connection connection, final Dialect dialect) throws SQLException {
		final CatalogReader catalogReader = dialect.getCatalogReader();
		final SchemaReader schemaReader = catalogReader.getSchemaReader();
		if (this.isOnlyCurrentCatalog()) {
			final String catalogName = getCurrentCatalogName(connection);
			schemaReader.setCatalogName(catalogName);
		}
		if (this.isOnlyCurrentSchema()) {
			final String schemaName = getCurrentSchemaName(connection);
			schemaReader.setSchemaName(schemaName);
		}
		schemaReader.setReadDbObjectPredicate(getMetadataReaderFilter());
		return schemaReader;
	}

	protected ReadDbObjectPredicate getMetadataReaderFilter() {
		final ReadDbObjectPredicate readerFilter = new ObjectNameReaderPredicate(this.getIncludeSchemas(),
				this.getExcludeSchemas(), this.getIncludeObjects(), this.getExcludeObjects());
		return readerFilter;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.command.AbstractCommand#doRun()
	 */
	@Override
	protected void doRun() {
		execute(getDataSource(), connection -> {
			connection.setAutoCommit(false);
			final Dialect dialect = this.getDialect(connection);
			final SqlFactoryRegistry sqlFactoryRegistry = getSqlFactoryRegistry(dialect);
			final SchemaReader schemaReader = getSchemaReader(connection, dialect);
			final List<Schema> schemas = schemaReader.getAll(connection);
			for (final Schema schema : schemas) {
				if (this.isDropObjects()) {
					schemaReader.load(connection, schema);
					dropObjects(connection, schemaReader, schema, sqlFactoryRegistry);
				}
				if (this.isDropTables()) {
					if (!CommonUtils.isEmpty(this.getPreDropTableSql())) {
						try (Statement statement = connection.createStatement()) {
							statement.executeQuery(this.getPreDropTableSql());
						}
					}
					if (!this.isDropObjects()) {
						final TableReader tableReader = schemaReader.getTableReader();
						tableReader.setCatalogName(schema.getCatalogName());
						tableReader.setSchemaName(schema.getName());
						tableReader.loadFull(connection, schema);
					}
					dropTables(connection, schemaReader, schema, sqlFactoryRegistry);
				}
			}
			this.commit(connection);
		});
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void dropObjects(final Connection connection, final SchemaReader schemaReader, final Schema schema,
			final SqlFactoryRegistry sqlFactoryRegistry) throws SQLException {
		loadDetail(connection, schemaReader, schema);
		final ConnectionSqlExecutor operationExecutor = new ConnectionSqlExecutor(connection);
		for (final Map.Entry<String, AbstractSchemaObjectCollection> entry : schema.getChildObjectCollectionMap()
				.entrySet()) {
			if (SchemaObjectProperties.TABLES.getLabel().equals(entry.getKey())) {
				continue;
			}
			final DbObjectCollection<DbObject<?>> collection = entry.getValue();
			for (final DbObject<?> object : collection) {
				final SqlFactory<DbObject<?>> sqlFactory = sqlFactoryRegistry.getSqlFactory(object, SqlType.DROP);
				final List<SqlOperation> operations = sqlFactory.createSql(object);
				operationExecutor.execute(operations);
			}
		}
	}

	protected void loadDetail(final Connection connection, final SchemaReader schemaReader, final Schema schema)
			throws SQLException {
		schemaReader.load(connection, schema);
	}

	protected void dropTables(final Connection connection, final SchemaReader schemaReader, final Schema schema,
			final SqlFactoryRegistry sqlFactoryRegistry) throws SQLException {
		final ConnectionSqlExecutor sqlExecutor = new ConnectionSqlExecutor(connection);
		final SqlFactory<Table> sqlFactory = sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.DROP);
		for (Table table : schema.getTables()) {
			final List<SqlOperation> operations = sqlFactory.createSql(table);
			sqlExecutor.execute(operations);
			if (this.getTableOptions().getCommitPerTable().test(table)) {
				this.commit(connection);
			}
		}
		if (!CommonUtils.isEmpty(this.getAfterDropTableSql())) {
			try (Statement statement = connection.createStatement()) {
				statement.executeQuery(this.getAfterDropTableSql());
			}
		}
	}

	@Override
	public void setIncludeSchemas(final String... includeSchemas) {
		this.includeSchemas = PropertyUtils.convertArray(includeSchemas);
	}

	@Override
	public void setExcludeSchemas(final String... excludeSchemas) {
		this.excludeSchemas = PropertyUtils.convertArray(excludeSchemas);
	}

	@Override
	public void setIncludeObjects(final String... includeObject) {
		this.includeObjects = PropertyUtils.convertArray(includeObjects);
	}

	@Override
	public void setExcludeObjects(final String... excludeObjects) {
		this.excludeObjects = PropertyUtils.convertArray(excludeObjects);
	}

	private TableOptions tableOptions = new TableOptions();

	@Override
	public TableOptions getTableOptions() {
		return tableOptions;
	}

	@Override
	public void setTableOptions(TableOptions tableOptions) {
		this.tableOptions = tableOptions;
	}
}
