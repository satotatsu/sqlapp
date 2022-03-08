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

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ProcedureReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SavepointLevel;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * HSQLのProcedure作成クラス
 * 
 * @author satoh
 * 
 */
public class HsqlProcedureReader extends ProcedureReader {

	protected HsqlProcedureReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Procedure> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Procedure> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Procedure obj = createProcedure(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("procedures.sql");
	}

	protected Procedure createProcedure(ExResultSet rs) throws SQLException {
		Procedure obj = new Procedure(getString(rs, ROUTINE_NAME));
		HsqlUtils.setRoutineInfo(rs, obj);
		Boolean bool = toBoolean(getString(rs, "NEW_SAVEPOINT_LEVEL"));
		if (bool != null) {
			if (bool.booleanValue()) {
				obj.setSavepointLevel(SavepointLevel.NewSavePointLevel);
			} else {
				obj.setSavepointLevel(SavepointLevel.OldSavePointLevel);
			}
		}
		String routine_definition = HsqlUtils.normalizeStatement(obj, getString(rs, "ROUTINE_DEFINITION"));
		if (this.getReaderOptions().isReadDefinition()){
			obj.setDefinition(routine_definition);
		}
		if (this.getReaderOptions().isReadStatement()){
			Pattern pattern = Pattern.compile("CREATE\\s*PROCEDURE.*"
					+ obj.getSavepointLevel().getSqlValue()
					+ "\\s*(DYNAMIC\\s+RESULT\\s+SETS\\s+[0-9]+){0,1}\\s*(.*)",
					Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(routine_definition);
			if (matcher.matches()) {
				String dynamic = matcher.group(1);
				obj.setMaxDynamicResultSets(getDynamicResultSet(dynamic));
				String statement = HsqlUtils.formatStatement(matcher.group(2));
				if ("SQL".equals(obj.getLanguage())) {
					obj.setStatement(statement);
				}
			} else {
				throw new RuntimeException(routine_definition);
			}
		}
		return obj;
	}

	private static final Pattern DYNAMIC_RESULTSET = Pattern
			.compile("DYNAMIC\\s+RESULT\\s+SETS\\s+([0-9]+)");

	private Integer getDynamicResultSet(String dynamic) {
		if (dynamic == null) {
			return null;
		}
		Matcher matcher = DYNAMIC_RESULTSET.matcher(dynamic);
		if (matcher.matches()) {
			return Converters.getDefault().convertObject(matcher.group(1),
					Integer.class);
		}
		return null;
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return new HsqlProcedureArgumentReader(this.getDialect());
	}
}
