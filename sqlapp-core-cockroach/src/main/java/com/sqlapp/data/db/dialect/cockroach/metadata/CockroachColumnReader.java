/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;
import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
public class CockroachColumnReader extends ColumnReader {
	public CockroachColumnReader(Dialect dialect) { super(dialect); }
	@Override protected void doGetAllAfter(Connection c,List<Column> columns) { }
	@Override protected List<Column> doGetAll(Connection c,ParametersContext context,ProductVersionInfo version) {
		var result=new ArrayList<Column>();
		String query="SELECT table_schema,table_name,column_name,data_type,udt_schema,udt_name,character_maximum_length,numeric_precision,numeric_scale,datetime_precision,is_nullable,column_default,is_generated,generation_expression,is_hidden FROM information_schema.columns WHERE table_catalog=current_database() ORDER BY table_schema,table_name,ordinal_position";
		try(var sql=c.createStatement(); var rows=sql.executeQuery(query)) {
			while(rows.next()) {
				String schema=rows.getString(1),table=rows.getString(2),name=rows.getString(3);
				if(!CockroachMetadata.userSchema(schema) || !CockroachMetadata.matches(context,"schemaName",schema) || !CockroachMetadata.matches(context,"tableName",table) || !CockroachMetadata.matches(context,"columnName",name)) continue;
				var column=new Column(name).setSchemaName(schema).setTableName(table).setDialect(getDialect());
				String type=rows.getString(6); boolean array="ARRAY".equals(rows.getString(4));
				if(array && type.startsWith("_")) type=type.substring(1);
				Long precision=(Long)rows.getObject(7,Long.class);
				if(precision==null) precision=rows.getObject(8,Long.class);
				if(precision==null) precision=rows.getObject(10,Long.class);
				getDialect().setDbType(type,precision,(rows.getObject(9)==null ? null : ((Number)rows.getObject(9)).intValue()),column);
				if ("USER-DEFINED".equals(rows.getString(4))) column.setDataTypeName(CockroachMetadata.fullName(rows.getString(5),type));
				column.setHidden("YES".equals(rows.getString(15)));
				column.setArrayDimension(array?1:0).setNullable("YES".equals(rows.getString(11)));
				if("ALWAYS".equals(rows.getString(13))) column.setFormula(rows.getString(14)).setFormulaPersisted(true);
				else column.setDefaultValue(rows.getString(12));
				result.add(column);
			}
		} catch(SQLException e) { throw new IllegalStateException("Cannot read CockroachDB columns",e); }
		return result;
	}
}
