/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;
/**
 * Postgres10.0 Table Reader
 * @author satoh
 *
 */
public class Postgres100TableReader extends Postgres93TableReader{

	protected Postgres100TableReader(Dialect dialect) {
		super(dialect);
		this.setRelkind("r","p");
	}
	
	private static final Pattern LIST_PARTITION_DEF=Pattern.compile("\\s*FOR\\s+VALUES\\s+IN\\s*\\((?<highValue>.*?)\\)\\s*", Pattern.CASE_INSENSITIVE);

	private static final Pattern RANGE_PARTITION_DEF=Pattern.compile("\\s*FOR\\s+VALUES\\s+FROM\\s*\\((?<lowValue>.*?)\\)\\s+TO\\s*\\((?<highValue>.*?)\\)\\s*", Pattern.CASE_INSENSITIVE);

	private static final Pattern PARTITION_COLUMN_DEF=Pattern.compile("\\s*[^(]+\\((?<column>.*)\\)\\s*", Pattern.CASE_INSENSITIVE);

	@Override
	protected Table createTable(ExResultSet rs) throws SQLException{
		Table table=super.createTable(rs);
		String partStrategy=getString(rs, "partition_strategy");
		String partitionExpression=getString(rs, "partition_expression");
		String partitionStrategyColumn=getString(rs, "partition_strategy_column");
		if (partStrategy!=null) {
			table.toPartitioning();
			table.getPartitioning().setPartitioningType(partStrategy);
			Matcher matcher=PARTITION_COLUMN_DEF.matcher(partitionStrategyColumn);
			matcher.matches();
			String column=matcher.group("column");
			table.getPartitioning().getPartitioningColumns().add(new ReferenceColumn(column));
		}
		if (partitionExpression!=null) {
			Table parentTable=new Table(getString(rs, "parent_table_name"));
			parentTable.setSchemaName(getString(rs, "parent_schema_name"));
			Matcher matcher=RANGE_PARTITION_DEF.matcher(partitionExpression);
			if (matcher.matches()) {
				String lowValue=matcher.group("lowValue");
				String highValue=matcher.group("highValue");
				table.setPartitionParent(parentTable, lowValue, highValue);
			} else {
				matcher=LIST_PARTITION_DEF.matcher(partitionExpression);
				matcher.matches();
				String value=matcher.group("highValue");
				table.setPartitionParent(parentTable, null, value);
			}
		}
		return table;
	}
	
	@Override
	protected void addInherits(Table table, Table pTable) {
		if (table.getPartitionParent()==null) {
			table.getInherits().add(pTable);
		}
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables100.sql");
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new Postgres100ColumnReader(this.getDialect());
	}
}
