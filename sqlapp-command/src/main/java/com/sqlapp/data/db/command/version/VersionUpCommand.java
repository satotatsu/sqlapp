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
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.command.AbstractSqlCommand;
import com.sqlapp.data.db.command.version.DbVersionFileHandler.SqlFile;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.data.db.sql.ConnectionSqlExecutor;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.thread.ThreadContext;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.OutputTextBuilder;

public class VersionUpCommand extends AbstractSqlCommand{

	/**
	 * バージョンアップ用SQLのディレクトリ
	 */
	private File sqlDirectory;
	/**
	 * バージョンダウン用のSQLのディレクトリ
	 */
	private File downSqlDirectory;
	/**
	 * バージョンアップ前に実行するSQLのディレクトリ
	 */
	private File setupSqlDirectory=null;
	/**
	 * バージョンアップ後に実行するSQLのディレクトリ
	 */
	private File finalizeSqlDirectory=null;
	/**Schema Change log table name*/
	private String schemaChangeLogTableName="changelog";
	
	private String idColumnName="change_number";
	
	private String statusColumnName="status";
	
	private String appliedByColumnName="applied_by";

	private String appliedAtColumnName="applied_at";

	private String descriptionColumnName="description";

	private String seriesNumberColumnName="series_number";
	/**Last change to Apply*/
	private Long lastChangeToApply=Long.MAX_VALUE;
	private boolean showVersionOnly=false;
	
	private boolean withSeriesNumber=true;

	private String previousState=null;
	
	private String lastState=null;
	
	private Table previousTable=null;
	
	private Table table=null;
	
	@Override
	protected void doRun() {
		final DbVersionHandler dbVersionHandler=createDbVersionHandler();
		final DbVersionFileHandler dbVersionFileHandler=new DbVersionFileHandler();
		dbVersionFileHandler.setUpSqlDirectory(this.getSqlDirectory());
		dbVersionFileHandler.setDownSqlDirectory(this.getDownSqlDirectory());
		Dialect dialect;
		Connection connection=null;
		try{
			connection=this.getConnection();
			dialect=this.getDialect(connection);
			dbVersionFileHandler.setSqlSplitter(dialect.createSqlSplitter());
		} catch (final RuntimeException e) {
			this.getExceptionHandler().handle(e);
		} finally {
			releaseConnection(connection);
		}
		dbVersionFileHandler.setEncoding(this.getEncoding());
		DialectTableHolder holder=logCurrentState(dbVersionHandler, dbVersionFileHandler, true);
		previousTable=holder.table;
		previousState=lastState;
		if (!isShowVersionOnly()){
			if (!holder.rows.isEmpty()){
				this.println("");
				executeChangeVersion(holder.dialect, holder.table, holder.rows, holder.sqlFiles, dbVersionHandler);
				holder=logCurrentState(dbVersionHandler, dbVersionFileHandler, false);
				table=holder.table;
			} else{
				executeEmptyVersion(holder.dialect, holder.table, holder.rows, holder.sqlFiles, dbVersionHandler);
				holder=logCurrentState(dbVersionHandler, dbVersionFileHandler, false);
				table=holder.table;
			}
		}
	}
	
	private DbVersionHandler createDbVersionHandler() {
		final DbVersionHandler dbVersionHandler=new DbVersionHandler();
		dbVersionHandler.setIdColumnName(this.getIdColumnName());
		dbVersionHandler.setAppliedAtColumnName(this.getAppliedAtColumnName());
		dbVersionHandler.setAppliedByColumnName(this.getAppliedByColumnName());
		dbVersionHandler.setStatusColumnName(this.getStatusColumnName());
		dbVersionHandler.setDescriptionColumnName(this.getDescriptionColumnName());
		dbVersionHandler.setSeriesNumberColumnName(this.getSeriesNumberColumnName());
		dbVersionHandler.setWithSeriesNumber(this.withSeriesNumber);
		return dbVersionHandler;
	}

	protected void executeEmptyVersion(final Dialect dialect, final Table table, final List<Row> rows, final List<SqlFile> sqlFiles, final DbVersionHandler dbVersionHandler){
		this.println("No sql file for apply.");
	}

	/**
	 * ディレクトリ内のSQLを取得します。
	 * @return SQL
	 */
	private List<SplitResult> read(final Dialect dialect, final File directory){
		if (directory==null){
			return Collections.emptyList();
		}
		final List<File> files=CommonUtils.list();
		final List<SplitResult> result=CommonUtils.list();
		final SqlSplitter splitter=dialect.createSqlSplitter();
		if (directory.exists()){
			final File[] children=directory.listFiles();
			if (children!=null) {
				for(final File file:children){
					if (!file.getAbsolutePath().endsWith(".sql")){
						return null;
					}
					files.add(file);
				}
			}
		}
		Collections.sort(files);
		files.forEach(file->{
			final String text=FileUtils.readText(file, getEncoding());
			final List<SplitResult> splits=splitter.parse(text);
			result.addAll(splits);
		});
		return result;
	}
	
	protected DialectTableHolder logCurrentState(final DbVersionHandler dbVersionHandler, final DbVersionFileHandler dbVersionFileHandler, final boolean target){
		final DialectTableHolder holder=new DialectTableHolder();
		holder.sqlFiles=dbVersionFileHandler.read();
		try(Connection connection=this.getConnection()){
			holder.dialect=DialectResolver.getInstance().getDialect(connection);
			holder.table=dbVersionHandler.createVersionTableDefinition(schemaChangeLogTableName);
			checkTable(connection, holder.dialect, holder.table, dbVersionHandler);
			dbVersionHandler.load(connection, holder.dialect, holder.table);
			dbVersionHandler.mergeSqlFiles(holder.sqlFiles, holder.table);
			if (target){
				holder.rows=getVersionRows(holder.table, holder.sqlFiles, dbVersionHandler);
			} else{
				dbVersionHandler.markCurrentVersion(holder.table);
			}
			lastState=outputCurrent(holder.table, dbVersionHandler);
		} catch (final SQLException e) {
			this.getExceptionHandler().handle(e);
		}
		return holder;
	}

	static class DialectTableHolder{
		public Dialect dialect=null;
		public Table table=null;
		public List<Row> rows=null;
		public List<SqlFile> sqlFiles=null;
	}
	
	protected void checkTable(final Connection connection, final Dialect dialect, final Table table, final DbVersionHandler dbVersionHandler) throws SQLException{
		final Table currentTable=dbVersionHandler.getTable(connection, dialect, table);
		if (currentTable==null){
			final boolean bool=dbVersionHandler.createTable(connection, dialect, table);
			if (bool){
				this.println("New change log table ["+getName(table)+"] was created.");
				return;
			}
		} else{
			final DefaultSchemaEqualsHandler equalsHandler=new DefaultSchemaEqualsHandler();
			equalsHandler.setValueEqualsPredicate((propertyName, eq, object1,
					object2, value1, value2)->{
						if (object1 instanceof UniqueConstraint||object2 instanceof UniqueConstraint){
							return true;
						}
						if (SchemaProperties.DATA_TYPE.getLabel().equals(propertyName)){
							return true;
						}
						if (SchemaProperties.DATA_TYPE_NAME.getLabel().equals(propertyName)){
							return true;
						}
						if (SchemaProperties.LENGTH.getLabel().equals(propertyName)){
							return true;
						}
						if (SchemaProperties.OCTET_LENGTH.getLabel().equals(propertyName)){
							return true;
						}
						if (SchemaProperties.SPECIFICS.getLabel().equals(propertyName)){
							return true;
						}
						if (SchemaProperties.STATISTICS.getLabel().equals(propertyName)){
							return true;
						}
						if (SchemaProperties.CREATED_AT.getLabel().equals(propertyName)){
							return true;
						}
						if (SchemaProperties.LAST_ALTERED_AT.getLabel().equals(propertyName)){
							return true;
						}
						if (SchemaProperties.COLLATION.getLabel().equals(propertyName)){
							return true;
						}
						if (SchemaProperties.CHARACTER_SET.getLabel().equals(propertyName)){
							return true;
						}
						if (SchemaProperties.CHARACTER_SEMANTICS.getLabel().equals(propertyName)){
							return true;
						}
						return eq;
					});
			final DbObjectDifference diff=currentTable.diff(table, equalsHandler);
			final ConnectionSqlExecutor executor=new ConnectionSqlExecutor(connection);
			final List<SqlOperation> sqlList=dialect.createSqlFactoryRegistry().createSql(diff);
			executor.setAutoClose(false);
			if (!sqlList.isEmpty()){
				final List<SqlOperation> lockTableSqlList=dialect.createSqlFactoryRegistry().createSql(table, SqlType.LOCK);
				executor.execute(lockTableSqlList);
				executor.execute(sqlList);
				try(Statement statement=connection.createStatement();){
					String name=dialect.quote(currentTable.getName());
					if (currentTable.getSchemaName()!=null){
						name=dialect.quote(currentTable.getSchemaName())+"."+name;
					}
					statement.execute("UPDATE "+name
							+" SET "+dialect.quote(this.getStatusColumnName())+"='"+Status.Completed+"' WHERE "+dialect.quote(this.getStatusColumnName())+" IS NULL");
					if (this.isWithSeriesNumber()){
						statement.execute("UPDATE "+name
							+" SET "+dialect.quote(this.getSeriesNumberColumnName())+"="+dialect.quote(this.getIdColumnName())+" WHERE "+dialect.quote(this.getSeriesNumberColumnName())+" IS NULL");
					}
				}
			}
		}
	}

	protected String outputCurrent(final Table table, final DbVersionHandler dbVersionHandler){
		final OutputTextBuilder builder=createOutputTextBuilder();
		builder.append("Database status.");
		builder.lineBreak();
		dbVersionHandler.append(table, builder);
		final String value=builder.toString();
		this.println(value);
		return value;
	}

	protected List<Row> getVersionRows(final Table table, final List<SqlFile> sqlFiles, final DbVersionHandler dbVersionHandler){
		this.println("lastChangeToApply="+getLastChangeToApply());
		final List<Row> rows=dbVersionHandler.getRowsForVersionUp(table, getLastChangeToApply());
		return rows;
	}

	protected void executeChangeVersion(final Dialect dialect, final Table table, final List<Row> rows, final List<SqlFile> sqlFiles, final DbVersionHandler dbVersionHandler){
		final Map<Long, SqlFile> sqlFileMap=CommonUtils.map();
		for(final SqlFile sqlFile:sqlFiles){
			sqlFileMap.put(sqlFile.getVersionNumber(), sqlFile);
		}
		Long seriesNumber=null;
		Connection connection=null;
		Long id=null;
		Row currentRow=null;
		try{
			final SqlConverter sqlConverter=getSqlConverter();
			connection=this.getConnection();
			connection.setAutoCommit(false);
			final List<SplitResult> setupSqls=read(dialect, this.getSetupSqlDirectory());
			if (!CommonUtils.isEmpty(setupSqls)){
				this.println("*********** execute setup sql. ***********");
			}
			executeSql(connection, sqlConverter, new ParametersContext(), setupSqls);
			final List<SqlOperation> ddlAutoCommitOffSqlList=dialect.createSqlFactoryRegistry().createSql(SqlType.DDL_AUTOCOMMIT_OFF);
			final List<SqlOperation> lockTableSqlList=dialect.createSqlFactoryRegistry().createSql(table, SqlType.LOCK);
			final ConnectionSqlExecutor executor=new ConnectionSqlExecutor(this.getConnection());
			executor.setAutoClose(false);
			if (!CommonUtils.isEmpty(rows)){
				this.println("*********** execute version sql. ***********");
			}
			for(final Row row:rows){
				id=dbVersionHandler.getId(row);
				if (id==null){
					continue;
				}
				if(!preCheck(connection, dialect, table, id, row, dbVersionHandler)){
					return;
				}
				connection.setAutoCommit(false);
				executor.execute(ddlAutoCommitOffSqlList);
				executor.execute(lockTableSqlList);
				if(!startVersion(connection, dialect, table, row, seriesNumber!=null?seriesNumber:id, dbVersionHandler)){
					return;
				}
				if (seriesNumber==null){
					seriesNumber=id;
				}
				currentRow=row;
				executeSql(connection, sqlConverter, id, sqlFileMap);
				finalizeVersion(connection, dialect, table, row, id, dbVersionHandler);
				connection.commit();
				currentRow=null;
			}
			final List<SplitResult> finalizeSqls=read(dialect, this.getFinalizeSqlDirectory());
			if (!CommonUtils.isEmpty(finalizeSqls)){
				this.println("*********** execute finalize sql. ***********");
			}
			executeSql(connection, sqlConverter, new ParametersContext(), finalizeSqls);
			connection.commit();
		} catch (final RuntimeException e) {
			final String sql=ThreadContext.getSql();
			logger.error("sql=["+sql+"]", e);
			if (connection!=null){
				rollback(connection);
				if (currentRow!=null&&id!=null){
					if (executedSqlCount>0){
						try {
							connection.setAutoCommit(false);
							errorVersion(connection, dialect, table, currentRow, id, dbVersionHandler);
							connection.commit();
						} catch (final SQLException e1) {
							logger.error("set error "+currentRow+" status failed.", e);
						}
					} else{
						try {
							connection.setAutoCommit(false);
							deleteVersion(connection, dialect, table, currentRow, dbVersionHandler);
							connection.commit();
						} catch (final SQLException e1) {
							logger.error(this.getSchemaChangeLogTableName()+" recovery failed.", e);
						}
					}
				}
			}
			this.getExceptionHandler().handle(e);
		} catch (final SQLException e) {
			rollback(connection);
			logger.error("version update error.", e);
			this.getExceptionHandler().handle(e);
		}
	}

	private int executedSqlCount=0;
	
	protected void executeSql(final Connection connection, final SqlConverter sqlConverter, final Long id, final Map<Long, SqlFile> sqlFileMap){
		final SqlFile sqlFile=sqlFileMap.get(id);
		final ParametersContext context=new ParametersContext();
		context.putAll(this.getContext());
		final List<SplitResult> sqls=getSqls(sqlFile);
		executedSqlCount=0;
		for(final SplitResult splitResult:sqls){
			executeSql(connection, sqlConverter, context, splitResult);
			executedSqlCount++;
		}
	}

	protected void executeSql(final Connection connection, final SqlConverter sqlConverter, final ParametersContext context, final List<SplitResult> splitResults){
		splitResults.forEach(splitResult->{
			executeSql(connection, sqlConverter, context, splitResult);
		});
	}
	
	protected void executeSql(final Connection connection, final SqlConverter sqlConverter, final ParametersContext context, final SplitResult splitResult){
		final SqlNode sqlNode=sqlConverter.parseSql(context, splitResult.getText());
		final JdbcHandler jdbcHandler=new JdbcHandler(sqlNode);
		jdbcHandler.execute(connection, context);
	}

	protected List<SplitResult> getSqls(final SqlFile sqlFile){
		return sqlFile.getUpSqls();
	}

	protected boolean preCheck(final Connection connection, final Dialect dialect, final Table table, final Long id, final Row row, final DbVersionHandler dbVersionHandler) throws SQLException{
		return !dbVersionHandler.exists(dialect, connection, table, id);
	}

	protected boolean startVersion(final Connection connection, final Dialect dialect, final Table table, final Row row, final Long seriesNumber, final DbVersionHandler dbVersionHandler) throws SQLException{
		try{
			dbVersionHandler.insertVersion(connection, dialect, table, row, seriesNumber, Status.Started);
			return true;
		} catch(final SQLIntegrityConstraintViolationException e){
			return false;
		}
	}
	
	protected void finalizeVersion(final Connection connection, final Dialect dialect, final Table table, final Row row, final Long id, final DbVersionHandler dbVersionHandler) throws SQLException{
		dbVersionHandler.updateVersion(connection, dialect, table, row, id, Status.Started, Status.Completed);
	}

	protected void errorVersion(final Connection connection, final Dialect dialect, final Table table, final Row row, final Long id, final DbVersionHandler dbVersionHandler) throws SQLException{
		dbVersionHandler.updateVersion(connection, dialect, table, row, id, Status.Started, Status.Errored);
	}
	
	protected void deleteVersion(final Connection connection, final Dialect dialect, final Table table, final Row row, final DbVersionHandler dbVersionHandler) throws SQLException{
		dbVersionHandler.deleteVersion(connection, dialect, table, row);
	}

	protected void deleteVersion(final Table table, final long id) throws SQLException{
		final Connection connection=this.getConnection();
		connection.setAutoCommit(false);
		final Dialect dialect=DialectResolver.getInstance().getDialect(connection);
		try {
			final DbVersionHandler dbVersionHandler=createDbVersionHandler();
			dbVersionHandler.deleteVersion(connection, dialect, table, id);
			connection.commit();
		} catch (final SQLException e) {
			rollback(connection);
			this.getExceptionHandler().handle(e);
		}
	}

	protected String getName(final Table table){
		if (CommonUtils.isEmpty(table.getSchemaName())){
			return table.getName();
		}
		return table.getSchemaName()+"."+table.getName();
	}
	
	/**
	 * @return the sqlDirectory
	 */
	public File getSqlDirectory() {
		return sqlDirectory;
	}

	/**
	 * @param sqlDirectory the sqlDirectory to set
	 */
	public void setSqlDirectory(final File sqlDirectory) {
		this.sqlDirectory = sqlDirectory;
	}

	/**
	 * @param sqlDirectory the sqlDirectory to set
	 */
	public void setSqlDirectory(final String sqlDirectory) {
		this.sqlDirectory = new File(sqlDirectory);
	}

	/**
	 * @return the previousState
	 */
	public String getPreviousState() {
		return previousState;
	}

	/**
	 * @return the previousTable
	 */
	public Table getPreviousTable() {
		return previousTable;
	}

	/**
	 * @return the lastState
	 */
	public String getLastState() {
		return lastState;
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

	/**
	 * @return the table
	 */
	public Table getTable() {
		return table;
	}

	/**
	 * @return the downSqlDirectory
	 */
	public File getDownSqlDirectory() {
		return downSqlDirectory;
	}

	/**
	 * @param downSqlDirectory the downSqlDirectory to set
	 */
	public void setDownSqlDirectory(final File downSqlDirectory) {
		this.downSqlDirectory = downSqlDirectory;
	}

	/**
	 * @param downSqlDirectory the downSqlDirectory to set
	 */
	public void setDownSqlDirectory(final String downSqlDirectory) {
		this.downSqlDirectory = new File(downSqlDirectory);
	}

	/**
	 * @return the showVersionOnly
	 */
	public boolean isShowVersionOnly() {
		return showVersionOnly;
	}

	/**
	 * @param showVersionOnly the showVersionOnly to set
	 */
	public void setShowVersionOnly(final boolean showVersionOnly) {
		this.showVersionOnly = showVersionOnly;
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

	/**
	 * @return the lastChangeToApply
	 */
	public Long getLastChangeToApply() {
		return lastChangeToApply;
	}

	/**
	 * @param lastChangeToApply the lastChangeToApply to set
	 */
	public void setLastChangeToApply(final Long lastChangeToApply) {
		this.lastChangeToApply = lastChangeToApply;
	}

	/**
	 * @return the setupSqlDirectory
	 */
	public File getSetupSqlDirectory() {
		return setupSqlDirectory;
	}

	/**
	 * @param setupSqlDirectory the setupSqlDirectory to set
	 */
	public void setSetupSqlDirectory(final File setupSqlDirectory) {
		this.setupSqlDirectory = setupSqlDirectory;
	}

	/**
	 * @return the finalizeSqlDirectory
	 */
	public File getFinalizeSqlDirectory() {
		return finalizeSqlDirectory;
	}

	/**
	 * @param finalizeSqlDirectory the finalizeSqlDirectory to set
	 */
	public void setFinalizeSqlDirectory(final File finalizeSqlDirectory) {
		this.finalizeSqlDirectory = finalizeSqlDirectory;
	}

}
