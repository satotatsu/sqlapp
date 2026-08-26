/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertExecutor;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.util.CommonUtils;

/** Vertica bulk upsert using COPY, a local temporary table and MERGE. */
public class VirticaBulkUpsertExecutor implements BulkUpsertExecutor {
	private final Dialect dialect;
	public VirticaBulkUpsertExecutor(final Dialect d){dialect=java.util.Objects.requireNonNull(d,"dialect");}
	@Override public long execute(final Connection c,final Table table,final BulkUpsertOption options)throws SQLException{
		java.util.Objects.requireNonNull(c,"connection");java.util.Objects.requireNonNull(table,"table");
		final BulkUpsertOption o=options==null?BulkUpsertOption.defaults():options;
		if(!o.isUpdateWhenMatched()&&!o.isInsertWhenNotMatched())throw new IllegalArgumentException("At least one upsert action must be enabled");
		final List<Column> keys=keys(table,o),staged=staged(table,o,keys),updates=updates(table,o,keys,staged);
		if(o.isUpdateWhenMatched()&&updates.isEmpty()&&!o.isInsertWhenNotMatched())throw new IllegalArgumentException("No columns are available to update");
		final String stage=stageName(o),stageSql=quote(stage),target=dialect.getObjectFullName(table.getCatalogName(),table.getSchemaName(),table.getName());
		final boolean manage=o.isUseTransaction()&&c.getAutoCommit();boolean created=false;Throwable failure=null;SQLException cleanup=null;
		try{if(manage)c.setAutoCommit(false);try(var s=c.createStatement()){
			s.execute("CREATE LOCAL TEMPORARY TABLE "+stageSql+" ON COMMIT PRESERVE ROWS AS SELECT "+list(staged,null)+" FROM "+target+" WHERE 1 = 0");created=true;}
			BulkInsertResolver.resolve(dialect).execute(c,stagingTable(table,stage,staged),o.getBulkOption());
			final long affected;try(var s=c.createStatement()){affected=s.executeUpdate(merge(target,stageSql,keys,staged,updates,o));}
			if(manage)c.commit();return affected;
		}catch(SQLException|RuntimeException e){failure=e;if(manage)try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}throw e;
		}finally{if(created)try(var s=c.createStatement()){s.execute("DROP TABLE "+stageSql);}catch(SQLException e){if(failure!=null)failure.addSuppressed(e);else cleanup=e;}
			if(manage)try{c.setAutoCommit(true);}catch(SQLException e){if(failure!=null)failure.addSuppressed(e);else if(cleanup!=null)cleanup.addSuppressed(e);else throw e;}
			if(failure==null&&cleanup!=null)throw cleanup;}}
	private String merge(final String target,final String stage,final List<Column> keys,final List<Column> staged,final List<Column> updates,final BulkUpsertOption o){
		final StringBuilder s=new StringBuilder("MERGE INTO ").append(target).append(" AS target USING ").append(stage).append(" AS source ON ");
		for(int i=0;i<keys.size();i++){if(i>0)s.append(" AND ");final String n=quote(keys.get(i).getName());s.append("target.").append(n).append(" = source.").append(n);}
		if(o.isUpdateWhenMatched()&&!updates.isEmpty()){s.append(" WHEN MATCHED THEN UPDATE SET ");for(int i=0;i<updates.size();i++){if(i>0)s.append(", ");final String n=quote(updates.get(i).getName());s.append(n).append(" = source.").append(n);}}
		if(o.isInsertWhenNotMatched())s.append(" WHEN NOT MATCHED THEN INSERT (").append(list(staged,null)).append(") VALUES (").append(list(staged,"source")).append(')');return s.toString();}
	private List<Column> keys(final Table t,final BulkUpsertOption o){final List<String> n=new ArrayList<>(o.getKeyColumns());if(n.isEmpty()){
		if(t.getPrimaryKeyConstraint()==null||t.getPrimaryKeyConstraint().getColumns().isEmpty())throw new IllegalArgumentException("Bulk upsert requires keyColumns or a primary key: "+t.getName());t.getPrimaryKeyConstraint().getColumns().forEach(c->n.add(c.getName()));}
		final List<Column> r=columns(t,n,"key");if(r.stream().anyMatch(Column::isIdentity)&&!o.getBulkOption().isKeepIdentity())throw new IllegalArgumentException("An identity key requires bulkOption.keepIdentity=true");return r;}
	private List<Column> staged(final Table t,final BulkUpsertOption o,final List<Column> keys){final Set<String> kn=names(keys);final List<Column> r=new ArrayList<>();for(final Column c:t.getColumns())
		if(!c.isHidden()&&CommonUtils.isEmpty(c.getFormula())&&(!c.isIdentity()||o.getBulkOption().isKeepIdentity()||kn.contains(c.getName())))r.add(c);if(!names(r).containsAll(kn))throw new IllegalArgumentException("Every key column must be writable to the staging table");return r;}
	private List<Column> updates(final Table t,final BulkUpsertOption o,final List<Column> keys,final List<Column> staged){final Set<String> kn=names(keys),sn=names(staged);if(!o.getUpdateColumns().isEmpty()){
		final List<Column> r=columns(t,o.getUpdateColumns(),"update");for(final Column c:r)if(kn.contains(c.getName())||c.isIdentity()||!sn.contains(c.getName()))throw new IllegalArgumentException("Invalid bulk upsert update column: "+c.getName());return r;}
		final List<Column> r=new ArrayList<>();for(final Column c:staged)if(!kn.contains(c.getName())&&!c.isIdentity())r.add(c);return r;}
	private List<Column> columns(final Table t,final List<String> names,final String role){final List<Column> r=new ArrayList<>();final Set<String> u=new HashSet<>();for(final String n:names){final Column c=t.getColumns().get(n);if(c==null||!u.add(c.getName()))throw new IllegalArgumentException("Invalid bulk upsert "+role+" column: "+n);r.add(c);}return r;}
	private Table stagingTable(final Table source,final String name,final List<Column> staged){final Table t=new Table(name){private static final long serialVersionUID=1L;@Override public RowCollection getRows(){return source.getRows();}};final Set<String> in=names(staged);for(final Column c:source.getColumns()){final Column copy=c.clone().setIdentity(false);if(!in.contains(c.getName()))copy.setHidden(true);t.getColumns().add(copy);}return t;}
	private String stageName(final BulkUpsertOption o){final String n=CommonUtils.isEmpty(o.getStagingTableName())?"SQLAPP_UP_"+UUID.randomUUID().toString().replace("-","").substring(0,16):o.getStagingTableName();if(!n.matches("[A-Za-z][A-Za-z0-9_$]{0,127}"))throw new IllegalArgumentException("Invalid Vertica stagingTableName: "+n);return n;}
	private Set<String> names(final List<Column> c){final Set<String> r=new HashSet<>();c.forEach(x->r.add(x.getName()));return r;}
	private String list(final List<Column> c,final String a){final StringBuilder r=new StringBuilder();for(int i=0;i<c.size();i++){if(i>0)r.append(", ");if(a!=null)r.append(a).append('.');r.append(quote(c.get(i).getName()));}return r.toString();}
	private String quote(final String n){return dialect.quote(n);}
}
