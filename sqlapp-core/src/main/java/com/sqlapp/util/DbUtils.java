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

package com.sqlapp.util;

import static com.sqlapp.util.CommonUtils.caseInsensitiveLinkedMap;
import static com.sqlapp.util.CommonUtils.emptyToNull;
import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Table;

/**
 * JDBC関係のユーティリティ
 */
public class DbUtils {

	/**
	 * カタログ名のキー
	 */
	public static final String CATALOG_NAME = "catalogName";
	/**
	 * スキーマ名のキー
	 */
	public static final String SCHEMA_NAME = "schemaName";
	/**
	 * テーブル名のキー
	 */
	public static final String TABLE_NAME = "tableName";

	/**
	 * DBメタデータの取得
	 * 
	 * @param connection
	 */
	public static DatabaseMetaData getDatabaseMetaData(Connection connection) {
		try {
			return connection.getMetaData();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * DBコネクションの取得
	 * 
	 * @param databaseMetaData
	 */
	public static Connection getConnection(DatabaseMetaData databaseMetaData) {
		try {
			return databaseMetaData.getConnection();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * システム関数を取得します
	 * 
	 * @param databaseMetaData
	 */
	public static Set<String> getSystemFunctions(
			DatabaseMetaData databaseMetaData) {
		String functions = null;
		try {
			functions = databaseMetaData.getSystemFunctions();
			Set<String> ret = CommonUtils
					.set(CommonUtils.split(functions, ","));
			return ret;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * NUMERIC関数を取得します
	 * 
	 * @param databaseMetaData
	 */
	public static Set<String> getNumericFunctions(
			DatabaseMetaData databaseMetaData) {
		String functions = null;
		try {
			functions = databaseMetaData.getNumericFunctions();
			Set<String> ret = CommonUtils
					.set(CommonUtils.split(functions, ","));
			return ret;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 文字列関数を取得します
	 * 
	 * @param databaseMetaData
	 */
	public static Set<String> getStringFunctions(
			DatabaseMetaData databaseMetaData) {
		String functions = null;
		try {
			functions = databaseMetaData.getStringFunctions();
			Set<String> ret = CommonUtils
					.set(CommonUtils.split(functions, ","));
			return ret;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 日付関数を取得します
	 * 
	 * @param databaseMetaData
	 */
	public static Set<String> getTimeDateFunctions(
			DatabaseMetaData databaseMetaData) {
		String functions = null;
		try {
			functions = databaseMetaData.getTimeDateFunctions();
			Set<String> ret = CommonUtils
					.set(CommonUtils.split(functions, ","));
			return ret;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 関数を取得します
	 * 
	 * @param databaseMetaData
	 */
	public static Set<String> getFunctions(DatabaseMetaData databaseMetaData) {
		Set<String> ret = CommonUtils.set();
		ret.addAll(getTimeDateFunctions(databaseMetaData));
		ret.addAll(getStringFunctions(databaseMetaData));
		ret.addAll(getNumericFunctions(databaseMetaData));
		ret.addAll(getSystemFunctions(databaseMetaData));
		return ret;
	}

	/**
	 * DBプロダクト名を取得します
	 * 
	 * @param databaseMetaData
	 */
	public static String getDatabaseProductName(
			DatabaseMetaData databaseMetaData) {
		String result = null;
		try {
			result = databaseMetaData.getDatabaseProductName();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return result;
	}

	/**
	 * DBプロダクトバージョンを取得します
	 * 
	 * @param databaseMetaData
	 */
	public static String getDatabaseProductVersion(
			DatabaseMetaData databaseMetaData) {
		String result = null;
		try {
			result = databaseMetaData.getDatabaseProductVersion();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return result;
	}

	private static final Pattern MARIADB_PATTERN=Pattern.compile(".*?-(?<version>.*)-MariaDB.*"); 
	
	/**
	 * 製品バージョン情報を取得します
	 * 
	 * @param databaseMetaData
	 */
	public static ProductVersionInfo getProductVersionInfo(
			final DatabaseMetaData databaseMetaData) {
		final ProductVersionInfo productVersionInfo = new ProductVersionInfo();
		final String dbProductVersion = getDatabaseProductVersion(databaseMetaData);
		Matcher matcher=MARIADB_PATTERN.matcher(dbProductVersion);
		if (matcher.matches()) {
			//5.5.5-10.2.8-MariaDB
			final String version=matcher.group("version");
			final String[] args=version.split("\\.");
			productVersionInfo.setName("MariaDB");
			int pos=0;
			productVersionInfo.setMajorVersion(Converters.getDefault().convertObject(CommonUtils.get(args, pos++), Integer.class));
			productVersionInfo.setMinorVersion(Converters.getDefault().convertObject(CommonUtils.get(args, pos++), Integer.class));
			productVersionInfo.setRevision(Converters.getDefault().convertObject(CommonUtils.get(args, pos++), Integer.class));
			return productVersionInfo;
		}
		final String name = getDatabaseProductName(databaseMetaData);
		productVersionInfo.setName(name);
		int majorVersion = getDatabaseMajorVersion(databaseMetaData);
		productVersionInfo.setMajorVersion(majorVersion);
		int minorVersion = getDatabaseMinorVersion(databaseMetaData);
		productVersionInfo.setMinorVersion(minorVersion);
		Pattern pattern = Pattern.compile(".*" + majorVersion + "\\.0*"
				+ minorVersion + "\\.([0-9]+).*");
		matcher = pattern.matcher(dbProductVersion);
		Integer revision = null;
		if (matcher.matches()) {
			revision = Integer.valueOf(matcher.group(1));
			productVersionInfo.setRevision(revision);
		}
		return productVersionInfo;
	}

	/**
	 * 製品バージョン情報を取得します
	 * 
	 * @param connection
	 */
	public static ProductVersionInfo getProductVersionInfo(
			final Connection connection) {
		return getProductVersionInfo(getDatabaseMetaData(connection));
	}

	/**
	 * DBプロダクト名の取得
	 * 
	 * @param connection
	 */
	public static String getDatabaseProductName(Connection connection) {
		return getDatabaseProductName(getDatabaseMetaData(connection));
	}

	/**
	 * DBのメジャーバージョン取得
	 * 
	 * @param databaseMetaData
	 */
	public static int getDatabaseMajorVersion(DatabaseMetaData databaseMetaData) {
		int result = 0;
		try {
			result = databaseMetaData.getDatabaseMajorVersion();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return result;
	}

	/**
	 * DBのメジャーバージョン取得
	 * 
	 * @param connection
	 */
	public static int getDatabaseMajorVersion(Connection connection) {
		return getDatabaseMajorVersion(getDatabaseMetaData(connection));
	}

	/**
	 * DBのマイナーバージョン取得
	 * 
	 * @param databaseMetaData
	 */
	public static int getDatabaseMinorVersion(DatabaseMetaData databaseMetaData) {
		int result = 0;
		try {
			result = databaseMetaData.getDatabaseMinorVersion();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return result;
	}

	/**
	 * DBのマイナーバージョン取得
	 * 
	 * @param connection
	 */
	public static int getDatabaseMinorVersion(Connection connection) {
		return getDatabaseMinorVersion(getDatabaseMetaData(connection));
	}

	/**
	 * テーブル名リストの取得
	 * 
	 * @param databaseMetaData
	 * @param catalog
	 *            DBカタログ
	 * @param schemaPattern
	 *            スキーマ名パターン
	 * @param tableNamePattern
	 *            テーブル名パターン
	 */
	public static List<String> getTableNames(DatabaseMetaData databaseMetaData,
			String catalog, String schemaPattern, String tableNamePattern) {
		return getTableNames(databaseMetaData, catalog, schemaPattern,
				tableNamePattern, "TABLE");
	}

	/**
	 * テーブル名リストの取得
	 * 
	 * @param databaseMetaData
	 * @param catalog
	 *            DBカタログ
	 * @param schema
	 *            スキーマ名パターン
	 * @param tableNamePattern
	 *            テーブル名パターン
	 */
	public static List<String> getTableNames(DatabaseMetaData databaseMetaData,
			String catalog, String schema, String tableNamePattern,
			String... tableTypes) {
		List<String> result = new ArrayList<String>();
		ResultSet resultSet = null;
		try {
			resultSet = databaseMetaData.getTables(emptyToNull(catalog),
					emptyToNull(schema), tableNamePattern, tableTypes);
			while (resultSet.next()) {
				String tableName = resultSet.getString("TABLE_NAME");
				result.add(tableName);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(resultSet);
		}
		return result;
	}

	/**
	 * SQLの実行
	 * 
	 * @param connection
	 * @param sqlList
	 * @throws SQLException
	 */
	public static void executeSql(Connection connection,
			Collection<SqlOperation> sqlList) throws SQLException {
		Statement statement = null;
		try {
			statement = connection.createStatement();
			for (SqlOperation commandText : sqlList) {
				if (!isEmpty(commandText.getSqlText())) {
					statement.execute(commandText.getSqlText());
				}
			}
		} finally {
			close(statement);
		}
	}

	/**
	 * SQLの実行
	 * 
	 * @param connection
	 * @param sqlList
	 * @throws SQLException
	 */
	public static void executeSql(Connection connection, String... sqlList)
			throws SQLException {
		Statement statement = null;
		try {
			statement = connection.createStatement();
			for (String sql : sqlList) {
				if (!isEmpty(sql)) {
					statement.execute(sql);
				}
			}
		} finally {
			close(statement);
		}
	}

	/**
	 * ビュー名リストの取得
	 * 
	 * @param connection
	 * @param catalog
	 *            DBカタログ
	 * @param schemaPattern
	 *            スキーマ名パターン
	 * @param tableNamePattern
	 *            テーブル名パターン
	 */
	public static List<String> getViewNames(Connection connection,
			String catalog, String schemaPattern, String tableNamePattern) {
		return getTableNames(getDatabaseMetaData(connection), catalog,
				schemaPattern, tableNamePattern, "VIEW");
	}

	/**
	 * ビュー名リストの取得
	 * 
	 * @param databaseMetaData
	 * @param catalog
	 *            DBカタログ
	 * @param schemaPattern
	 *            スキーマ名パターン
	 * @param tableNamePattern
	 *            テーブル名パターン
	 */
	public static List<String> getViewNames(DatabaseMetaData databaseMetaData,
			String catalog, String schemaPattern, String tableNamePattern) {
		return getTableNames(databaseMetaData, catalog, schemaPattern,
				tableNamePattern, "VIEW");
	}

	/**
	 * テーブル名リストの取得
	 * 
	 * @param connection
	 * @param schemaPattern
	 * @param tableNamePattern
	 */
	public static List<String> getTableNames(Connection connection,
			String catalog, String schemaPattern, String tableNamePattern) {
		return getTableNames(getDatabaseMetaData(connection), catalog,
				schemaPattern, tableNamePattern);
	}

	/**
	 * Connectionのクローズ
	 * 
	 * @param connection
	 */
	public static void close(Connection connection) {
		if (connection == null) {
			return;
		}
		try {
			if (!connection.isClosed()) {
				connection.close();
			}
		} catch (SQLException e) {
		}
	}

	/**
	 * AutoCloseableのクローズ
	 * 
	 * @param autoCloseable
	 */
	public static void close(AutoCloseable autoCloseable) {
		if (autoCloseable == null) {
			return;
		}
		try {
			autoCloseable.close();
		} catch (Exception e) {
		}
	}
	
	/**
	 * Statementのクローズ
	 * 
	 * @param statement
	 */
	public static void close(Statement statement) {
		if (statement == null) {
			return;
		}
		try {
			if (!statement.isClosed()) {
				statement.close();
			}
		} catch (SQLException e) {
		}
	}

	/**
	 * Statementのクローズ
	 * 
	 * @param statement
	 */
	public static void close(PreparedStatement statement) {
		if (statement == null) {
			return;
		}
		try {
			if (!statement.isClosed()) {
				statement.close();
			}
		} catch (SQLException e) {
		}
	}
	
	/**
	 * CloseableのClose
	 * 
	 * @param closeable
	 */
	public static void close(Closeable closeable) {
		if (closeable == null) {
			return;
		}
		try {
			closeable.close();
		} catch (IOException e) {
		}
	}

	/**
	 * ResultSetのクローズ
	 * 
	 * @param resultSet
	 */
	public static void close(ResultSet resultSet) {
		if (resultSet == null) {
			return;
		}
		try {
			// if(!resultSet.isClosed()){
			resultSet.close();
			// }
		} catch (SQLException e) {
		}
	}

	/**
	 * プライマリーキー情報の設定
	 * 
	 * @param connection
	 * @param table
	 */
	public static void setPrimaryKeyInfo(Connection connection, Table table) {
		if (table.getName()==null){
			return;
		}
		List<String> keys = new FlexList<String>();
		ResultSet rs = null;
		try {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			rs = databaseMetaData.getPrimaryKeys(table.getCatalogName(),
					table.getSchemaName(), table.getName());
			String pkName = null;
			while (rs.next()) {
				// String tableName=resultSet.getString("TABLE_NAME");
				pkName = rs.getString("PK_NAME");
				String columnName = rs.getString("COLUMN_NAME");
				short keySeq = rs.getShort("KEY_SEQ");
				if (!table.getColumns().contains(columnName)) {
					break;
				}
				keys.set((keySeq - 1), columnName);
			}
			if (keys.size() > 0 && table.getPrimaryKeyConstraint() == null) {
				table.setPrimaryKey(pkName, table.getColumns().getAll(keys)
						.toArray(new Column[0]));
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
		}
	}

	/**
	 * ResultSetのメタデータをマップで取得する
	 * 
	 * @param resultset
	 * @throws SQLException
	 */
	public static List<Map<String, Object>> getResultSetMetadata(
			ResultSet resultset) throws SQLException {
		ResultSetMetaData metadata = resultset.getMetaData();
		int count = metadata.getColumnCount();
		List<Map<String, Object>> result = list();
		for (int i = 1; i <= count; i++) {
			Map<String, Object> map = caseInsensitiveLinkedMap();
			map.put(SchemaProperties.CATALOG_NAME.getLabel(), metadata.getCatalogName(i));
			map.put(SchemaProperties.SCHEMA_NAME.getLabel(), metadata.getSchemaName(i));
			map.put(SchemaProperties.COLUMN_NAME.getLabel(), metadata.getColumnName(i));
			map.put("columnType", metadata.getColumnType(i));
			map.put("columnTypeName", metadata.getColumnTypeName(i));
			map.put("precision", metadata.getPrecision(i));
			map.put("scale", metadata.getScale(i));
			map.put(SchemaProperties.TABLE_NAME.getLabel(), metadata.getTableName(i));
			map.put("columnClassName", metadata.getColumnClassName(i));
			map.put("columnDisplaySize", metadata.getColumnDisplaySize(i));
			map.put("autoIncrement", metadata.isAutoIncrement(i));
			map.put("caseSensitive", metadata.isCaseSensitive(i));
			map.put("currency", metadata.isCurrency(i));
			map.put("nullable", metadata.isNullable(i));
			map.put("readOnly", metadata.isReadOnly(i));
			map.put("searchable", metadata.isSearchable(i));
			map.put("writable", metadata.isWritable(i));
			map.put("definitelyWritable", metadata.isDefinitelyWritable(i));
			result.add(map);
		}
		return result;
	}

	/**
	 * テーブルまたはビューの列メタデータの読み込み
	 * 
	 * @param connection
	 * @param table
	 */
	public static void setColumnMetadataFromSql(Connection connection,
			Table table) {
		Statement statement = null;
		ResultSet resultSet = null;
		Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		try {
			statement = connection.createStatement();
			StringBuilder sql = new StringBuilder();
			sql.append("SELECT * FROM ");
			if (!isEmpty(table.getSchemaName())) {
				sql.append(table.getSchemaName());
				sql.append(".");
			}
			sql.append(table.getName());
			sql.append(" WHERE 0=1");
			resultSet = statement.executeQuery(sql.toString());
			setColumnMetadata(dialect, resultSet, table);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(resultSet);
			close(statement);
		}
	}

	/**
	 * ResultSetのメタデータのカラム情報の読み込み
	 * 
	 * @param resultSet
	 * @param table
	 *            データテーブル
	 */
	public static void setColumnMetadata(Dialect dialect, ResultSet resultSet,
			Table table) {
		try {
			ResultSetMetaData metaData = resultSet.getMetaData();
			int colCount = metaData.getColumnCount();
			String catalogName = null;
			String schemaName = null;
			String tableName = null;
			for (int i = 1; i <= colCount; i++) {
				if (!isEmpty(metaData.getCatalogName(i))) {
					catalogName = metaData.getCatalogName(i);
				}
				if (!isEmpty(metaData.getSchemaName(i))) {
					schemaName = metaData.getSchemaName(i);
				}
				if (!isEmpty(metaData.getTableName(i))) {
					tableName = metaData.getTableName(i);
				}
				String columnName = metaData.getColumnLabel(i);
				if (columnName == null) {
					columnName = metaData.getColumnName(i);
				}
				Column column = null;
				if (table.getColumns().contains(columnName)) {
					column = table.getColumns().get(columnName);
				} else {
					column = new Column(columnName);
					int sqlType = metaData.getColumnType(i);
					String productDataType = metaData.getColumnTypeName(i);
					long precision = metaData.getPrecision(i);
					int scale = metaData.getScale(i);
					boolean autoIncrement = metaData.isAutoIncrement(i);
					int nullable = metaData.isNullable(i);
					boolean allowDBNull = false;
					if (nullable != ResultSetMetaData.columnNullableUnknown) {
						if (nullable == ResultSetMetaData.columnNullable) {
							allowDBNull = true;
						}
					}
					dialect.setDbType(sqlType, productDataType, precision,
							scale, column);
					column.setNullable(allowDBNull);
					column.setIdentity(autoIncrement);
					table.getColumns().add(column);
				}
			}
			table.setCatalogName(catalogName);
			table.setSchemaName(schemaName);
			table.setName(tableName);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * CLOB,NCLOB,LONGVARCHAR,LONGNVARCHAR の読み込み用メソッド
	 * 
	 * @param reader
	 */
	public static String readerToString(java.io.Reader reader) {
		BufferedReader bufferedReader = new BufferedReader(reader);
		StringBuilder buf = new StringBuilder();
		try {
			String line = null;
			if ((line = bufferedReader.readLine()) != null) {
				buf.append(line);
			}
			while ((line = bufferedReader.readLine()) != null) {
				buf.append('\n');
				buf.append(line);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			close(reader);
		}
		return buf.toString();
	}

	/**
	 * 指定したSQLで文字列の値を取得する
	 * 
	 * @param connection
	 * @param sql
	 */
	public static String getStringValue(Connection connection, String sql) {
		return executeScalar(connection, sql, String.class);
	}

	/**
	 * 指定したSQLでスカラー値を取得する
	 * 
	 * @param connection
	 * @param sql
	 */
	public static <T> T executeScalar(Connection connection, String sql,
			Class<T> clazz) {
		return executeScalar(connection, sql, clazz, 1);
	}

	/**
	 * 指定したSQLでスカラー値を取得する
	 * 
	 * @param connection
	 * @param sql
	 */
	@SuppressWarnings("unchecked")
	public static <T> T executeScalar(Connection connection, String sql,
			Class<T> clazz, int columnIndex) {
		SqlExecuter sqlExec = new SqlExecuter(sql);
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			statement = sqlExec.createPreparedStatement(connection);
			rs = statement.executeQuery();
			while (rs.next()) {
				return (T) rs.getObject(columnIndex);
			}
			return null;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
			close(statement);
		}
	}

}
