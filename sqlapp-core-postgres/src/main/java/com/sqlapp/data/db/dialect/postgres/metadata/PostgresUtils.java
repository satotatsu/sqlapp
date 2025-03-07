/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.ltrim;
import static com.sqlapp.util.CommonUtils.notEmpty;

import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ReaderUtils;
import com.sqlapp.data.db.metadata.SqlNodeCache;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

public class PostgresUtils extends ReaderUtils {
	/**
	 * カラムのメタデータを設定します
	 * 
	 * @param rs
	 * @param dialect
	 * @param column
	 * @throws SQLException
	 */
	public static void setColumnMetadata(ResultSet rs, Dialect dialect,
			AbstractColumn<?> column) throws SQLException {
		column.setName(rs.getString("attname"));
		column.setSchemaName(rs.getString("nspname"));
		String productDataType = rs.getString("typname");
		int arrayDimension = rs.getInt("attndims");
		String intervalTypeName = rs.getString("interval_type_name");
		if (arrayDimension > 0) {
			productDataType = ltrim(productDataType, '_');
		}
		productDataType = notEmpty(intervalTypeName, productDataType);
		Long maxLength = getLong(rs, "max_length");
		Long numericPrecision = getLong(rs, "numeric_precision");
		Integer numericScale = getInteger(rs, "numeric_scale");
		Integer datetimeScale = getInteger(rs, "datetime_scale");
		Integer intervalScale = getInteger(rs, "interval_scale");
		String sequenceName = rs.getString("sequence_name");
		boolean nullable = !rs.getBoolean("attnotnull");
		boolean autoIncrement = !isEmpty(sequenceName);
		column.setNullable(nullable);
		column.setIdentity(autoIncrement);
		dialect.setDbType(productDataType, CommonUtils.notZero(maxLength, numericPrecision)
				, CommonUtils.notZero(numericScale, datetimeScale, intervalScale), column);
		if (!isEmpty(sequenceName)) {
			String[] names = sequenceName.split("[.]");
			Sequence sequence = new Sequence(names[names.length - 1]);
			if (names.length > 1) {
				sequence.setSchemaName(names[names.length - 2]);
			}
			column.setSequence(sequence);
		}
		column.setArrayDimension(arrayDimension);
		column.setDefaultValue(rs.getString("adsrc"));
		column.setRemarks(rs.getString("remarks"));
	}

	private static Map<String, WeakReference<NamedArgument>> TYPE_CACHE = new HashMap<>();

	/**
	 * IDから型情報を取得します
	 * 
	 * @param connection
	 * @param dialect
	 * @param typeId
	 * @return 型情報
	 */
	public static NamedArgument getTypeInfoById(Connection connection,
			final Dialect dialect, final String typeId) {
		WeakReference<NamedArgument> ref=TYPE_CACHE.get(typeId);
		NamedArgument arg=null;
		if (ref != null) {
			arg=ref.get();
			if (arg != null) {
				return arg;
			} else{
				synchronized(TYPE_CACHE){
					Set<String> keySet=CommonUtils.set();
					for(Map.Entry<String, WeakReference<NamedArgument>> entry:TYPE_CACHE.entrySet()){
						if (entry.getValue()==null||entry.getValue().get()==null){
							keySet.add(entry.getKey());
						}
					}
					for(String key:keySet){
						TYPE_CACHE.remove(key);
					}
				}
			}
		}
		SqlNode node = getSqlNodeCache().getString("typeById.sql");
		final ParametersContext context = new ParametersContext();
		context.put("typeId", Integer.valueOf(typeId));
		final NamedArgument obj = new NamedArgument();
		obj.setDialect(dialect);
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String typeName = rs.getString("type_name");
				obj.setDialect(dialect);
				obj.setDataTypeName(typeName);
				synchronized (TYPE_CACHE) {
					TYPE_CACHE.put(typeId, new WeakReference<>(obj));
				}
			}
		});
		if (obj.getDataTypeName() == null&&obj.getDataType()==null) {
			throw new RuntimeException("typeId=" + typeId);
		}
		return obj;
	}

	public static List<NamedArgument> getTypeInfoById(Connection connection,
			final Dialect dialect, final String... typeIds) {
		List<NamedArgument> result = list();
		for (String typeId : typeIds) {
			NamedArgument routineArgument = getTypeInfoById(connection,
					dialect, typeId);
			result.add(routineArgument);
		}
		return result;
	}

	public static List<NamedArgument> getTypeInfoById(Connection connection,
			final Dialect dialect, final String[] typeIds, String[] argNames,
			String[] argModes) {
		List<NamedArgument> result = list();
		for (int i = 0; i < typeIds.length; i++) {
			NamedArgument routineArgument = getTypeInfoById(connection,
					dialect, typeIds[i]);
			if (!isEmpty(argModes) && argModes.length > i) {
				String mode = argModes[i];
				if ("o".equalsIgnoreCase(mode)) {
					routineArgument.setDirection(ParameterDirection.Output);
				} else if ("io".equalsIgnoreCase(mode)) {
					routineArgument.setDirection(ParameterDirection.Inout);
				}
			}
			if (!isEmpty(argNames) && argNames.length > i) {
				String name = CommonUtils.unwrap(argNames[i], "\"");
				routineArgument.setName(name);
			}
			result.add(routineArgument);
		}
		return result;
	}

	public static List<NamedArgument> getTypeInfoById(Connection connection,
			final Dialect dialect, final String[] typeIds, String[] argNames,
			String[] argModes, String[] argDefaults) {
		List<NamedArgument> result = list();
		for (int i = 0; i < typeIds.length; i++) {
			NamedArgument routineArgument = getTypeInfoById(connection,
					dialect, typeIds[i]);
			if (!isEmpty(argModes) && argModes.length > i) {
				String mode = argModes[i];
				if ("o".equalsIgnoreCase(mode)) {
					routineArgument.setDirection(ParameterDirection.Output);
				} else if ("io".equalsIgnoreCase(mode)) {
					routineArgument.setDirection(ParameterDirection.Inout);
				}
			}
			if (!isEmpty(argDefaults) && argDefaults.length > i) {
				routineArgument.setDefaultValue(argDefaults[i]);
			}
			if (!isEmpty(argNames) && argNames.length > i) {
				String name = argNames[i];
				routineArgument.setName(name);
			}
			result.add(routineArgument);
		}
		return result;
	}

	
	protected static JdbcQueryHandler execute(final Connection connection,
			SqlNode node, final ParametersContext context,
			ResultSetNextHandler handler) {
		JdbcQueryHandler jdbcQueryHandler = new JdbcQueryHandler(node, handler);
		return jdbcQueryHandler.execute(connection, context);
	}

	protected static SqlNodeCache getSqlNodeCache() {
		return SqlNodeCache.getInstance(PostgresUtils.class);
	}
	
	private static final Pattern RELOPTION_PATTERN=Pattern.compile("\\s*\\{(.*)\\}\\s*", Pattern.CASE_INSENSITIVE);
	
	public static Map<String,String> parseRelOption(String value){
		if (value==null||value.length()==0){
			return Collections.emptyMap();
		}
		Map<String,String> map=CommonUtils.map();
		Matcher matcher=RELOPTION_PATTERN.matcher(value);
		if (matcher.matches()){
			value=matcher.group(1);
			String[] args=value.split("\\s*(,;)\\s*");
			for(String arg:args){
				String[] splits=arg.split("\\s*=\\s*");
				if (splits.length==2){
					map.put(splits[0], splits[1]);
				}else{
					throw new IllegalArgumentException("value="+value);
				}
			}
		}
		return Collections.emptyMap();
	}
}
