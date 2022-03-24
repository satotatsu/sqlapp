/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.data.db.metadata.MetadataReader.COLUMN_NAME;
import static com.sqlapp.data.db.metadata.MetadataReader.TABLE_NAME;
import static com.sqlapp.util.CommonUtils.deleteLineSeparator;
import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.notZero;
import static com.sqlapp.util.CommonUtils.toUpperCase;
import static com.sqlapp.util.DbUtils.close;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SqlNodeCache;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.Routine;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;
import com.sqlapp.util.SqlExecuter;

public class OracleMetadataUtils {

	private OracleMetadataUtils() {
	};

	/**
	 * パーティションカラム情報を設定します
	 * 
	 * @param connection
	 * @param node
	 *            SQL
	 * @param context
	 * @param objectType
	 *            TABLE or INDEX
	 * @param objectName
	 *            テーブル名 or インデックス名
	 * @param partitionInfo
	 *            パーティション情報
	 */
	protected static void setPartitionColumnInfo(Connection connection,
			SqlNode node, ParametersContext context, String objectType,
			String objectName, final Partitioning partitionInfo) {
		context.put("objectType", objectType);
		context.put("objectName", objectName);
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String columnName = rs.getString(COLUMN_NAME);
				partitionInfo.getPartitioningColumns().add(columnName);
			}
		});
	}

	protected static JdbcQueryHandler execute(final Connection connection,
			SqlNode node, final ParametersContext context,
			ResultSetNextHandler handler) {
		JdbcQueryHandler jdbcQueryHandler = new JdbcQueryHandler(node, handler);
		return jdbcQueryHandler.execute(connection, context);
	}

	/**
	 * サブパーティションカラム情報を設定します
	 * 
	 * @param node
	 *            SQL
	 * @param context
	 * @param objectType
	 *            TABLE or INDEX
	 * @param objectName
	 *            テーブル名 or インデックス名
	 * @param partitionInfo
	 *            パーティション情報
	 */
	protected static void setSubPartitionColumnInfo(Connection connection,
			SqlNode node, ParametersContext context, String objectType,
			String objectName, final Partitioning partitionInfo) {
		context.put("objectType", objectType);
		context.put("objectName", objectName);
		final List<String> columnNameSet = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String columnName = rs.getString(COLUMN_NAME);
				columnNameSet.add(columnName);
			}
		});
		if (columnNameSet.size() > 0) {
			partitionInfo.getSubPartitioningColumns().addAll(columnNameSet);
		}
	}

	protected static void setCommonInfo(ResultSet rs,
			AbstractSchemaObject<?> obj) throws SQLException {
		obj.setSchemaName(rs.getString("OWNER"));
		setCommonInfo(rs, (AbstractNamedObject<?>)obj);
	}

	protected static void setCommonInfo(ResultSet rs, AbstractNamedObject<?> obj)
			throws SQLException {
		obj.setCreatedAt(rs.getTimestamp("CREATED"));
		obj.setLastAlteredAt(rs.getTimestamp("LAST_DDL_TIME"));
		if (!"VALID".equalsIgnoreCase(rs.getString("STATUS"))) {
			obj.setValid(false);
		}
	}

	/**
	 * PACKAGE,PACKAGE BODY,PROCEDURE,FUNCTION,TYPE,TYPE BODYのソースの設定
	 * 
	 * @param connection
	 * @param obj
	 * @param routineType
	 */
	protected static DoubleKeyMap<String, String, List<String>> getRoutineSources(Connection connection,
			Dialect dialect, ParametersContext context,
			List<? extends AbstractSchemaObject<?>> list, String routineType) {
		Set<String> schemaNames=CommonUtils.treeSet();
		Set<String> objectNames=CommonUtils.treeSet();
		for(AbstractSchemaObject<?> obj:list){
			if (obj.getSchemaName()!=null){
				schemaNames.add(obj.getSchemaName());
			}
			if (obj.getName()!=null){
				objectNames.add(obj.getName());
			}
		}
		final DoubleKeyMap<String, String, List<String>> result=new DoubleKeyMap<String, String, List<String>>();
		if (schemaNames.isEmpty()&&objectNames.isEmpty()){
			return result;
		}
		SqlNode node = getSqlNodeCache().getString("source.sql");
		context.put("objectType", toUpperCase(routineType));
		context.put("schemaName", schemaNames);
		context.put("objectName", objectNames);
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schemaName=rs.getString("OWNER");
				String objectName=rs.getString("NAME");
				List<String> texts=result.get(schemaName, objectName);
				if (texts==null){
					texts = list();
					result.put(schemaName, objectName, texts);
				}
				String text = rs.getString("TEXT");// 本体
				// String type=rs.getString("TYPE");
				texts.add(deleteLineSeparator(text));
			}
		});
		return result;
	}

	/**
	 * FunctionのStatementを抽出します。
	 * 
	 * @param obj オブジェクト
	 * @param difinition 定義
	 */
	protected static String getFunctionStatement(AbstractSchemaObject<?> obj, List<String> difinition) {
		Pattern pattern=Pattern.compile("FUNCTION\\s+("+obj.getName()+"|\""+obj.getName()+"\")[\\s\\n]*.*?RETURN\\s+([^-/\\s\\n])+\\s+(?<statement>.*)", Pattern.MULTILINE+Pattern.CASE_INSENSITIVE+Pattern.DOTALL);
		Pattern endPattern=createEndPattern(obj);
		StringBuilder builder=new StringBuilder();
		for(String text:difinition){
			builder.append(text);
			builder.append('\n');
		}
		Matcher matcher=pattern.matcher(builder.substring(0, builder.length()-1));
		if (matcher.matches()){
			String statement=matcher.group("statement");
			Matcher endMatcher=endPattern.matcher(statement);
			if (endMatcher.matches()){
				return endMatcher.group("statement")+endMatcher.group("end");
			} else{
				return statement;
			}
		}
		return null;
	}

	private static final Pattern createEndPattern(AbstractSchemaObject<?> obj){
		Pattern endPattern=Pattern.compile("(?<statement>.*)(?<end>END)\\s+"+obj.getName()+"\\s*.*", Pattern.MULTILINE+Pattern.CASE_INSENSITIVE+Pattern.DOTALL);
		return endPattern;
	}
	
	/**
	 * ProcedureのStatementを抽出します。
	 * 
	 * @param obj オブジェクト
	 * @param difinition 定義
	 */
	protected static String getProcedureStatement(AbstractSchemaObject<?> obj, List<String> difinition) {
		Pattern pattern=Pattern.compile("PROCEDURE\\s+("+obj.getName()+"|\""+obj.getName()+"\")[\\s\\n]*.*?(?<asis>AS|IS)(?<statement>.*)", Pattern.MULTILINE+Pattern.CASE_INSENSITIVE+Pattern.DOTALL);
		Pattern endPattern=createEndPattern(obj);
		StringBuilder builder=new StringBuilder();
		for(String text:difinition){
			builder.append(text);
			builder.append('\n');
		}
		Matcher matcher=pattern.matcher(builder.substring(0, builder.length()-1));
		if (matcher.matches()){
			String asis=matcher.group("asis");
			String statement=matcher.group("statement");
			statement=asis+statement;
			Matcher endMatcher=endPattern.matcher(statement);
			if (endMatcher.matches()){
				return endMatcher.group(1)+endMatcher.group(2);
			} else{
				return statement;
			}
		}
		return null;
	}
	
	/**
	 * PackageのStatementを抽出します。
	 * 
	 * @param obj オブジェクト
	 * @param difinition 定義
	 */
	protected static String getPackageStatement(AbstractSchemaObject<?> obj, List<String> difinition) {
		Pattern pattern=Pattern.compile("PACKAGE\\s*(BODY)?\\s+("+obj.getName()+"|\""+obj.getName()+"\")[\\s\\n]*.*?(?<asis>AS|IS)(?<statement>.*)", Pattern.MULTILINE+Pattern.CASE_INSENSITIVE+Pattern.DOTALL);
		Pattern endPattern=createEndPattern(obj);
		StringBuilder builder=new StringBuilder();
		for(String text:difinition){
			builder.append(text);
			builder.append('\n');
		}
		Matcher matcher=pattern.matcher(builder.substring(0, builder.length()-1));
		if (matcher.matches()){
			String asis=matcher.group("asis");
			String statement=matcher.group("statement");
			statement=asis+statement;
			Matcher endMatcher=endPattern.matcher(statement);
			if (endMatcher.matches()){
				return endMatcher.group(1)+endMatcher.group(2);
			} else{
				return statement;
			}
		}
		return null;
	}
	
	
	public static SqlNodeCache getSqlNodeCache() {
		return SqlNodeCache.getInstance(OracleMetadataUtils.class);
	}

	/**
	 * DDLの取得
	 * 
	 * @param connection
	 * @param objectType
	 * @param schemaName
	 * @param objectName
	 * @throws SQLException
	 */
	public static String getDdl(Connection connection, String objectType,
			String schemaName, String objectName) throws SQLException {
		SqlExecuter sql = new SqlExecuter("SELECT ");
		sql.addSql("DBMS_METADATA.GET_DDL(?, ?");
		sql.addParameter(objectType);
		sql.addParameter(objectName);
		if (!isEmpty(schemaName)) {
			sql.addSql(", ?)");
			sql.addParameter(schemaName);
		} else {
			sql.addSql(")");
		}
		sql.addSqlLine(" FROM DUAL");
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			statement = sql.createPreparedStatement(connection);
			rs = statement.executeQuery();
			while (rs.next()) {
				return rs.getString(1);
			}
			return null;
		} finally {
			close(rs);
			close(statement);
		}
	}

	/**
	 * 制約の遅延の取得
	 * 
	 * @param deferrable
	 * @param deferred
	 */
	public static Deferrability getDeferrability(String deferrable,
			String deferred) {
		if ("DEFERRABLE".equalsIgnoreCase(deferrable)) {
			if ("IMMEDIATE".equalsIgnoreCase(deferred)) {
				return Deferrability.InitiallyImmediate;
			} else if ("DEFERRED".equalsIgnoreCase(deferrable)) {
				return Deferrability.InitiallyDeferred;
			}
		} else if ("NOT DEFERRABLE".equalsIgnoreCase(deferrable)) {
			return Deferrability.NotDeferrable;
		}
		return null;
	}

	public static boolean hasSelectPrivilege(Connection connection,
			Dialect dialect, String schemaName, String tableName) {
		SqlNode node = getSqlNodeCache().getString("hasPrivileges.sql");
		ParametersContext context = new ParametersContext();
		context.put("schemaName", schemaName);
		context.put("tableName", tableName);
		final List<ObjectPrivilege> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				ObjectPrivilege obj = new ObjectPrivilege();
				obj.setGrantorName(rs.getString("GRANTOR"));
				obj.setGranteeName(rs.getString("GRANTEE"));
				obj.setSchemaName(rs.getString("TABLE_SCHEMA"));
				obj.setObjectName(rs.getString(TABLE_NAME));
				obj.setPrivilege(rs.getString("PRIVILEGE"));
				obj.setGrantable("YES".equalsIgnoreCase(rs
						.getString("GRANTABLE")));
				obj.setHierachy("YES".equalsIgnoreCase(rs
						.getString("HIERARCHY")));
				result.add(obj);
			}
		});
		return result.size() > 0;
	}

	protected static void setDba(boolean dba, ParametersContext context) {
		if (dba) {
			context.put("dbaOrAll", "DBA");
		} else {
			context.put("dbaOrAll", "ALL");
		}
	}

	protected static void setDbaOrUser(boolean dba, ParametersContext context) {
		if (dba) {
			context.put("dbaOrUser", "DBA");
		} else {
			context.put("dbaOrUser", "USER");
		}
	}
	
	protected static void setNamedArgument(ResultSet rs, Routine<?> routine, NamedArgument obj)
			throws SQLException {
		long dataLevel=rs.getLong("DATA_LEVEL");
		String name=getString(rs, "ARGUMENT_NAME");
		if (name==null&&dataLevel==0){
			return;
		}
		routine.setSchemaName(getString(rs, "OWNER"));
		routine.setName(getString(rs, "OBJECT_NAME"));
		obj.setName(name);
		obj.setSchemaName(getString(rs, "OWNER"));
		obj.setDirection(ParameterDirection.parse(getString(rs, "IN_OUT")));
		String productDataType = getString(rs, "DATA_TYPE");
		String def=getString(rs, "DEFAULTED");
		if ("Y".equals(def)){
			obj.setDefaultValue("NULL");
		}
		long max_length = rs.getLong("CHAR_LENGTH");
		long precision = rs.getLong("DATA_PRECISION");
		Integer scale = getInteger(rs, "DATA_SCALE");
		String characterSetName=getString(rs, "CHARACTER_SET_NAME");
		obj.setCharacterSet(characterSetName);
		obj.getDialect().setDbType(productDataType, notZero(max_length, precision), scale, obj);
		if ("OBJECT".equals(productDataType)){
			String typeName=getString(rs, "TYPE_NAME");
			obj.setDataTypeName(typeName);
		}
		SchemaUtils.setRoutine(obj, routine);
	}

	private static String getString(ResultSet rs, String name) throws SQLException{
		return rs.getString(name);
	}

	/**
	 * ResultSetから指定したカラムのInteger値を取得します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected static Integer getInteger(ResultSet rs, String name) throws SQLException {
		Integer val = Converters.getDefault().convertObject(rs.getObject(name),
				Integer.class);
		return val;
	}
}
