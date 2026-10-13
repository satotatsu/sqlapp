/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.parameter.ParametersContext;

/** Set-based access to explicitly named EPAS compatibility views. */
final class EdbOracleCompatibilityCatalog {
	interface Row<T> { T read(ResultSet rows) throws SQLException; }
	private EdbOracleCompatibilityCatalog() { }
	static <T> List<T> read(Connection connection,String view,String columns,String predicate,
			ParametersContext context,Map<String,String> filters,Row<T> mapper) {
		try {
			String namespace=null;
			try(var sql=connection.prepareStatement("SELECT n.nspname FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE c.relname=? AND c.relkind IN ('v','m') AND n.nspname IN ('sys','pg_catalog')")) {
				sql.setString(1,view);
				try(var rows=sql.executeQuery()) {
					if(rows.next()) namespace=rows.getString(1);
					if(rows.next()) throw new SQLException("Ambiguous EPAS compatibility catalog: "+view);
				}
			}
			if(namespace==null) return List.of();
			if(!Set.of("sys","pg_catalog").contains(namespace)) throw new SQLException("Unexpected EPAS catalog namespace");
			var query=new StringBuilder("SELECT "+columns+" FROM \""+namespace+"\"."+view+" WHERE "+predicate);
			var values=new ArrayList<Object>();
			for(var filter:filters.entrySet()) {
				Object value=context.get(filter.getKey());
				if(value==null) continue;
				Collection<?> items=value instanceof Collection<?> c ? c : value instanceof Object[] a ? Arrays.asList(a) : List.of(value);
				if(items.isEmpty()) continue;
				query.append(" AND ").append(filter.getValue()).append(" IN (").append(String.join(",",Collections.nCopies(items.size(),"?"))).append(")");
				values.addAll(items);
			}
			var result=new ArrayList<T>();
			try(var sql=connection.prepareStatement(query.toString())) {
				for(int i=0;i<values.size();i++) sql.setObject(i+1,values.get(i));
				try(var rows=sql.executeQuery()) {while(rows.next()) result.add(mapper.read(rows));}
			}
			return result;
		} catch(SQLException e) {throw new IllegalStateException("Failed to read EPAS "+view,e);}
	}
}
