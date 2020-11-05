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
package com.sqlapp.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;

public abstract class AbstractDatabaseMetaData extends
		AbstractJdbc<DatabaseMetaData> implements DatabaseMetaData {

	protected final Connection connection;

	public AbstractDatabaseMetaData(DatabaseMetaData nativeObject,
			Connection connection) {
		super(nativeObject);
		this.connection = connection;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#allProceduresAreCallable()
	 */
	@Override
	public boolean allProceduresAreCallable() throws SQLException {
		return nativeObject.allProceduresAreCallable();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#allTablesAreSelectable()
	 */
	@Override
	public boolean allTablesAreSelectable() throws SQLException {
		return nativeObject.allTablesAreSelectable();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#autoCommitFailureClosesAllResultSets()
	 */
	@Override
	public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
		return nativeObject.autoCommitFailureClosesAllResultSets();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#dataDefinitionCausesTransactionCommit()
	 */
	@Override
	public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
		return nativeObject.dataDefinitionCausesTransactionCommit();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#dataDefinitionIgnoredInTransactions()
	 */
	@Override
	public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
		return nativeObject.dataDefinitionIgnoredInTransactions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#deletesAreDetected(int)
	 */
	@Override
	public boolean deletesAreDetected(int type) throws SQLException {
		return nativeObject.deletesAreDetected(type);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#doesMaxRowSizeIncludeBlobs()
	 */
	@Override
	public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
		return nativeObject.doesMaxRowSizeIncludeBlobs();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getAttributes(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getAttributes(String catalog, String schemaPattern,
			String typeNamePattern, String attributeNamePattern)
			throws SQLException {
		ResultSet rs = nativeObject.getAttributes(catalog, schemaPattern,
				typeNamePattern, attributeNamePattern);
		if (rs == null) {
			return null;
		}
		return getResultSet(rs, this);
	}

	protected abstract ResultSet getResultSet(ResultSet rs,
			DatabaseMetaData databaseMetaData);

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getBestRowIdentifier(java.lang.String,
	 * java.lang.String, java.lang.String, int, boolean)
	 */
	@Override
	public ResultSet getBestRowIdentifier(String catalog, String schema,
			String table, int scope, boolean nullable) throws SQLException {
		ResultSet rs = nativeObject.getBestRowIdentifier(catalog, schema,
				table, scope, nullable);
		if (rs == null) {
			return null;
		}
		return getResultSet(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getCatalogSeparator()
	 */
	@Override
	public String getCatalogSeparator() throws SQLException {
		return nativeObject.getCatalogSeparator();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getCatalogTerm()
	 */
	@Override
	public String getCatalogTerm() throws SQLException {
		return nativeObject.getCatalogTerm();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getCatalogs()
	 */
	@Override
	public ResultSet getCatalogs() throws SQLException {
		ResultSet rs = nativeObject.getCatalogs();
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getClientInfoProperties()
	 */
	@Override
	public ResultSet getClientInfoProperties() throws SQLException {
		ResultSet rs = nativeObject.getClientInfoProperties();
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getColumnPrivileges(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getColumnPrivileges(String catalog, String schema,
			String table, String columnNamePattern) throws SQLException {
		ResultSet rs = nativeObject.getColumnPrivileges(catalog, schema, table,
				columnNamePattern);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getColumns(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getColumns(String catalog, String schemaPattern,
			String tableNamePattern, String columnNamePattern)
			throws SQLException {
		ResultSet rs = nativeObject.getColumns(catalog, schemaPattern,
				tableNamePattern, columnNamePattern);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getConnection()
	 */
	@Override
	public Connection getConnection() throws SQLException {
		return this.connection;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getCrossReference(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String, java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public ResultSet getCrossReference(String parentCatalog,
			String parentSchema, String parentTable, String foreignCatalog,
			String foreignSchema, String foreignTable) throws SQLException {
		ResultSet rs = nativeObject.getCrossReference(parentCatalog,
				parentSchema, parentTable, foreignCatalog, foreignSchema,
				foreignTable);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getDatabaseMajorVersion()
	 */
	@Override
	public int getDatabaseMajorVersion() throws SQLException {
		return nativeObject.getDatabaseMajorVersion();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getDatabaseMinorVersion()
	 */
	@Override
	public int getDatabaseMinorVersion() throws SQLException {
		return nativeObject.getDatabaseMinorVersion();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getDatabaseProductName()
	 */
	@Override
	public String getDatabaseProductName() throws SQLException {
		return nativeObject.getDatabaseProductName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getDatabaseProductVersion()
	 */
	@Override
	public String getDatabaseProductVersion() throws SQLException {
		return nativeObject.getDatabaseProductVersion();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getDefaultTransactionIsolation()
	 */
	@Override
	public int getDefaultTransactionIsolation() throws SQLException {
		return nativeObject.getDefaultTransactionIsolation();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getDriverMajorVersion()
	 */
	@Override
	public int getDriverMajorVersion() {
		return nativeObject.getDriverMajorVersion();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getDriverMinorVersion()
	 */
	@Override
	public int getDriverMinorVersion() {
		return nativeObject.getDriverMinorVersion();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getDriverName()
	 */
	@Override
	public String getDriverName() throws SQLException {
		return nativeObject.getDriverName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getDriverVersion()
	 */
	@Override
	public String getDriverVersion() throws SQLException {
		return nativeObject.getDriverVersion();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getExportedKeys(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getExportedKeys(String catalog, String schema, String table)
			throws SQLException {
		ResultSet rs = nativeObject.getExportedKeys(catalog, schema, table);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getExtraNameCharacters()
	 */
	@Override
	public String getExtraNameCharacters() throws SQLException {
		return nativeObject.getExtraNameCharacters();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getFunctionColumns(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getFunctionColumns(String catalog, String schemaPattern,
			String functionNamePattern, String columnNamePattern)
			throws SQLException {
		ResultSet rs = nativeObject.getFunctionColumns(catalog, schemaPattern,
				functionNamePattern, columnNamePattern);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getFunctions(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getFunctions(String catalog, String schemaPattern,
			String functionNamePattern) throws SQLException {
		ResultSet rs = nativeObject.getFunctions(catalog, schemaPattern,
				functionNamePattern);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getIdentifierQuoteString()
	 */
	@Override
	public String getIdentifierQuoteString() throws SQLException {
		return nativeObject.getIdentifierQuoteString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getImportedKeys(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getImportedKeys(String catalog, String schema, String table)
			throws SQLException {
		ResultSet rs = nativeObject.getImportedKeys(catalog, schema, table);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getIndexInfo(java.lang.String,
	 * java.lang.String, java.lang.String, boolean, boolean)
	 */
	@Override
	public ResultSet getIndexInfo(String catalog, String schema, String table,
			boolean unique, boolean approximate) throws SQLException {
		ResultSet rs = nativeObject.getIndexInfo(catalog, schema, table,
				unique, approximate);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getJDBCMajorVersion()
	 */
	@Override
	public int getJDBCMajorVersion() throws SQLException {
		return nativeObject.getJDBCMajorVersion();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getJDBCMinorVersion()
	 */
	@Override
	public int getJDBCMinorVersion() throws SQLException {
		return nativeObject.getJDBCMinorVersion();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxBinaryLiteralLength()
	 */
	@Override
	public int getMaxBinaryLiteralLength() throws SQLException {
		return nativeObject.getMaxBinaryLiteralLength();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxCatalogNameLength()
	 */
	@Override
	public int getMaxCatalogNameLength() throws SQLException {
		return nativeObject.getMaxCatalogNameLength();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxCharLiteralLength()
	 */
	@Override
	public int getMaxCharLiteralLength() throws SQLException {
		return nativeObject.getMaxCharLiteralLength();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxColumnNameLength()
	 */
	@Override
	public int getMaxColumnNameLength() throws SQLException {
		return nativeObject.getMaxColumnNameLength();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxColumnsInGroupBy()
	 */
	@Override
	public int getMaxColumnsInGroupBy() throws SQLException {
		return nativeObject.getMaxColumnsInGroupBy();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxColumnsInIndex()
	 */
	@Override
	public int getMaxColumnsInIndex() throws SQLException {
		return nativeObject.getMaxColumnsInIndex();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxColumnsInOrderBy()
	 */
	@Override
	public int getMaxColumnsInOrderBy() throws SQLException {
		return nativeObject.getMaxColumnsInOrderBy();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxColumnsInSelect()
	 */
	@Override
	public int getMaxColumnsInSelect() throws SQLException {
		return nativeObject.getMaxColumnsInSelect();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxColumnsInTable()
	 */
	@Override
	public int getMaxColumnsInTable() throws SQLException {
		return nativeObject.getMaxColumnsInTable();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxConnections()
	 */
	@Override
	public int getMaxConnections() throws SQLException {
		return nativeObject.getMaxConnections();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxCursorNameLength()
	 */
	@Override
	public int getMaxCursorNameLength() throws SQLException {
		return nativeObject.getMaxCursorNameLength();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxIndexLength()
	 */
	@Override
	public int getMaxIndexLength() throws SQLException {
		return nativeObject.getMaxIndexLength();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxProcedureNameLength()
	 */
	@Override
	public int getMaxProcedureNameLength() throws SQLException {
		return nativeObject.getMaxProcedureNameLength();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxRowSize()
	 */
	@Override
	public int getMaxRowSize() throws SQLException {
		return nativeObject.getMaxRowSize();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxSchemaNameLength()
	 */
	@Override
	public int getMaxSchemaNameLength() throws SQLException {
		return nativeObject.getMaxSchemaNameLength();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxStatementLength()
	 */
	@Override
	public int getMaxStatementLength() throws SQLException {
		return nativeObject.getMaxStatementLength();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxStatements()
	 */
	@Override
	public int getMaxStatements() throws SQLException {
		return nativeObject.getMaxStatements();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxTableNameLength()
	 */
	@Override
	public int getMaxTableNameLength() throws SQLException {
		return nativeObject.getMaxTableNameLength();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxTablesInSelect()
	 */
	@Override
	public int getMaxTablesInSelect() throws SQLException {
		return nativeObject.getMaxTablesInSelect();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getMaxUserNameLength()
	 */
	@Override
	public int getMaxUserNameLength() throws SQLException {
		return nativeObject.getMaxUserNameLength();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getNumericFunctions()
	 */
	@Override
	public String getNumericFunctions() throws SQLException {
		return nativeObject.getNumericFunctions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getPrimaryKeys(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getPrimaryKeys(String catalog, String schema, String table)
			throws SQLException {
		ResultSet rs = nativeObject.getPrimaryKeys(catalog, schema, table);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getProcedureColumns(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getProcedureColumns(String catalog, String schemaPattern,
			String procedureNamePattern, String columnNamePattern)
			throws SQLException {
		ResultSet rs = nativeObject.getProcedureColumns(catalog, schemaPattern,
				procedureNamePattern, columnNamePattern);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getProcedureTerm()
	 */
	@Override
	public String getProcedureTerm() throws SQLException {
		return nativeObject.getProcedureTerm();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getProcedures(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getProcedures(String catalog, String schemaPattern,
			String procedureNamePattern) throws SQLException {
		ResultSet rs = nativeObject.getProcedures(catalog, schemaPattern,
				procedureNamePattern);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getResultSetHoldability()
	 */
	@Override
	public int getResultSetHoldability() throws SQLException {
		return nativeObject.getResultSetHoldability();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getRowIdLifetime()
	 */
	@Override
	public RowIdLifetime getRowIdLifetime() throws SQLException {
		return nativeObject.getRowIdLifetime();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getSQLKeywords()
	 */
	@Override
	public String getSQLKeywords() throws SQLException {
		return nativeObject.getSQLKeywords();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getSQLStateType()
	 */
	@Override
	public int getSQLStateType() throws SQLException {
		return nativeObject.getSQLStateType();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getSchemaTerm()
	 */
	@Override
	public String getSchemaTerm() throws SQLException {
		return nativeObject.getSchemaTerm();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getSchemas()
	 */
	@Override
	public ResultSet getSchemas() throws SQLException {
		ResultSet rs = nativeObject.getSchemas();
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getSchemas(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public ResultSet getSchemas(String catalog, String schemaPattern)
			throws SQLException {
		return nativeObject.getSchemas(catalog, schemaPattern);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getSearchStringEscape()
	 */
	@Override
	public String getSearchStringEscape() throws SQLException {
		return nativeObject.getSearchStringEscape();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getStringFunctions()
	 */
	@Override
	public String getStringFunctions() throws SQLException {
		return nativeObject.getStringFunctions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getSuperTables(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getSuperTables(String catalog, String schemaPattern,
			String tableNamePattern) throws SQLException {
		ResultSet rs = nativeObject.getSuperTables(catalog, schemaPattern,
				tableNamePattern);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getSuperTypes(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getSuperTypes(String catalog, String schemaPattern,
			String typeNamePattern) throws SQLException {
		ResultSet rs = nativeObject.getSuperTables(catalog, schemaPattern,
				typeNamePattern);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getSystemFunctions()
	 */
	@Override
	public String getSystemFunctions() throws SQLException {
		return nativeObject.getSystemFunctions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getTablePrivileges(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getTablePrivileges(String catalog, String schemaPattern,
			String tableNamePattern) throws SQLException {
		ResultSet rs = nativeObject.getTablePrivileges(catalog, schemaPattern,
				tableNamePattern);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getTableTypes()
	 */
	@Override
	public ResultSet getTableTypes() throws SQLException {
		ResultSet rs = nativeObject.getTableTypes();
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getTables(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	public ResultSet getTables(String catalog, String schemaPattern,
			String tableNamePattern, String[] types) throws SQLException {
		ResultSet rs = nativeObject.getTables(catalog, schemaPattern,
				tableNamePattern, types);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getTimeDateFunctions()
	 */
	@Override
	public String getTimeDateFunctions() throws SQLException {
		return nativeObject.getTimeDateFunctions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getTypeInfo()
	 */
	@Override
	public ResultSet getTypeInfo() throws SQLException {
		ResultSet rs = nativeObject.getTypeInfo();
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getUDTs(java.lang.String,
	 * java.lang.String, java.lang.String, int[])
	 */
	@Override
	public ResultSet getUDTs(String catalog, String schemaPattern,
			String typeNamePattern, int[] types) throws SQLException {
		ResultSet rs = nativeObject.getUDTs(catalog, schemaPattern,
				typeNamePattern, types);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getURL()
	 */
	@Override
	public String getURL() throws SQLException {
		return nativeObject.getURL();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getUserName()
	 */
	@Override
	public String getUserName() throws SQLException {
		return nativeObject.getUserName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getVersionColumns(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet getVersionColumns(String catalog, String schema,
			String table) throws SQLException {
		ResultSet rs = nativeObject.getVersionColumns(catalog, schema, table);
		if (rs == null) {
			return null;
		}
		return new SqlappResultSet<DatabaseMetaData>(rs, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#insertsAreDetected(int)
	 */
	@Override
	public boolean insertsAreDetected(int type) throws SQLException {
		return nativeObject.insertsAreDetected(type);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#isCatalogAtStart()
	 */
	@Override
	public boolean isCatalogAtStart() throws SQLException {
		return nativeObject.isCatalogAtStart();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#isReadOnly()
	 */
	@Override
	public boolean isReadOnly() throws SQLException {
		return nativeObject.isReadOnly();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#locatorsUpdateCopy()
	 */
	@Override
	public boolean locatorsUpdateCopy() throws SQLException {
		return nativeObject.locatorsUpdateCopy();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#nullPlusNonNullIsNull()
	 */
	@Override
	public boolean nullPlusNonNullIsNull() throws SQLException {
		return nativeObject.nullPlusNonNullIsNull();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#nullsAreSortedAtEnd()
	 */
	@Override
	public boolean nullsAreSortedAtEnd() throws SQLException {
		return nativeObject.nullsAreSortedAtEnd();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#nullsAreSortedAtStart()
	 */
	@Override
	public boolean nullsAreSortedAtStart() throws SQLException {
		return nativeObject.nullsAreSortedAtStart();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#nullsAreSortedHigh()
	 */
	@Override
	public boolean nullsAreSortedHigh() throws SQLException {
		return nativeObject.nullsAreSortedAtStart();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#nullsAreSortedLow()
	 */
	@Override
	public boolean nullsAreSortedLow() throws SQLException {
		return nativeObject.nullsAreSortedLow();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#othersDeletesAreVisible(int)
	 */
	@Override
	public boolean othersDeletesAreVisible(int type) throws SQLException {
		return nativeObject.othersDeletesAreVisible(type);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#othersInsertsAreVisible(int)
	 */
	@Override
	public boolean othersInsertsAreVisible(int type) throws SQLException {
		return nativeObject.othersInsertsAreVisible(type);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#othersUpdatesAreVisible(int)
	 */
	@Override
	public boolean othersUpdatesAreVisible(int type) throws SQLException {
		return nativeObject.othersUpdatesAreVisible(type);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#ownDeletesAreVisible(int)
	 */
	@Override
	public boolean ownDeletesAreVisible(int type) throws SQLException {
		return nativeObject.ownDeletesAreVisible(type);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#ownInsertsAreVisible(int)
	 */
	@Override
	public boolean ownInsertsAreVisible(int type) throws SQLException {
		return nativeObject.ownInsertsAreVisible(type);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#ownUpdatesAreVisible(int)
	 */
	@Override
	public boolean ownUpdatesAreVisible(int type) throws SQLException {
		return nativeObject.ownUpdatesAreVisible(type);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#storesLowerCaseIdentifiers()
	 */
	@Override
	public boolean storesLowerCaseIdentifiers() throws SQLException {
		return nativeObject.storesLowerCaseIdentifiers();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#storesLowerCaseQuotedIdentifiers()
	 */
	@Override
	public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
		return nativeObject.storesLowerCaseQuotedIdentifiers();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#storesMixedCaseIdentifiers()
	 */
	@Override
	public boolean storesMixedCaseIdentifiers() throws SQLException {
		return nativeObject.storesMixedCaseIdentifiers();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#storesMixedCaseQuotedIdentifiers()
	 */
	@Override
	public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
		return nativeObject.storesMixedCaseQuotedIdentifiers();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#storesUpperCaseIdentifiers()
	 */
	@Override
	public boolean storesUpperCaseIdentifiers() throws SQLException {
		return nativeObject.storesUpperCaseIdentifiers();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#storesUpperCaseQuotedIdentifiers()
	 */
	@Override
	public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
		return nativeObject.storesUpperCaseQuotedIdentifiers();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsANSI92EntryLevelSQL()
	 */
	@Override
	public boolean supportsANSI92EntryLevelSQL() throws SQLException {
		return nativeObject.supportsANSI92EntryLevelSQL();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsANSI92FullSQL()
	 */
	@Override
	public boolean supportsANSI92FullSQL() throws SQLException {
		return nativeObject.supportsANSI92FullSQL();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsANSI92IntermediateSQL()
	 */
	@Override
	public boolean supportsANSI92IntermediateSQL() throws SQLException {
		return nativeObject.supportsANSI92IntermediateSQL();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsAlterTableWithAddColumn()
	 */
	@Override
	public boolean supportsAlterTableWithAddColumn() throws SQLException {
		return nativeObject.supportsAlterTableWithAddColumn();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsAlterTableWithDropColumn()
	 */
	@Override
	public boolean supportsAlterTableWithDropColumn() throws SQLException {
		return nativeObject.supportsAlterTableWithDropColumn();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsBatchUpdates()
	 */
	@Override
	public boolean supportsBatchUpdates() throws SQLException {
		return nativeObject.supportsBatchUpdates();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsCatalogsInDataManipulation()
	 */
	@Override
	public boolean supportsCatalogsInDataManipulation() throws SQLException {
		return nativeObject.supportsCatalogsInDataManipulation();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsCatalogsInIndexDefinitions()
	 */
	@Override
	public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
		return nativeObject.supportsCatalogsInIndexDefinitions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsCatalogsInPrivilegeDefinitions()
	 */
	@Override
	public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
		return nativeObject.supportsCatalogsInPrivilegeDefinitions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsCatalogsInProcedureCalls()
	 */
	@Override
	public boolean supportsCatalogsInProcedureCalls() throws SQLException {
		return nativeObject.supportsCatalogsInProcedureCalls();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsCatalogsInTableDefinitions()
	 */
	@Override
	public boolean supportsCatalogsInTableDefinitions() throws SQLException {
		return nativeObject.supportsCatalogsInTableDefinitions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsColumnAliasing()
	 */
	@Override
	public boolean supportsColumnAliasing() throws SQLException {
		return nativeObject.supportsColumnAliasing();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsConvert()
	 */
	@Override
	public boolean supportsConvert() throws SQLException {
		return nativeObject.supportsConvert();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsConvert(int, int)
	 */
	@Override
	public boolean supportsConvert(int fromType, int toType)
			throws SQLException {
		return nativeObject.supportsConvert(fromType, toType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsCoreSQLGrammar()
	 */
	@Override
	public boolean supportsCoreSQLGrammar() throws SQLException {
		return nativeObject.supportsCoreSQLGrammar();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsCorrelatedSubqueries()
	 */
	@Override
	public boolean supportsCorrelatedSubqueries() throws SQLException {
		return nativeObject.supportsCorrelatedSubqueries();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#
	 * supportsDataDefinitionAndDataManipulationTransactions()
	 */
	@Override
	public boolean supportsDataDefinitionAndDataManipulationTransactions()
			throws SQLException {
		return nativeObject
				.supportsDataDefinitionAndDataManipulationTransactions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsDataManipulationTransactionsOnly()
	 */
	@Override
	public boolean supportsDataManipulationTransactionsOnly()
			throws SQLException {
		return nativeObject.supportsDataManipulationTransactionsOnly();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsDifferentTableCorrelationNames()
	 */
	@Override
	public boolean supportsDifferentTableCorrelationNames() throws SQLException {
		return nativeObject.supportsDifferentTableCorrelationNames();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsExpressionsInOrderBy()
	 */
	@Override
	public boolean supportsExpressionsInOrderBy() throws SQLException {
		return nativeObject.supportsExpressionsInOrderBy();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsExtendedSQLGrammar()
	 */
	@Override
	public boolean supportsExtendedSQLGrammar() throws SQLException {
		return nativeObject.supportsExtendedSQLGrammar();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsFullOuterJoins()
	 */
	@Override
	public boolean supportsFullOuterJoins() throws SQLException {
		return nativeObject.supportsFullOuterJoins();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsGetGeneratedKeys()
	 */
	@Override
	public boolean supportsGetGeneratedKeys() throws SQLException {
		return nativeObject.supportsGetGeneratedKeys();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsGroupBy()
	 */
	@Override
	public boolean supportsGroupBy() throws SQLException {
		return nativeObject.supportsGroupBy();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsGroupByBeyondSelect()
	 */
	@Override
	public boolean supportsGroupByBeyondSelect() throws SQLException {
		return nativeObject.supportsGroupByBeyondSelect();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsGroupByUnrelated()
	 */
	@Override
	public boolean supportsGroupByUnrelated() throws SQLException {
		return nativeObject.supportsGroupByUnrelated();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsIntegrityEnhancementFacility()
	 */
	@Override
	public boolean supportsIntegrityEnhancementFacility() throws SQLException {
		return nativeObject.supportsIntegrityEnhancementFacility();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsLikeEscapeClause()
	 */
	@Override
	public boolean supportsLikeEscapeClause() throws SQLException {
		return nativeObject.supportsLikeEscapeClause();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsLimitedOuterJoins()
	 */
	@Override
	public boolean supportsLimitedOuterJoins() throws SQLException {
		return nativeObject.supportsLimitedOuterJoins();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsMinimumSQLGrammar()
	 */
	@Override
	public boolean supportsMinimumSQLGrammar() throws SQLException {
		return nativeObject.supportsMinimumSQLGrammar();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsMixedCaseIdentifiers()
	 */
	@Override
	public boolean supportsMixedCaseIdentifiers() throws SQLException {
		return nativeObject.supportsMixedCaseIdentifiers();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsMixedCaseQuotedIdentifiers()
	 */
	@Override
	public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
		return nativeObject.supportsMixedCaseQuotedIdentifiers();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsMultipleOpenResults()
	 */
	@Override
	public boolean supportsMultipleOpenResults() throws SQLException {
		return nativeObject.supportsMultipleOpenResults();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsMultipleResultSets()
	 */
	@Override
	public boolean supportsMultipleResultSets() throws SQLException {
		return nativeObject.supportsMultipleResultSets();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsMultipleTransactions()
	 */
	@Override
	public boolean supportsMultipleTransactions() throws SQLException {
		return nativeObject.supportsMultipleTransactions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsNamedParameters()
	 */
	@Override
	public boolean supportsNamedParameters() throws SQLException {
		return nativeObject.supportsNamedParameters();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsNonNullableColumns()
	 */
	@Override
	public boolean supportsNonNullableColumns() throws SQLException {
		return nativeObject.supportsNonNullableColumns();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsOpenCursorsAcrossCommit()
	 */
	@Override
	public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
		return nativeObject.supportsOpenCursorsAcrossCommit();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsOpenCursorsAcrossRollback()
	 */
	@Override
	public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
		return nativeObject.supportsOpenCursorsAcrossRollback();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsOpenStatementsAcrossCommit()
	 */
	@Override
	public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
		return nativeObject.supportsOpenStatementsAcrossCommit();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsOpenStatementsAcrossRollback()
	 */
	@Override
	public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
		return nativeObject.supportsOpenStatementsAcrossRollback();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsOrderByUnrelated()
	 */
	@Override
	public boolean supportsOrderByUnrelated() throws SQLException {
		return nativeObject.supportsOrderByUnrelated();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsOuterJoins()
	 */
	@Override
	public boolean supportsOuterJoins() throws SQLException {
		return nativeObject.supportsOuterJoins();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsPositionedDelete()
	 */
	@Override
	public boolean supportsPositionedDelete() throws SQLException {
		return nativeObject.supportsPositionedDelete();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsPositionedUpdate()
	 */
	@Override
	public boolean supportsPositionedUpdate() throws SQLException {
		return nativeObject.supportsPositionedUpdate();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsResultSetConcurrency(int, int)
	 */
	@Override
	public boolean supportsResultSetConcurrency(int type, int concurrency)
			throws SQLException {
		return nativeObject.supportsResultSetConcurrency(type, concurrency);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsResultSetHoldability(int)
	 */
	@Override
	public boolean supportsResultSetHoldability(int holdability)
			throws SQLException {
		return nativeObject.supportsResultSetHoldability(holdability);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsResultSetType(int)
	 */
	@Override
	public boolean supportsResultSetType(int type) throws SQLException {
		return nativeObject.supportsResultSetType(type);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsSavepoints()
	 */
	@Override
	public boolean supportsSavepoints() throws SQLException {
		return nativeObject.supportsSavepoints();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsSchemasInDataManipulation()
	 */
	@Override
	public boolean supportsSchemasInDataManipulation() throws SQLException {
		return nativeObject.supportsSchemasInDataManipulation();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsSchemasInIndexDefinitions()
	 */
	@Override
	public boolean supportsSchemasInIndexDefinitions() throws SQLException {
		return nativeObject.supportsSchemasInIndexDefinitions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsSchemasInPrivilegeDefinitions()
	 */
	@Override
	public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
		return nativeObject.supportsSchemasInPrivilegeDefinitions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsSchemasInProcedureCalls()
	 */
	@Override
	public boolean supportsSchemasInProcedureCalls() throws SQLException {
		return nativeObject.supportsSchemasInProcedureCalls();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsSchemasInTableDefinitions()
	 */
	@Override
	public boolean supportsSchemasInTableDefinitions() throws SQLException {
		return nativeObject.supportsSchemasInTableDefinitions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsSelectForUpdate()
	 */
	@Override
	public boolean supportsSelectForUpdate() throws SQLException {
		return nativeObject.supportsSelectForUpdate();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsStatementPooling()
	 */
	@Override
	public boolean supportsStatementPooling() throws SQLException {
		return nativeObject.supportsStatementPooling();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsStoredFunctionsUsingCallSyntax()
	 */
	@Override
	public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
		return nativeObject.supportsStoredFunctionsUsingCallSyntax();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsStoredProcedures()
	 */
	@Override
	public boolean supportsStoredProcedures() throws SQLException {
		return nativeObject.supportsStoredProcedures();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsSubqueriesInComparisons()
	 */
	@Override
	public boolean supportsSubqueriesInComparisons() throws SQLException {
		return nativeObject.supportsSubqueriesInComparisons();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsSubqueriesInExists()
	 */
	@Override
	public boolean supportsSubqueriesInExists() throws SQLException {
		return nativeObject.supportsSubqueriesInExists();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsSubqueriesInIns()
	 */
	@Override
	public boolean supportsSubqueriesInIns() throws SQLException {
		return nativeObject.supportsSubqueriesInIns();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsSubqueriesInQuantifieds()
	 */
	@Override
	public boolean supportsSubqueriesInQuantifieds() throws SQLException {
		return nativeObject.supportsSubqueriesInQuantifieds();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsTableCorrelationNames()
	 */
	@Override
	public boolean supportsTableCorrelationNames() throws SQLException {
		return nativeObject.supportsTableCorrelationNames();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsTransactionIsolationLevel(int)
	 */
	@Override
	public boolean supportsTransactionIsolationLevel(int level)
			throws SQLException {
		return nativeObject.supportsTransactionIsolationLevel(level);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsTransactions()
	 */
	@Override
	public boolean supportsTransactions() throws SQLException {
		return nativeObject.supportsTransactions();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsUnion()
	 */
	@Override
	public boolean supportsUnion() throws SQLException {
		return nativeObject.supportsUnion();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#supportsUnionAll()
	 */
	@Override
	public boolean supportsUnionAll() throws SQLException {
		return nativeObject.supportsUnionAll();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#updatesAreDetected(int)
	 */
	@Override
	public boolean updatesAreDetected(int type) throws SQLException {
		return nativeObject.updatesAreDetected(type);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#usesLocalFilePerTable()
	 */
	@Override
	public boolean usesLocalFilePerTable() throws SQLException {
		return nativeObject.usesLocalFilePerTable();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#usesLocalFiles()
	 */
	@Override
	public boolean usesLocalFiles() throws SQLException {
		return nativeObject.usesLocalFiles();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#getPseudoColumns(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String)
	 */
	public ResultSet getPseudoColumns(String catalog, String schemaPattern,
			String tableNamePattern, String columnNamePattern)
			throws SQLException {
		return nativeObject.getPseudoColumns(catalog, schemaPattern,
				tableNamePattern, columnNamePattern);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.DatabaseMetaData#generatedKeyAlwaysReturned()
	 */
	public boolean generatedKeyAlwaysReturned() throws SQLException {
		return nativeObject.generatedKeyAlwaysReturned();
	}
}
