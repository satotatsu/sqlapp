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
package com.sqlapp.data.db.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.rtrim;
import static com.sqlapp.util.DbUtils.getDatabaseMetaData;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.properties.ProductProperties;
import com.sqlapp.data.schemas.properties.SpecificsProperty;
import com.sqlapp.data.schemas.properties.StatisticsProperty;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DbUtils;

public abstract class MetadataReader<T extends DbObject<?>, S> {
	protected static final Logger logger = LogManager
			.getLogger(MetadataReader.class);
	/**
	 * カタログ名
	 */
	public static final String CATALOG_NAME = "catalog_name";
	/**
	 * スキーマ名
	 */
	public static final String SCHEMA_NAME = "schema_name";
	/**
	 * オブジェクトスキーマ名
	 */
	public static final String OBJECT_SCHEMA = "object_schema";
	/**
	 * オブジェクト名
	 */
	public static final String OBJECT_NAME = "object_name";
	/**
	 * テーブルスキーマ名
	 */
	public static final String TABLE_CATALOG = "table_catalog";
	/**
	 * テーブルスキーマ名
	 */
	public static final String TABLE_SCHEMA = "table_schema";
	/**
	 * テーブル名
	 */
	public static final String TABLE_NAME = "table_name";
	/**
	 * カラム名
	 */
	public static final String COLUMN_NAME = "column_name";
	/**
	 * データ型
	 */
	public static final String DATA_TYPE = "data_type";
	/**
	 * ビュー名
	 */
	public static final String VIEW_NAME = "view_name";
	/**
	 * インデックス名
	 */
	public static final String INDEX_NAME = "index_name";
	/**
	 * 制約名
	 */
	public static final String CONSTRAINT_NAME = "constraint_name";
	/**
	 * シーケンス名
	 */
	public static final String SEQUENCE_NAME = "sequence_name";
	/**
	 * 関数名
	 */
	public static final String FUNCTION_NAME = "function_name";
	/**
	 * プロシージャ名
	 */
	public static final String PROCEDURE_NAME = "procedure_name";
	/**
	 * ルーチン名
	 */
	public static final String ROUTINE_NAME = "routine_name";
	/**
	 * SQLデータアクセス
	 */
	public static final String SQL_DATA_ACCESS = "sql_data_access";
	/**
	 * SQLセキュリティ
	 */
	public static final String SECURITY_TYPE = "security_type";
	/**
	 * ルーチンによって返される動的結果セットの最大数。
	 */
	public static final String MAX_DYNAMIC_RESULT_SETS = "max_dynamic_result_sets";
	/**
	 * ロール名
	 */
	public static final String ROLE_NAME = "role_name";
	/**
	 * SPECIFICカタログ名
	 */
	public static final String SPECIFIC_CATALOG = "specific_catalog";
	/**
	 * SPECIFICスキーマ名
	 */
	public static final String SPECIFIC_SCHEMA = "specific_schema";
	/**
	 * SPECIFIC名
	 */
	public static final String SPECIFIC_NAME = "specific_name";
	/**
	 * ドメイン名
	 */
	public static final String DOMAIN_NAME = "domain_name";
	/**
	 * PARAMETER_NAME
	 */
	public static final String PARAMETER_NAME = "parameter_name";
	/**
	 * オペレータ名
	 */
	public static final String OPERATOR_NAME = "operator_name";
	/**
	 * テーブルスペース名
	 */
	public static final String TABLESPACE_NAME = "tablespace_name";
	/**
	 * トリガー名
	 */
	public static final String TRIGGER_NAME = "trigger_name";
	/**
	 * 型名
	 */
	public static final String TYPE_NAME = "type_name";
	/**
	 * 定数名
	 */
	public static final String CONSTANT_NAME = "constant_name";
	/**
	 * シノニム名
	 */
	public static final String SYNONYM_NAME = "synonym_name";
	/**
	 * イベント名
	 */
	public static final String EVENT_NAME = "event_name";
	/**
	 * 権限授与者
	 */
	public static final String GRANTEE = "grantee";
	/**
	 * 権限付与者
	 */
	public static final String GRANTOR = "grantor";
	/**
	 * 権限タイプ
	 */
	public static final String PRIVILEGE_TYPE = "privilege_type";
	/**
	 * 権限が付与可能か？
	 */
	public static final String IS_GRANTABLE = "is_grantable";
	/**
	 * サブオブジェクトに対して権限を付与
	 */
	public static final String WITH_HIERARCHY = "with_hierarchy";
	/**
	 * コメント
	 */
	public static final String REMARKS = "remarks";
	/**
	 * CHARACTER_SET_NAME
	 */
	public static final String CHARACTER_SET_NAME = "character_set_name";
	/**
	 * COLLATION_NAME
	 */
	public static final String COLLATION_NAME = "collation_name";
	/**
	 * 親のreaderへの参照
	 */
	@SuppressWarnings("rawtypes")
	private MetadataReader parent = null;

	private final Dialect dialect;

	/**
	 * カタログ名
	 */
	private String catalogName = null;
	/**
	 * 読み込みオプション
	 */
	private ReaderOptions readerOptions;

	protected MetadataReader(final Dialect dialect) {
		this.dialect = dialect;
	}

	protected Dialect getDialect() {
		return dialect;
	}

	/**
	 * @return the parentReader
	 */
	@SuppressWarnings("unchecked")
	public <U extends MetadataReader<?, ?>> U getParent() {
		return (U) parent;
	}

	@SuppressWarnings("unchecked")
	public <U extends MetadataReader<?, ?>> U getAncestor(final Predicate<MetadataReader<?, ?>> pre){
		if (this.getParent()==null){
			return null;
		}
		MetadataReader<?, ?> current=this.getParent();
		while(true){
			if (current==null){
				return null;
			}
			if (pre.test(current)){
				return (U)current;
			}
			current=current.getParent();
		}
	}
	
	@SuppressWarnings("unchecked")
	public <U extends MetadataReader<?, ?>> U getAncestor(final Class<U> clazz){
		return (U)getAncestor(r->clazz.isInstance(r));
	}
	
	/**
	 * @param parentReader
	 *            the parentReader to set
	 */
	@SuppressWarnings("rawtypes")
	protected void setParent(final MetadataReader parent) {
		this.parent = parent;
	}

	/**
	 * @return the readerOptions
	 */
	public ReaderOptions getReaderOptions() {
		if (readerOptions == null) {
			return getParent().getReaderOptions();
		}
		return readerOptions;
	}

	/**
	 * @param readerOptions
	 *            the readerOptions to set
	 */
	public void setReaderOptions(final ReaderOptions readerOptions) {
		this.readerOptions = readerOptions;
	}

	/**
	 * 子供のReaderの初期化を行います
	 * 
	 * @param obj
	 */
	protected <U extends MetadataReader<?, ?>> void initializeChild(final U obj) {
		if (obj != this) {
			obj.setParent(this);
		}
	}

	protected SqlNodeCache getSqlNodeCache() {
		return getSqlNodeCache(this.getClass());
	}

	protected SqlNodeCache getSqlNodeCache(final Class<?> clazz) {
		return SqlNodeCache.getInstance(clazz);
	}

	/**
	 * @return the catalogName
	 */
	public String getCatalogName() {
		return catalogName;
	}

	/**
	 * @param catalogName
	 *            the catalogName to set
	 */
	public void setCatalogName(final String catalogName) {
		this.catalogName = catalogName;
	}

	/**
	 * DBメタデータのフィルター
	 */
	private ReadDbObjectPredicate readDbObjectPredicate = null;

	/**
	 * @param readDbObjectPredicate
	 *            the readDbObjectPredicate to set
	 */
	public void setReadDbObjectPredicate(
			final ReadDbObjectPredicate readDbObjectPredicate) {
		this.readDbObjectPredicate = readDbObjectPredicate;
	}

	/**
	 */
	protected ReadDbObjectPredicate getReadDbObjectPredicate() {
		if (readDbObjectPredicate != null) {
			return readDbObjectPredicate;
		}
		if (this.getParent() != null) {
			return this.getParent().getReadDbObjectPredicate();
		}
		return null;
	}

	/**
	 * 全メタデータを取得します
	 * 
	 * @param connection
	 */
	public List<T> getAllFull(final Connection connection) {
		final ParametersContext context = defaultParametersContext(connection);
		return getAllFull(connection, context);
	}

	/**
	 * 全メタデータを取得します
	 * 
	 * @param connection
	 */
	public List<T> getAllFull(final Connection connection, final ParametersContext context) {
		final List<T> result = getAll(connection, context);
		executeSetMetadataDetail(connection, context, result);
		for (final T obj : result) {
			executeSetMetadataDetail(connection, obj);
		}
		return result;
	}
	
	/**
	 * 指定したオブジェクトにデータを読み込みます
	 * 
	 * @param connection
	 * @param target
	 */
	public abstract void loadFull(Connection connection, S target);

	/**
	 * 最小限のメタデータを取得します。
	 * 
	 * @param connection
	 */
	public List<T> getAll(final Connection connection) {
		return getAll(connection, defaultParametersContext(connection));
	}

	/**
	 * 条件を指定して最小限のメタデータを取得します。
	 * 
	 * @param connection
	 * @param context
	 */
	public List<T> getAll(final Connection connection, final ParametersContext context) {
		List<T> result = null;
		try {
			final ProductVersionInfo productVersionInfo = getProductVersionInfo(connection);
			final List<T> list = doGetAll(connection, context, productVersionInfo);
			for (final T obj : list) {
				if (obj instanceof ProductProperties){
					final ProductProperties<?> props=((ProductProperties<?>)obj);
					props.setProductName(productVersionInfo.getName());
					props.setProductMajorVersion(productVersionInfo.getMajorVersion());
					props.setProductMinorVersion(productVersionInfo.getMinorVersion());
					props.setProductRevision(productVersionInfo.getRevision());
				}
				if (obj instanceof AbstractDbObject){
					((AbstractDbObject<?>)obj).setDialect(this.getDialect());
				}
				initialize(obj);
			}
			result = CommonUtils.list(list.size());
			doGetAllAfter(connection, list);
			for (final T obj : list) {
				if (filterObject(obj)) {
					result.add(obj);
				}
			}
		} catch (final Exception e) {
			handleError(e);
			result = CommonUtils.emptyList();
		}
		return result;
	}

	protected void initialize(final T obj){
	}

	protected void handleError(final Throwable t){
		logger.warn(t.getMessage(), t);
		if (t instanceof RuntimeException){
			throw (RuntimeException)t;
		} else{
			throw new RuntimeException(t);
		}
	}
	
	protected boolean filterObject(final T obj){
		if (getReadDbObjectPredicate()==null){
			return true;
		}
		return getReadDbObjectPredicate().test(obj, this);
	}

	/**
	 * 最小限のメタデータを取得します。
	 * 
	 * @param connection
	 * @param context
	 */
	protected abstract List<T> doGetAll(Connection connection,
			ParametersContext context, ProductVersionInfo productVersionInfo);

	/**
	 * doGetAll後の処理を実行します
	 * 
	 * @param connection
	 * @param list
	 */
	protected void doGetAllAfter(final Connection connection, final List<T> list) {

	}

	private void executeSetMetadataDetail(final Connection connection,
			final ParametersContext context, final List<T> obj) {
		try {
			setMetadataDetail(connection, context, obj);
		} catch (final SQLException e) {
			logger.error(e.getMessage(), e);
			this.handleError(e);
		}
	}

	/**
	 * メタデータの詳細情報を設定するためのメソッドです。子クラスでのオーバーライドを想定しています。
	 * 
	 * @param connection
	 * @param obj
	 */
	protected void setMetadataDetail(final Connection connection,
			final ParametersContext context, final List<T> obj) throws SQLException {

	}

	private void executeSetMetadataDetail(final Connection connection, final T obj) {
		try {
			setMetadataDetail(connection, obj);
		} catch (final SQLException e) {
			handleError(e);
		}
	}

	/**
	 * メタデータの詳細情報を設定するためのメソッドです。子クラスでのオーバーライドを想定しています。
	 * 
	 * @param connection
	 * @param obj
	 */
	protected void setMetadataDetail(final Connection connection, final T obj)
			throws SQLException {

	}

	/**
	 * カタログ名、スキーマ名、オブジェクト名を含むパラメタコンテキストを作成します。
	 * 
	 */
	protected abstract ParametersContext defaultParametersContext(
			Connection connection);

	/**
	 * カタログ名を含むパラメタコンテキストを作成します。
	 * 
	 * @param obj
	 */
	protected ParametersContext toParametersContext(final T obj) {
		final ParametersContext context = new ParametersContext();
		context.put(SchemaProperties.CATALOG_NAME.getLabel(), this.getCatalogName());
		return context;
	}

	/**
	 * 指定された名称をDBの規定の名称に変換するか?
	 */
	private boolean convertNativeCaseIdentifiers=false;
	
	/**
	 * @return the convertNativeCaseIdentifiers
	 */
	public boolean isConvertNativeCaseIdentifiers() {
		return convertNativeCaseIdentifiers;
	}

	/**
	 * @param convertNativeCaseIdentifiers the convertNativeCaseIdentifiers to set
	 */
	public void setConvertNativeCaseIdentifiers(final boolean convertNativeCaseIdentifiers) {
		this.convertNativeCaseIdentifiers = convertNativeCaseIdentifiers;
	}

	/**
	 * 入力された文字をDBの既定の文字に変換します。
	 * 
	 */
	protected String nativeCaseString(final Connection connection, final String value) {
		if (isEmpty(value)) {
			return value;
		}
		if (!isConvertNativeCaseIdentifiers()){
			return value;
		}
		try {
			if (isMixedCase(value)){
				return value;
			}
			final DatabaseMetaData databaseMetaData = getDatabaseMetaData(connection);
			if (!databaseMetaData.storesMixedCaseIdentifiers()) {
				if (databaseMetaData.storesLowerCaseIdentifiers()) {
					return value.toLowerCase();
				}
				if (databaseMetaData.storesUpperCaseIdentifiers()) {
					return value.toUpperCase();
				}
			}
			return value;
		} catch (final SQLException e) {
			return this.getDialect().nativeCaseString(value);
		}
	}

	private boolean isMixedCase(final String value){
		boolean upper=false;
		boolean lower=false;
		for(int i=0;i<value.length();i++){
			final char c=value.charAt(i);
			if (c>='a'&&c<='z'){
				lower=true;
			}
			if (c>='A'&&c<='Z'){
				upper=true;
			}
			if (lower&&upper){
				return true;
			}
		}
		return lower&&upper;
	}
	
	/**
	 * カタログ名とスキーマ名を含むパラメタコンテキストを作成します。
	 * 
	 * @param connection
	 *            コネクション
	 * @param catalogName
	 *            カタログ名
	 * @param schemaName
	 *            スキーマ名
	 */
	protected ParametersContext newParametersContext(final Connection connection,
			final String catalogName, final String schemaName) {
		final ParametersContext context = newParametersContext(connection,
				catalogName);
		context.put(SchemaProperties.SCHEMA_NAME.getLabel(), nativeCaseString(connection, schemaName));
		return context;
	}

	/**
	 * カタログ名を含むパラメタコンテキストを作成します。
	 * 
	 * @param connection
	 *            コネクション
	 * @param catalogName
	 *            カタログ名
	 */
	protected ParametersContext newParametersContext(final Connection connection,
			final String catalogName) {
		final ParametersContext context = newParametersContext(connection);
		context.put(SchemaProperties.CATALOG_NAME.getLabel(),
				nativeCaseString(connection, catalogName));
		return context;
	}

	/**
	 * パラメタコンテキストを作成します。
	 * 
	 * @param connection
	 *            コネクション
	 */
	protected ParametersContext newParametersContext(final Connection connection) {
		final ParametersContext context = new ParametersContext();
		return context;
	}

	/**
	 * DB固有情報の設定
	 * 
	 * @param rs
	 * @param columnName
	 * @param obj
	 * @throws SQLException
	 */
	protected void setSpecifics(final ResultSet rs, final String columnName,
			final SpecificsProperty<?> obj) throws SQLException {
		setSpecifics(rs, columnName, columnName, obj);
	}

	/**
	 * DB固有情報の設定
	 * 
	 * @param rs
	 * @param columnName
	 * @param obj
	 * @throws SQLException
	 */
	protected void setStatistics(final ResultSet rs, final String columnName,
			final StatisticsProperty<?> obj) throws SQLException {
		setStatistics(rs, columnName, columnName, obj);
	}

	/**
	 * DB固有情報を設定します
	 * 
	 * @param rs
	 * @param columnName
	 * @param obj
	 * @throws SQLException
	 */
	protected void setSpecifics(final ResultSet rs, final String columnName,
			final String key, final SpecificsProperty<?> obj) throws SQLException {
		final Object val = rs.getObject(columnName);
		if (!isEmpty(val)) {
			if (val instanceof Boolean){
				obj.getSpecifics().put(key, ((Boolean)val).toString());
			} else{
				final String text = Converters.getDefault().convertString(val,
						val.getClass());
				obj.getSpecifics().put(key, text);
			}
		}
	}

	/**
	 * DB固有情報を設定します
	 * 
	 * @param rs
	 * @param key
	 * @param obj
	 * @throws SQLException
	 */
	protected void setSpecifics(final String key, final Object value, final SpecificsProperty<?> obj) throws SQLException {
		if (!isEmpty(value)) {
			if (value instanceof Boolean){
				obj.getSpecifics().put(key, ((Boolean)value).toString());
			} else{
				final String text = Converters.getDefault().convertString(value,
						value.getClass());
				obj.getSpecifics().put(key, text);
			}
		}
	}

	
	/**
	 * DB固有情報の設定
	 * 
	 * @param rs
	 * @param columnName
	 * @param obj
	 * @throws SQLException
	 */
	protected void setStatistics(final ResultSet rs, final String columnName,
			final String key, final StatisticsProperty<?> obj) throws SQLException {
		final Object val = rs.getObject(columnName);
		if (val != null) {
			if (val instanceof Boolean){
				obj.getStatistics().put(key, ((Boolean)val).toString());
			} else{
				final String text = Converters.getDefault().convertString(val,
						val.getClass());
				obj.getStatistics().put(key, text);
			}
		}
	}
	
	/**
	 * DB固有の動的情報を設定します。
	 * 
	 * @param key
	 * @param value
	 * @param obj
	 * @param bool
	 * @throws SQLException
	 */
	protected <X> void setStatistics(final String key, final X value, final StatisticsProperty<?> obj, final boolean bool) throws SQLException {
		if (value != null) {
			if (!bool){
				return;
			}
			final String text = Converters.getDefault().convertString(value,
					value.getClass());
			obj.getStatistics().put(key, text);
		}
	}

	/**
	 * DB固有の動的情報を設定します。
	 * 
	 * @param key
	 * @param value
	 * @param obj
	 * @throws SQLException
	 */
	protected <X> void setStatistics(final String key, final X value, final StatisticsProperty<?> obj) throws SQLException {
		setStatistics(key,value, obj,true);
	}
	
	protected JdbcQueryHandler execute(final Connection connection, final SqlNode node,
			final ParametersContext context, final ResultSetNextHandler handler) {
		final ParametersContext clone=context.clone();
		clone.put("readerOptions", this.getReaderOptions());
		final long start=System.currentTimeMillis();
		final JdbcQueryHandler jdbcQueryHandler = new JdbcQueryHandler(node, handler);
		jdbcQueryHandler.setDialect(this.getDialect());
		jdbcQueryHandler.setFetchSize(1024);
		final JdbcQueryHandler ret= jdbcQueryHandler.execute(connection, clone);
		final long end=System.currentTimeMillis();
		if ((end-start)>3000){
			logger.warn("time="+(end-start)+", sql="+node);
		}
		return ret;
	}

	/**
	 * カタログ名をコンテキストから取得します
	 * 
	 * @param context
	 */
	protected String getCatalogName(final ParametersContext context) {
		return toString(context.get(SchemaProperties.CATALOG_NAME.getLabel()));
	}
	
	private String toString(final Object obj){
		Object internal=null;
		if (obj ==null){
			return null;
		}else if (obj instanceof String){
			return (String)obj;
		}else if (obj instanceof Collection){
			internal=CommonUtils.first((Collection<?>)obj);
		}else if (obj.getClass().isArray()){
			final int size=Array.getLength(obj);
			if (size>0){
				internal=Array.get(obj, 0);
			}
		}
		if (internal!=null){
			return internal.toString();
		}
		return null;
	}

	/**
	 * スキーマ名をコンテキストから取得します
	 * 
	 * @param context
	 */
	protected String getSchemaName(final ParametersContext context) {
		return toString(context.get(SchemaProperties.SCHEMA_NAME.getLabel()));
	}

	/**
	 * ResultSetから指定したカラムの文字列を取得します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected String getString(final ResultSet rs, final String name) throws SQLException {
		return rtrim(rs.getString(name));
	}

	/**
	 * ResultSetから指定したカラムの文字列を取得します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected Timestamp getTimestamp(final ResultSet rs, final String name) throws SQLException {
		return rs.getTimestamp(name);
	}
	
	/**
	 * ResultSetから指定したカラムのInteger値を取得します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected Integer getInteger(final ResultSet rs, final String name) throws SQLException {
		final int val = rs.getInt(name);
		if (rs.wasNull()) {
			return null;
		}
		return val;
	}

	/**
	 * ResultSetから指定したカラムのint値を取得します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected int getInt(final ResultSet rs, final String name) throws SQLException {
		return rs.getInt(name);
	}

	/**
	 * ResultSetから指定したカラムのLong値を取得します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected Long getLong(final ResultSet rs, final String name) throws SQLException {
		final long ret = rs.getLong(name);
		if (rs.wasNull()) {
			return null;
		}
		return ret;
	}

	/**
	 * ResultSetから指定したカラムのBoolean値を取得します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected Boolean getBoolean(final ResultSet rs, final String name) throws SQLException {
		return ReaderUtils.getBoolean(rs, name);
	}

	/**
	 * 製品バージョン情報を取得します
	 * 
	 * @param connection
	 */
	public ProductVersionInfo getProductVersionInfo(final Connection connection) {
		return DbUtils.getProductVersionInfo(getDatabaseMetaData(connection));
	}

	public Boolean toBoolean(final String value) {
		return Converters.getDefault().convertObject(value, Boolean.class);
	}

}
