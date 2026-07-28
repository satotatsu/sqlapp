/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.SQLException;
import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TemporalPeriod;
import com.sqlapp.data.schemas.TemporalPeriodType;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

public class Db2_1010TableReader extends Db2_980TableReader {

	protected Db2_1010TableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Table createTable(final ExResultSet rs) throws SQLException {
		final Table table = super.createTable(rs);
		final String comp = getString(rs, "COMPRESSION");
		final String compMode = getString(rs, "ROWCOMPMODE");
		table.setCompression("B".equalsIgnoreCase(comp) || "V".equalsIgnoreCase(comp) || "R".equalsIgnoreCase(comp));
		if ("V".equalsIgnoreCase(comp)) {
			table.setCompressionType("VALUE");
		}
		if ("A".equalsIgnoreCase(compMode)) {
			//ADAPTIVE
		} else if("S".equalsIgnoreCase(compMode)){
			//STATIC
		}
		return table;
	}

	@Override
	protected void setMetadataDetail(final Connection connection,
			final ParametersContext context, final List<Table> tableList) throws SQLException {
		super.setMetadataDetail(connection, context, tableList);
		setTemporalPeriods(connection, SchemaUtils.toDoubleKeyMap(tableList));
	}

	protected void setTemporalPeriods(final Connection connection,
			final DoubleKeyMap<String, String, Table> tables) {
		final ParametersContext context = this.defaultParametersContext(connection);
		context.put(SCHEMA_NAME, tables.keySet());
		context.put(TABLE_NAME, tables.secondKeySet());
		execute(connection, getTemporalPeriodsSqlNode(), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Table table = tables.get(getString(rs, SCHEMA_NAME), getString(rs, TABLE_NAME));
				if (table != null) {
					readTemporalPeriod(rs, table);
				}
			}
		});
	}

	protected SqlNode getTemporalPeriodsSqlNode() {
		return getSqlNodeCache().getString("periods.sql");
	}

	protected void readTemporalPeriod(final ExResultSet rs, final Table table) throws SQLException {
		final TemporalPeriod period = new TemporalPeriod(getString(rs, "PERIOD_NAME"));
		final boolean systemTime = "S".equalsIgnoreCase(getString(rs, "PERIOD_TYPE"));
		period.setPeriodType(systemTime ? TemporalPeriodType.SYSTEM_TIME : TemporalPeriodType.APPLICATION_TIME);
		period.setStartColumnName(getString(rs, "BEGIN_COLUMN_NAME"));
		period.setEndColumnName(getString(rs, "END_COLUMN_NAME"));
		table.getTemporalPeriods().add(period);
		if (systemTime) {
			final String historyTableName = getString(rs, "HISTORY_TABLE_NAME");
			if (CommonUtils.isEmpty(historyTableName)) {
				return;
			}
			table.toSystemVersioning()
				.setPeriodName(period.getName())
				.setHistoryTableSchemaName(getString(rs, "HISTORY_TABLE_SCHEMA_NAME"))
				.setHistoryTableName(historyTableName)
				.setEnable(true);
			for (final Column column : table.getColumns()) {
				if ("Y".equalsIgnoreCase(String.valueOf(column.getSpecifics().get("TRANSACTIONSTARTID")))) {
					table.getSystemVersioning().setTransactionIdColumnName(column.getName());
					break;
				}
			}
		}
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new Db2_1010ColumnReader(this.getDialect());
	}
}
