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
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.ObjectNameReaderPredicate;
import com.sqlapp.data.db.metadata.ReadDbObjectPredicate;
import com.sqlapp.data.db.metadata.SchemaReader;

/**
 * TABLEコマンド
 * 
 * @author tatsuo satoh
 * 
 */
public abstract class AbstractTableCommand extends AbstractSchemaDataSourceCommand {
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

	protected SchemaReader getSchemaReader(final Dialect dialect) throws SQLException{
		try(Connection connection=this.getConnection()){
			return getSchemaReader(connection, dialect);
		}
	}

	protected SchemaReader getSchemaReader(final Connection connection, final Dialect dialect) throws SQLException{
		final CatalogReader catalogReader=dialect.getCatalogReader();
		final SchemaReader schemaReader=catalogReader.getSchemaReader();
		if (this.isOnlyCurrentCatalog()) {
			final String catalogName = getCurrentCatalogName(connection, dialect);
			schemaReader.setCatalogName(catalogName);
		}
		if (this.isOnlyCurrentSchema()) {
			final String schemaName = getCurrentSchemaName(connection, dialect);
			schemaReader.setSchemaName(schemaName);
		}
		schemaReader.setReadDbObjectPredicate(getMetadataReaderFilter());
		return schemaReader;
	}
	
	protected ReadDbObjectPredicate getMetadataReaderFilter() {
		final ReadDbObjectPredicate readerFilter = new ObjectNameReaderPredicate(
				this.getIncludeSchemas(), this.getExcludeSchemas(),
				this.getIncludeTables(), this.getExcludeTables());
		return readerFilter;
	}

	/**
	 * @return the includeSchemas
	 */
	public String[] getIncludeSchemas() {
		return includeSchemas;
	}

	/**
	 * @param includeSchemas
	 *            the includeSchemas to set
	 */
	public void setIncludeSchemas(final String... includeSchemas) {
		this.includeSchemas = includeSchemas;
	}

	/**
	 * @return the excludeSchemas
	 */
	public String[] getExcludeSchemas() {
		return excludeSchemas;
	}

	/**
	 * @param excludeSchemas
	 *            the excludeSchemas to set
	 */
	public void setExcludeSchemas(final String... excludeSchemas) {
		this.excludeSchemas = excludeSchemas;
	}

	/**
	 * @return the includeTables
	 */
	public String[] getIncludeTables() {
		return includeTables;
	}

	/**
	 * @param includeTables the includeTables to set
	 */
	public void setIncludeTables(final String... includeTables) {
		this.includeTables = includeTables;
	}

	/**
	 * @return the excludeTables
	 */
	public String[] getExcludeTables() {
		return excludeTables;
	}

	/**
	 * @param excludeTables the excludeTables to set
	 */
	public void setExcludeTables(final String... excludeTables) {
		this.excludeTables = excludeTables;
	}

	/**
	 * @return the onlyCurrentCatalog
	 */
	public boolean isOnlyCurrentCatalog() {
		return onlyCurrentCatalog;
	}

	/**
	 * @param onlyCurrentCatalog
	 *            the onlyCurrentCatalog to set
	 */
	public void setOnlyCurrentCatalog(final boolean onlyCurrentCatalog) {
		this.onlyCurrentCatalog = onlyCurrentCatalog;
	}

	/**
	 * @return the onlyCurrentSchema
	 */
	public boolean isOnlyCurrentSchema() {
		return onlyCurrentSchema;
	}

	/**
	 * @param onlyCurrentSchema
	 *            the onlyCurrentSchema to set
	 */
	public void setOnlyCurrentSchema(final boolean onlyCurrentSchema) {
		this.onlyCurrentSchema = onlyCurrentSchema;
	}


}
