/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.hsql.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.max;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionReturning;
import com.sqlapp.data.schemas.OnNullCall;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.TripleKeyMap;

/**
 * HSQLの関数読み込みクラス
 * 
 * @author satoh
 * 
 */
public class HsqlFunctionReader extends FunctionReader {

	protected HsqlFunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Function> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final TripleKeyMap<String, String, String, Function> map = new TripleKeyMap<String, String, String, Function>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Function function = createFunction(rs);
				map.put(function.getSchemaName(), function.getSchemaName(),
						function.getSpecificName(), function);
			}
		});
		return map.toList();
	}

	protected Function createFunction(ExResultSet rs) throws SQLException {
		Function obj = new Function(getString(rs, ROUTINE_NAME));
		HsqlUtils.setRoutineInfo(rs, obj);
		boolean bool = toBoolean(getString(rs, "IS_NULL_CALL"));
		if (bool) {
			obj.setOnNullCall(OnNullCall.ReturnsNullOnNullInput);
		} else {
			obj.setOnNullCall(OnNullCall.CalledOnNullInput);
		}
		setReturning(rs, obj);
		String routine_definition = HsqlUtils.normalizeStatement(obj, getString(rs, "ROUTINE_DEFINITION"));
		if (this.getReaderOptions().isReadDefinition()){
			obj.setDefinition(routine_definition);
		}
		if (this.getReaderOptions().isReadStatement()&&"SQL".equals(obj.getLanguage())){
			Pattern pattern = Pattern.compile("CREATE\\s*(.*)\\s*FUNCTION.*"
					+ obj.getOnNullCall().getSqlValue() + "\\s*(.*)",
					Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(routine_definition);
			if (matcher.matches()) {
				int count = matcher.groupCount();
				int pos = 1;
				if (count == 1) {
					obj.setStatement(matcher.group(pos++));
				} else if (count > 1) {
					obj.setFunctionType(matcher.group(pos++));
					obj.setStatement(HsqlUtils.formatStatement(matcher.group(pos++)));
				}
			} else {
				throw new RuntimeException(routine_definition);
			}
		}
		return obj;
	}

	protected void setReturning(ExResultSet rs, Function obj) throws SQLException {
		String data_type = getString(rs, "DATA_TYPE");
		String interval_type = getString(rs, "INTERVAL_TYPE");
		String domainName = getString(rs, "UDT_NAME");
		Long char_maxlength = this.getLong(rs, "CHARACTER_MAXIMUM_LENGTH");
		Long numeric_precision = this.getLong(rs, "NUMERIC_PRECISION");
		Integer numeric_scale = getInteger(rs, "NUMERIC_SCALE");
		Integer datetime_scale = getInteger(rs, "DATETIME_PRECISION");
		FunctionReturning ret = obj.getReturning();
		ret.setDataTypeName(getString(rs, "IS_NULL_CALL"));
		if (!isEmpty(domainName)) {
			ret.setDataTypeName(domainName);
			ret.setDataType(DataType.DOMAIN);
		} else if (!isEmpty(interval_type)) {
			Long interval_precision = this.getLong(rs, "INTERVAL_PRECISION");
			String productDataType = data_type + " " + interval_type;
			this.getDialect().setDbType(productDataType, interval_precision
					, CommonUtils.coalesce(numeric_scale, datetime_scale), ret);
		} else {
			this.getDialect().setDbType(data_type, max(char_maxlength, numeric_precision)
					, CommonUtils.coalesce(numeric_scale, datetime_scale), ret);
		}
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("functions.sql");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.metadata.RoutineReader#newRoutineArgumentReader()
	 */
	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new HsqlFunctionArgumentReader(this.getDialect());
	}
}
