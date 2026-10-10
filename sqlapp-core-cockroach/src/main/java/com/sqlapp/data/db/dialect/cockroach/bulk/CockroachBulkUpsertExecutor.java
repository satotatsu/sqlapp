/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.bulk;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.bulk.*;

/** Streaming ON CONFLICT batches; no temporary DDL inside a Cockroach transaction. */
public class CockroachBulkUpsertExecutor implements BulkUpsertExecutor {
	private final Dialect dialect;
	public CockroachBulkUpsertExecutor(Dialect dialect) { this.dialect=Objects.requireNonNull(dialect); }
	@Override public long execute(Connection c,Table table,BulkUpsertOption options) throws SQLException {
		var effective=options==null?BulkUpsertOption.defaults():options;
		if(effective.getStagingTableName()!=null && !effective.getStagingTableName().isEmpty())
			throw new IllegalArgumentException("CockroachDB bulk upsert does not use a stagingTableName");
		var plan=BulkUpsertPlan.resolve(table,effective);
		var columns=new ArrayList<Column>();
		if(effective.isInsertWhenNotMatched()) columns.addAll(plan.getStagingColumns());
		else { columns.addAll(plan.getUpdateColumns()); columns.addAll(plan.getKeyColumns()); }
		String target=dialect.getObjectFullName(table.getCatalogName(),table.getSchemaName(),table.getName());
		String sql;
		if(effective.isInsertWhenNotMatched()) {
			sql="INSERT INTO "+target+" ("+names(columns)+") VALUES ("+String.join(",",Collections.nCopies(columns.size(),"?"))+") ON CONFLICT ("+names(plan.getKeyColumns())+")";
			if(effective.isUpdateWhenMatched() && !plan.getUpdateColumns().isEmpty())
				sql+=" DO UPDATE SET "+plan.getUpdateColumns().stream().map(col->dialect.quote(col.getName())+"=EXCLUDED."+dialect.quote(col.getName())).collect(Collectors.joining(","));
			else sql+=" DO NOTHING";
		} else {
			sql="UPDATE "+target+" SET "+plan.getUpdateColumns().stream().map(col->dialect.quote(col.getName())+"=?").collect(Collectors.joining(","))
				+" WHERE "+plan.getKeyColumns().stream().map(col->dialect.quote(col.getName())+"=?").collect(Collectors.joining(" AND "));
		}
		final String statementSql=sql;
		var executor=new JdbcBatchBulkInsertExecutor(dialect) {
			@Override protected List<Column> writableColumns(Table ignored,BulkOption option) { return columns; }
			@Override protected String createInsertSql(Table ignored,List<Column> selected) { return statementSql; }
			@Override protected void bind(PreparedStatement statement,Row row,List<Column> selected) throws SQLException {
				for(int i=0;i<selected.size();i++) {
					var column=selected.get(i);Object value=row.get(column);
					if(value!=null && column.getArrayDimension()>0 && value.getClass().isArray()) {
						int size=java.lang.reflect.Array.getLength(value);Object[] values=new Object[size];
						for(int j=0;j<size;j++) values[j]=java.lang.reflect.Array.get(value,j);
						String type=column.getDataTypeName();
						if(type==null) type=dialect.getDbDataTypes().getDbType(column.getDataType()).getTypeName();
						var array=c.createArrayOf(type,values);
						try {statement.setArray(i+1,array);} finally {array.free();}
					} else if(column.getDataType()==com.sqlapp.data.db.datatype.DataType.JSON || column.getDataType()==com.sqlapp.data.db.datatype.DataType.OTHER) {
						statement.setObject(i+1,value,java.sql.Types.OTHER);
					} else com.sqlapp.jdbc.sql.JdbcParameterBinder.bind(statement,dialect,null,i+1,value);
				}
			}
		};
		var rows=plan.createStagingTable(table.getName());
		try(var transaction=BulkUpsertTransaction.begin(c,effective.isUseTransaction())) {
			try {
				long affected=executor.execute(c,rows,effective.getBulkOption());
				transaction.commit(); return affected;
			} catch(SQLException | RuntimeException | Error e) { transaction.rollback(e); throw e; }
		}
	}
	private String names(List<Column> columns) { return columns.stream().map(c->dialect.quote(c.getName())).collect(Collectors.joining(",")); }
}
