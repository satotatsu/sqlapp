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
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.command.properties.OnlyCurrentCatalogProperty;
import com.sqlapp.data.db.command.properties.OnlyCurrentSchemaProperty;
import com.sqlapp.data.db.command.properties.PropertyUtils;
import com.sqlapp.data.db.command.properties.SchemaTargetProperty;
import com.sqlapp.data.db.command.properties.TableOptionProperty;
import com.sqlapp.data.db.command.properties.TableTargetProperty;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.ObjectNameReaderPredicate;
import com.sqlapp.data.db.metadata.ReadDbObjectPredicate;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * TABLEコマンド
 * 
 * @author tatsuo satoh
 * 
 */
@Getter
@Setter
public abstract class AbstractTableCommand extends AbstractSchemaDataSourceCommand implements SchemaTargetProperty,
		TableTargetProperty, OnlyCurrentCatalogProperty, OnlyCurrentSchemaProperty, TableOptionProperty {
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
	private String[] includeTables = null;
	/**
	 * ダンプから除くテーブル
	 */
	private String[] excludeTables = null;
	/**
	 * 現在のカタログのみを対象とするフラグ
	 */
	private boolean onlyCurrentCatalog = true;
	/**
	 * 現在のスキーマのみを対象とするフラグ
	 */
	private boolean onlyCurrentSchema = false;

	private TableOptions tableOptions = new TableOptions();

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

	protected List<Table> getTables(Connection connection, final Dialect dialect) throws SQLException {
		final Catalog catalog = new Catalog();
		catalog.setDialect(dialect);
		final Map<String, Schema> schemaMap = getSchemaMap(connection, dialect);
		schemaMap.forEach((k, v) -> {
			catalog.getSchemas().add(v);
		});
		final List<Table> tables = CommonUtils.list();
		for (final Schema schema : catalog.getSchemas()) {
			for (final Table table : schema.getTables()) {
				tables.add(table);
			}
		}
		return tables;
	}

	private Map<String, Schema> getSchemaMap(Connection connection, final Dialect dialect) throws SQLException {
		final SchemaReader schemaReader = getSchemaReader(connection, dialect);
		final Map<String, Schema> schemaMap = this.getSchemas(connection, dialect, schemaReader, s -> true);
		return schemaMap;
	}

	protected ReadDbObjectPredicate getMetadataReaderFilter() {
		final ReadDbObjectPredicate readerFilter = new ObjectNameReaderPredicate(this.getIncludeSchemas(),
				this.getExcludeSchemas(), this.getIncludeTables(), this.getExcludeTables());
		return readerFilter;
	}

	@Override
	protected SqlFactoryRegistry getSqlFactoryRegistry(final Dialect dialect) {
		final SqlFactoryRegistry sqlFactoryRegistry = super.getSqlFactoryRegistry(dialect);
		sqlFactoryRegistry.getOption().setTableOptions(this.getTableOptions().clone());
		return sqlFactoryRegistry;
	}

	/**
	 * @param includeSchemas the includeSchemas to set
	 */
	@Override
	public void setIncludeSchemas(final String... includeSchemas) {
		this.includeSchemas = PropertyUtils.convertArray(includeSchemas);
	}

	/**
	 * @param excludeSchemas the excludeSchemas to set
	 */
	@Override
	public void setExcludeSchemas(final String... excludeSchemas) {
		this.excludeSchemas = PropertyUtils.convertArray(excludeSchemas);
	}

	/**
	 * @param includeTables the includeTables to set
	 */
	@Override
	public void setIncludeTables(final String... includeTables) {
		this.includeTables = PropertyUtils.convertArray(includeTables);
	}

	/**
	 * @param excludeTables the excludeTables to set
	 */
	public void setExcludeTables(final String... excludeTables) {
		this.excludeTables = PropertyUtils.convertArray(excludeTables);
	}

}
