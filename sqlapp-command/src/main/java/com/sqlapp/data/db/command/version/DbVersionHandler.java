/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command.version;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.version.DbVersionFileHandler.SqlFile;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.sql.ConnectionSqlExecutor;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbConcurrencyException;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DbUtils;
import com.sqlapp.util.OutputTextBuilder;

public class DbVersionHandler {

	private final Converters converters=Converters.getDefault();

	private String schemaChangeLogTableName="changelog";
	
	private String idColumnName="change_number";
	
	private String appliedByColumnName="applied_by";

	private String appliedAtColumnName="applied_at";

	private String statusColumnName="status";

	private String descriptionColumnName="description";

	private String seriesNumberColumnName="series_number";
	
	private boolean withSeriesNumber=true;

	/**
	 * @return the idColumnName
	 */
	public String getIdColumnName() {
		return idColumnName;
	}

	/**
	 * @param idColumnName the idColumnName to set
	 */
	public void setIdColumnName(final String idColumnName) {
		this.idColumnName = idColumnName;
	}

	/**
	 * @return the appliedByColumnName
	 */
	public String getAppliedByColumnName() {
		return appliedByColumnName;
	}

	/**
	 * @param appliedByColumnName the appliedByColumnName to set
	 */
	public void setAppliedByColumnName(final String appliedByColumnName) {
		this.appliedByColumnName = appliedByColumnName;
	}

	/**
	 * @return the appliedAtColumnName
	 */
	public String getAppliedAtColumnName() {
		return appliedAtColumnName;
	}

	/**
	 * @param appliedAtColumnName the appliedAtColumnName to set
	 */
	public void setAppliedAtColumnName(final String appliedAtColumnName) {
		this.appliedAtColumnName = appliedAtColumnName;
	}

	/**
	 * @return the statusColumnName
	 */
	public String getStatusColumnName() {
		return statusColumnName;
	}

	/**
	 * @param statusColumnName the statusColumnName to set
	 */
	public void setStatusColumnName(final String statusColumnName) {
		this.statusColumnName = statusColumnName;
	}

	/**
	 * @return the descriptionColumnName
	 */
	public String getDescriptionColumnName() {
		return descriptionColumnName;
	}

	/**
	 * @param descriptionColumnName the descriptionColumnName to set
	 */
	public void setDescriptionColumnName(final String descriptionColumnName) {
		this.descriptionColumnName = descriptionColumnName;
	}

	/**
	 * @return the seriesNumberColumnName
	 */
	public String getSeriesNumberColumnName() {
		return seriesNumberColumnName;
	}

	/**
	 * @param seriesNumberColumnName the seriesNumberColumnName to set
	 */
	public void setSeriesNumberColumnName(final String seriesNumberColumnName) {
		this.seriesNumberColumnName = seriesNumberColumnName;
	}

	public static final String COL_VERSION_UP_SQL="SQL(up)";

	public static final String COL_VERSION_DOWN_SQL="SQL(down)";

	public static final String COL_MIGRATION="migration";
	
	/**
	 * 名前を指定してバージョン管理テーブルを取得します。
	 * @param name 名前
	 * @return バージョン管理テーブル定義
	 */
	public Table createVersionTableDefinition(final String name){
		final String fullName=name!=null?name:getSchemaChangeLogTableName();
		final Table table=new Table();
		final String[] args=fullName.split("\\.");
		int pos=args.length-1;
		table.setName(args[pos--]);
		if (pos>=0){
			table.setSchemaName(args[pos--]);
		}
		Column pkColumn;
		Column column=new Column(getIdColumnName()).setDataType(DataType.BIGINT);
		pkColumn=column;
		table.getColumns().add(column);
		column=new Column(this.getAppliedByColumnName()).setDataType(DataType.NVARCHAR).setLength(255);
		table.getColumns().add(column);
		column=new Column(this.getAppliedAtColumnName()).setDataType(DataType.DATETIME);
		table.getColumns().add(column);
		column=new Column(this.getStatusColumnName()).setDataType(DataType.NVARCHAR).setLength(31);
		table.getColumns().add(column);
		column=new Column(this.getDescriptionColumnName()).setDataType(DataType.NVARCHAR).setLength(1023);
		table.getColumns().add(column);
		if (isWithSeriesNumber()){
			column=new Column(this.getSeriesNumberColumnName()).setDataType(DataType.BIGINT);
			table.getColumns().add(column);
		}
		table.getConstraints().addPrimaryKeyConstraint(name+"_PK", pkColumn);
		return table;
	}
	
	public void mergeSqlFiles(final List<SqlFile> sqlFiles, final Table table){
		Column column=new Column(COL_VERSION_UP_SQL).setDataType(DataType.INT);
		table.getColumns().add(column);
		column=new Column(COL_VERSION_DOWN_SQL).setDataType(DataType.INT);
		table.getColumns().add(column);
		column=new Column(COL_MIGRATION).setDataType(DataType.NVARCHAR).setLength(20);
		table.getColumns().add(column);
		for(final SqlFile sqlFile:sqlFiles){
			final Long versioNo=sqlFile.getVersionNumber();
			Row current=null;
			Long rowId=null;
			boolean match=false;
			int i=0;
			for(i=0;i<table.getRows().size();i++){
				current=table.getRows().get(i);
				rowId=getId(current);
				if (compare(rowId, versioNo)==0){
					current.put(COL_VERSION_UP_SQL, size(sqlFile.getUpSqls()));
					current.put(COL_VERSION_DOWN_SQL, size(sqlFile.getDownSqls()));
					match=true;
					break;
				}else if (compare(rowId, versioNo)>0){
					break;
				}
			}
			if (rowId==null||!match){
				final Row newRow=table.newRow();
				newRow.put(getIdColumnName(), versioNo);
				newRow.put(this.getDescriptionColumnName(),getFileName(sqlFile.getUpSqlFile()));
				newRow.put(COL_VERSION_UP_SQL, size(sqlFile.getUpSqls()));
				newRow.put(COL_VERSION_DOWN_SQL, size(sqlFile.getDownSqls()));
				if (compare(rowId, versioNo)>=0){
					table.getRows().add(i, newRow);
				} else{
					table.getRows().add(newRow);
				}
			}
		}
	}
	
	private Integer size(final Collection<?> c){
		if (c==null){
			return null;
		}
		return c.size();
	}
	
	protected Long getId(final Row row){
		if (row==null){
			return null;
		}
		return converters.convertObject(row.get(getIdColumnName()), Long.class);
	}

	protected Date getAppliedAt(final Row row){
		if (row==null){
			return null;
		}
		return converters.convertObject(row.get(this.getAppliedAtColumnName()), Date.class);
	}
	
	protected Status getStatus(final Row row){
		if (row==null){
			return null;
		}
		return Status.parse(row.get(this.getStatusColumnName()));
	}

	protected Long getSeriesNumber(final Row row){
		if (row==null){
			return null;
		}
		return converters.convertObject(row.get(this.getSeriesNumberColumnName()), Long.class);
	}

	protected String getDescription(final Row row){
		if (row==null){
			return null;
		}
		return converters.convertObject(row.get(this.getDescriptionColumnName()), String.class);
	}

	public void append(final Table table, final OutputTextBuilder builder){
		final Long lastApplied=getLastApplied(table);
		final Long[] current=new Long[1];
		builder.append(table, (column, row)->{
			final Object object=row.get(column);
			if (object==null){
				if (this.getStatusColumnName().equalsIgnoreCase(column.getName())){
					final Long val=getId(row);
					if (current[0]==null){
						current[0]=val;
					}
					if (val==null){
						return INITIAL_APPLIED_AT_TEXT;
					}else if (compare(current[0],lastApplied)>=0){
						return Status.Pending.toString();
					} else{
						current[0]=val;
						return NOT_APPLIED_APPLIED_AT_TEXT;
					}
				}
			}
			return object;
		});
	}

	private int compare(final Long val1, final Long val2){
		if (val1==null){
			if (val2==null){
				return 0;
			}
			return -1;
		} else{
			if (val2==null){
				return 1;
			}
			return val1.compareTo(val2);
		}
	}
	
	private String getFileName(final File file){
		if (file==null){
			return null;
		}
		final String path=file.getAbsolutePath();
		final String[] paths=path.split("[/\\\\]");
		String val= CommonUtils.last(paths);
		int index=val.indexOf("_");
		if (index>=0){
			val=val.substring(index+1);
		}
		index=val.lastIndexOf('.');
		if (index>=0){
			val=val.substring(0, index);
		}
		return val;
	}

	/**
	 * デフォルトの名前でバージョン管理テーブル定義を作成します。
	 * @return バージョン管理テーブル定義
	 */
	public Table createVersionTableDefinition(){
		return createVersionTableDefinition(getSchemaChangeLogTableName());
	}

	public Long getLastApplied(final Table table){
		if (table.getRows().isEmpty()){
			return null;
		}
		Long ret=Long.MIN_VALUE;
		for(final Row row:table.getRows()){
			final Long obj=getId(row);
			final Status status=getStatus(row);
			if (status.isPending()){
				continue;
			}
			if (status.isStarted()){
				continue;
			}
			if (compare(obj, ret)>0){
				ret=obj;
			}
		}
		return ret;
	}

	private static final String CURRENT_VERSION_TEXT="<= current";
	private static final String VERSION_UP_TEXT     =" ↓";
	private static final String VERSION_DOWN_TEXT   =" ↑";
	private static final String VERSION_TARGET      ="<= target";
	private static final String VERSION_CURRENT_TARGET      ="<= (current=target)";
	

	private static final String INITIAL_APPLIED_AT_TEXT="Initial";
	private static final String NOT_APPLIED_APPLIED_AT_TEXT="Not applied";
	
	public Row markCurrentVersion(final Table table){
		final int size=table.getRows().size();
		Row row=null;
		for(int i=size-1;i>=0;i--){
			row=table.getRows().get(i);
			final Status status=getStatus(row);
			if (status.isCompleted()||status.isErrord()||status.isStarted()){
				row.put(COL_MIGRATION, CURRENT_VERSION_TEXT);
				return row;
			}
		}
		row.put(COL_MIGRATION, CURRENT_VERSION_TEXT);
		return row;
	}

	public List<Row> getRowsForVersionUp(final Table table, Long version){
		if (table.getRows().isEmpty()){
			return Collections.emptyList();
		}
		if (version==null){
			final Long last=getLastVersionForApply(table);
			if (last!=null){
				version=last;
			} else{
				version=Long.MAX_VALUE;
			}
		}
		final List<Row> result=CommonUtils.list();
		final Row current=markCurrentVersion(table);
		Row lastRow=null;
		if (current!=null){
			lastRow=current;
		}
		boolean findCurrent=false;
		for(final Row row:table.getRows()){
			final Long id=getId(row);
			final Status status=getStatus(row);
			checkError(id, status);
			if (row==current){
				findCurrent=true;
			}
			if (compare(version, id)>=0){
				if (id!=null&&compare(id, getId(lastRow))>0){
					lastRow=row;
				}
			}
			if (status.isCompleted()){
				continue;
			}
			if (compare(version, id)>=0){
				if (current!=row&&findCurrent){
					row.put(COL_MIGRATION, VERSION_UP_TEXT);
				}
				result.add(row);
			} else{
				break;
			}
		}
		setTargetVersion(current, lastRow);
		return result;
	}
	
	private void setTargetVersion(final Row current, final Row row){
		if (row!=null){
			if (current==row){
				row.put(COL_MIGRATION, VERSION_CURRENT_TARGET);
				clearMigration(row.getTable());
			} else{
				final String val=(String)row.get(COL_MIGRATION);
				if (CURRENT_VERSION_TEXT.equals(val)){
					row.put(COL_MIGRATION, VERSION_CURRENT_TARGET);
					clearMigration(row.getTable());
				} else{
					setTargetVersion(row);
				}
			}
		} else{
			if (current!=null){
				current.put(COL_MIGRATION, VERSION_CURRENT_TARGET);
				clearMigration(current.getTable());
			}
		}
	}

	private void setTargetVersion(final Row row){
		row.put(COL_MIGRATION, VERSION_TARGET);
	}
	
	private void clearMigration(final Table table){
		for(final Row row:table.getRows()){
			final String val=(String)row.get(COL_MIGRATION);
			if (VERSION_UP_TEXT.equals(val)||VERSION_DOWN_TEXT.equals(val)){
				row.put(COL_MIGRATION, null);
			}
		}
	}

	private Long getLastVersionForApply(final Table table){
		if (table.getRows().isEmpty()){
			return null;
		}
		final int size=table.getRows().size();
		for(int i=size-1;i>=0;i--){
			final Row row=table.getRows().get(i);
			final Long id=getId(row);
			final Status status=getStatus(row);
			if (status.isPending()){
				continue;
			}
			checkError(id, status);
			return id;
		}
		return null;
	}

	private Long getPreviousVersion(final Table table){
		if (table.getRows().isEmpty()){
			return null;
		}
		final int size=table.getRows().size();
		int cnt=0;
		for(int i=size-1;i>=0;i--){
			final Row row=table.getRows().get(i);
			final Long id=getId(row);
			final Status status=getStatus(row);
			if (status.isPending()){
				continue;
			}
			checkError(id, status);
			if (id!=null){
				if (cnt>0){
					return id;
				}
				cnt++;
			}
		}
		return null;
	}

	/**
	 * 最後に適用されたバージョンを返します。
	 * @param table バージョン管理テーブル
	 * @param version バージョン
	 * @return 最後に適用されたバージョン
	 */
	public List<Row> getRowsForVersionDown(final Table table, Long version){
		if (table.getRows().isEmpty()){
			return Collections.emptyList();
		}
		if (version==null){
			version=getPreviousVersion(table);
		}
		final List<Row> result=CommonUtils.list();
		final int size=table.getRows().size();
		Row targetRow=null;
		Row previoudRow=CommonUtils.first(table.getRows());
		boolean find=false;
		for(int i=1;i<table.getRows().size();i++){
			targetRow=table.getRows().get(i);
			final Long id=getId(targetRow);
			if (version==null||(compare(version, id)<0)){
				targetRow=previoudRow;
				find=true;
				break;
			}
			previoudRow=targetRow;
		}
		if (!find){
			targetRow=CommonUtils.first(table.getRows());
		}
		final Row current=markCurrentVersion(table);
		for(int i=size-1;i>=0;i--){
			final Row row=table.getRows().get(i);
			final Long id=getId(row);
			final Status status=getStatus(row);
			if (status.isPending()){
				continue;
			}
			checkError(id, status);
			if (version==null||(compare(version, id)<0)){
				if (current!=row){
					row.put(COL_MIGRATION, VERSION_DOWN_TEXT);
				}
				result.add(row);
			} else{
				break;
			}
		}
		setTargetVersion(current, targetRow);
		return result;
	}

	/**
	 * Merge用のバージョンを返します。
	 * @param table バージョン管理テーブル
	 * @param sqlFiles 対象のSQL
	 * @return 未適用のバージョン
	 */
	public List<Row> getRowsForVersionMerge(final Table table, final List<SqlFile> sqlFiles){
		if (table.getRows().isEmpty()){
			return Collections.emptyList();
		}
		final List<Row> result=CommonUtils.list();
		for(final SqlFile sqlFile:sqlFiles) {
			boolean find=false;
			Row targetRow=null;
			for(int i=0;i<table.getRows().size();i++){
				targetRow=table.getRows().get(i);
				final Long id=getId(targetRow);
				if (Objects.equals(sqlFile.getVersionNumber(), id)){
					find=true;
					break;
				}
			}
			if (!find){
				final Row row=table.newRow();
				setTargetVersion(row);
				result.add(targetRow);
			} else {
				final Object obj=targetRow.get(this.getStatusColumnName());
				if (Objects.isNull(obj)) {
					result.add(targetRow);
				}
			}
		}
		return result;
	}

	/**
	 * Repair対象バージョンを返します。
	 * @param table バージョン管理テーブル
	 * @return Repair対象バージョン
	 */
	public Row getRowsForVersionRepair(final Table table){
		if (table.getRows().isEmpty()){
			return null;
		}
		final int size=table.getRows().size();
		Row targetRow=null;
		final boolean find=false;
		if (!find){
			targetRow=CommonUtils.first(table.getRows());
		}
		final Row current=markCurrentVersion(table);
		for(int i=size-1;i>=0;i--){
			final Row row=table.getRows().get(i);
			final Status status=getStatus(row);
			if (status.isCompleted()){
				return null;
			}
			if (status.isPending()){
				continue;
			}
			if (current!=row){
				row.put(COL_MIGRATION, VERSION_DOWN_TEXT);
			}
			setTargetVersion(current, targetRow);
			return row;
		}
		return null;
	}
	
	public boolean exists(final Dialect dialect, final Connection connection, final Table table, final Long id) throws SQLException{
		final boolean[] exists=new boolean[]{false};
		exists(dialect, connection, table, id, resultSet->{
			try {
				exists[0]=resultSet.next();
			} catch (final SQLException e) {
				throw new RuntimeException(e);
			}
		});
		return exists[0];
	}

	public void exists(final Dialect dialect, final Connection connection, final Table table, final Long id, final Consumer<ExResultSet> cons) throws SQLException{
		final List<SqlOperation> sqlOperations=dialect.getSqlFactoryRegistry().createSql(table, SqlType.SELECT_BY_PK);
		final SqlOperation sqlOperation=sqlOperations.get(0);
		final String sql=sqlOperation.getSqlText();
		final int transactionIsolation=connection.getTransactionIsolation();
		try{
			connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
			final ParametersContext context=new ParametersContext();
			try(Statement statement=connection.createStatement();){
				final SqlConverter sqlConverter=new SqlConverter();
				final SqlNode sqlNode=sqlConverter.parseSql(context, sql);
				context.put(this.getIdColumnName(), id);
				final JdbcHandler jdbcHandler=new JdbcHandler(sqlNode){
					@Override
					protected void handleResultSet(final ExResultSet resultSet) throws SQLException {
						cons.accept(resultSet);
					}
				};
				jdbcHandler.execute(connection, context);
			}
		} finally{
			connection.setTransactionIsolation(transactionIsolation);
		}
	}
	
	/**
	 * 一括適用されたバージョンを返します。
	 * @param table バージョン管理テーブル
	 * @return 一括適用されたバージョン
	 */
	public List<Row> getRowsForVersionDownSeries(final Table table){
		if (table.getRows().isEmpty()){
			return Collections.emptyList();
		}
		final List<Row> result=CommonUtils.list();
		final Row current=markCurrentVersion(table);
		Row targetRow=null;
		final Long seriesNumber=getSeriesNumber(current);
		final int size=table.getRows().size();
		for(int i=size-1;i>=0;i--){
			final Row row=table.getRows().get(i);
			final Long id=getId(row);
			final Status status=getStatus(row);
			final Long currentSeriesNumber=getSeriesNumber(row);
			if (status.isPending()){
				continue;
			}
			checkError(id, status);
			if (currentSeriesNumber==null){
				continue;
			}
			if (CommonUtils.eq(currentSeriesNumber, seriesNumber)){
				if (current!=row){
					row.put(COL_MIGRATION, VERSION_DOWN_TEXT);
				}
				result.add(row);
			} else{
				targetRow=row;
				break;
			}
		}
		setTargetVersion(current, targetRow);
		return result;
	}
	
	private void checkError(final Long id, final Status status){
		if (status.isStarted()){
			throw new DbConcurrencyException();
		}
		if (status.isErrord()){
			throw new SchemaVersionFailureException(id);
		}
	}

	protected Table getTable(final Connection connection, final Dialect dialect, final Table table) throws SQLException{
		final TableReader tableReader=dialect.getCatalogReader().getSchemaReader().getTableReader();
		tableReader.setSchemaName(table.getSchemaName());
		tableReader.setObjectName(table.getName());
		final List<Table> tables=tableReader.getAllFull(connection);
		return tables.isEmpty()?null:tables.get(0);
	}
	
	public boolean createTable(final Connection connection, final Dialect dialect, final Table table) throws SQLException{
		return operateTable(connection, dialect, table, SqlType.CREATE);
	}
	
	public boolean dropTable(final Connection connection, final Dialect dialect, final Table table) throws SQLException{
		return operateTable(connection, dialect, table, SqlType.DROP);
	}

	private boolean operateTable(final Connection connection, final Dialect dialect, final Table table, final SqlType sqlType) throws SQLException{
		final SqlFactory<Table> operationFacroty=dialect.getSqlFactoryRegistry().getSqlFactory(table, sqlType);
		final List<SqlOperation> operations=operationFacroty.createSql(table);
		if (operations.isEmpty()){
			return false;
		}
		final ConnectionSqlExecutor exec=new ConnectionSqlExecutor(connection);
		exec.setAutoClose(false);
		exec.execute(operations);
		return true;
	}
	
	public void load(final Connection connection, final Dialect dialect, final Table table) throws SQLException{
		PreparedStatement stmt=null;
		ResultSet rs=null;
		table.setDialect(dialect);
		try{
			final AbstractSqlBuilder<?> builder=dialect.createSqlBuilder();
			builder.select().space()._add("*").space().from().space().name(table);
			builder.lineBreak().order().by().space().name(getIdColumnName()).space().asc();
			stmt=connection.prepareStatement(builder.toString());
			rs=stmt.executeQuery();
			table.readData(rs);
			final Row row=table.newRow();
			table.getRows().add(0, row);
		} finally{
			DbUtils.close(rs);
			DbUtils.close(stmt);
		}
	}

	public int insertVersion(final Connection connection, final Dialect dialect, final Table table, final Row row, final Long seriesNumber, final Status status) throws SQLException{
		PreparedStatement stmt=null;
		try{
			final AbstractSqlBuilder<?> builder=dialect.createSqlBuilder();
			builder.insert().into().space().name(table);
			builder.space()._add("(");
			builder.name(this.getIdColumnName());
			builder.comma().name(this.getAppliedByColumnName());
			builder.comma().name(this.getAppliedAtColumnName());
			builder.comma().name(this.getStatusColumnName());
			builder.comma().name(this.getDescriptionColumnName());
			if (isWithSeriesNumber()){
				builder.comma().name(this.getSeriesNumberColumnName());
			}
			builder._add(")").values();
			builder.space()._add("(");
			if (isWithSeriesNumber()){
				builder._add("?,?,?,?,?,?");
			} else{
				builder._add("?,?,?,?,?");
			}
			builder._add(")");
			stmt=connection.prepareStatement(builder.toString());
			int i=1;
			stmt.setLong(i++, this.getId(row));
			stmt.setString(i++, connection.getMetaData().getUserName());
			stmt.setTimestamp(i++, new Timestamp(System.currentTimeMillis()));
			stmt.setString(i++, status.toString());
			stmt.setString(i++, this.getDescription(row));
			if (isWithSeriesNumber()){
				stmt.setLong(i++, seriesNumber);
			}
			return stmt.executeUpdate();
		} finally{
			DbUtils.close(stmt);
		}
	}
	
	public int updateVersion(final Connection connection, final Dialect dialect, final Table table, final Row row, final Long id, final Status from, final Status to) throws SQLException{
		PreparedStatement stmt=null;
		try{
			final AbstractSqlBuilder<?> builder=dialect.createSqlBuilder();
			builder.update().name(table);
			builder.set();
			builder.space().name(this.getStatusColumnName()).eq()._add("?");
			builder.lineBreak();
			builder.where().name(this.getIdColumnName()).eq()._add("?");
			builder.lineBreak();
			builder.and().name(this.getStatusColumnName()).eq()._add("?");
			stmt=connection.prepareStatement(builder.toString());
			int i=1;
			stmt.setString(i++, to.toString());
			stmt.setLong(i++, id);
			stmt.setString(i++, from.toString());
			return stmt.executeUpdate();
		} finally{
			DbUtils.close(stmt);
		}
	}
	
	public int deleteVersion(final Connection connection, final Dialect dialect, final Table table, final Row row) throws SQLException{
		return deleteVersion(connection, dialect, table, this.getId(row));
	}

	public int deleteVersion(final Connection connection, final Dialect dialect, final Table table, final long id) throws SQLException{
		PreparedStatement stmt=null;
		try{
			final AbstractSqlBuilder<?> builder=dialect.createSqlBuilder();
			builder.delete().from().space().name(table);
			builder.where().space().name(this.getIdColumnName()).eq()._add("?");
			stmt=connection.prepareStatement(builder.toString());
			int i=1;
			stmt.setLong(i++, id);
			return stmt.executeUpdate();
		} finally{
			DbUtils.close(stmt);
		}
	}

	/**
	 * @return the schemaChangeLogTableName
	 */
	public String getSchemaChangeLogTableName() {
		return schemaChangeLogTableName;
	}

	/**
	 * @param schemaChangeLogTableName the schemaChangeLogTableName to set
	 */
	public void setSchemaChangeLogTableName(final String schemaChangeLogTableName) {
		this.schemaChangeLogTableName = schemaChangeLogTableName;
	}

	/**
	 * @return the withSeriesNumber
	 */
	public boolean isWithSeriesNumber() {
		return withSeriesNumber;
	}

	/**
	 * @param withSeriesNumber the withSeriesNumber to set
	 */
	public void setWithSeriesNumber(final boolean withSeriesNumber) {
		this.withSeriesNumber = withSeriesNumber;
	}

}
