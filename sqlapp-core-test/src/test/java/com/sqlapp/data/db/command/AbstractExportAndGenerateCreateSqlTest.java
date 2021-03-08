/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-test.
 *
 * sqlapp-core-test is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-test is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-test.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command;

import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.html.GenerateHtmlCommand;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.ObjectNameReaderPredicate;
import com.sqlapp.data.db.metadata.ReadDbObjectPredicate;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.jdbc.JdbcUtils;
import com.sqlapp.jdbc.SqlappDataSource;
import com.sqlapp.test.AbstractTest;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DbUtils;
import com.sqlapp.util.FileUtils;
import com.zaxxer.hikari.HikariConfig;

/**
 *
 */
public abstract class AbstractExportAndGenerateCreateSqlTest extends AbstractTest{

	private final String packageName=CommonUtils.last(this.getClass().getPackage().getName().split("\\."));
	
	protected String tempPath=FileUtils.combinePath("temp", packageName);
	
	protected File outputPath=new File(FileUtils.combinePath("out", packageName));

	private final String outputSqlFileName=FileUtils.combinePath(outputPath, "createCatalog.sql");

	private final File outputHtmlPath=new File(FileUtils.combinePath(outputPath, "html"));

	private final File dictionariesPath=new File("src/main/resources/dictionaries");
	
	private String[] includeSchemas=null;

	private String[] includeRowDumpTables=null;

	private String target="catalog";

	private boolean dumpRows=false;

	private String diagramFont=null;
	
	@BeforeEach
	public void before(){
		FileUtils.remove(FileUtils.combinePath(this.tempPath));
		diagramFont=getTestProp("diagram.font");
	}
	
	@AfterEach
	public void after(){
		FileUtils.remove(FileUtils.combinePath(this.tempPath));
	}
	
	@Test
	public void readCatalogAndGenerateSql() throws Exception {
		Connection connection=null;
		if (CommonUtils.isEmpty(this.getUrl())){
			System.out.println("["+this.getClass().getSimpleName()+"] url is empty.");
			return;
		}
		final DataSource dataSource=this.newDataSource();
		try{
			connection=dataSource.getConnection();
			initialize(connection);
		} finally{
			DbUtils.close(connection);
		}
		final ExportXmlCommand command=new ExportXmlCommand();
		command.setDataSource(dataSource);
		command.setDumpRows(this.dumpRows);
		//command.setOutputFileName(outputDumpFileName);
		command.setOutputPath(outputPath);
		command.setIncludeSchemas(includeSchemas);
		command.setIncludeRowDumpTables(includeRowDumpTables);
		command.setTarget(this.getTarget());
		initialize(command);
		try{
			command.run();			
		} catch(final Exception e){
			e.printStackTrace();
			throw e;
		}
		generateHtml(new File(command.getOutputFileFullPath()));
		try{
			connection=dataSource.getConnection();
			final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			final SqlFactoryRegistry sqlFactoryRegistry = dialect.getSqlFactoryRegistry();
			final SqlFactory<Catalog> createCatalogOperationFactory=sqlFactoryRegistry.getSqlFactory(new Catalog(""), SqlType.CREATE);
			final CatalogReader reader=dialect.getCatalogReader();
			reader.setReadDbObjectPredicate(getMetadataReaderFilter());
			final List<Catalog> catalogs=reader.getAllFull(connection);
			final List<SqlOperation> operations=createCatalogOperationFactory.createSql(catalogs);
			final StringBuilder builder=new StringBuilder();
			for(int i=0;i<operations.size();i++){
				final SqlOperation operation=operations.get(i);
				builder.append(operation.getSqlText());
				builder.append(";\n\n");
			}
			String text;
			if (builder.length()>1){
				text= builder.substring(0, builder.length()-1).toString();
			} else{
				text="";
			}
			FileUtils.writeText(outputSqlFileName, "UTF8", text);
		} finally{
			DbUtils.close(connection);
		}
	}
	
	protected void generateHtml(final File targetFile){
		final GenerateHtmlCommand command=new GenerateHtmlCommand();
		command.setTargetFile(targetFile);
		command.setOutputDirectory(outputHtmlPath);
		command.setDictionaryFileDirectory(dictionariesPath);
		command.setDictionaryFileType("xml");
		if (!CommonUtils.isEmpty(diagramFont)){
			command.setDiagramFont(diagramFont);
		}
		command.run();
	}
	
	protected ReadDbObjectPredicate getMetadataReaderFilter() {
		final ReadDbObjectPredicate readerFilter = new ObjectNameReaderPredicate(
				this.getIncludeSchemas(), new String[0],
				new String[0], new String[0]);
		return readerFilter;
	}


	protected void initialize(final Connection connection) throws SQLException {
	}

	
	protected void initialize(final ExportXmlCommand command) throws SQLException {
	}

	protected HikariConfig getPoolConfiguration() {
		final HikariConfig poolConfiguration = new HikariConfig();
		poolConfiguration.setJdbcUrl(this.getUrl());
		if (this.getDriverClassName()==null){
			poolConfiguration.setDriverClassName(JdbcUtils.getDriverClassNameByUrl(this.getUrl()));
		} else{
			poolConfiguration.setDriverClassName(this.getDriverClassName());
		}
		if (getUsername()!=null){
			poolConfiguration.setUsername(this.getUsername());
		}
		if (getPassword()!=null){
			poolConfiguration.setPassword(this.getPassword());
		}
		return poolConfiguration;
	}

	protected DataSource newDataSource() {
		final DataSource ds = new SqlappDataSource(
					new com.zaxxer.hikari.HikariDataSource(
							getPoolConfiguration()));
		return ds;
	}

	/**
	 * @return the driverClassName
	 */
	public abstract String getDriverClassName();

	/**
	 * @return the url
	 */
	public abstract String getUrl();

	/**
	 * @return the username
	 */
	public abstract String getUsername();

	/**
	 * @return the password
	 */
	public abstract String getPassword();
	
	protected void executeSqlFileSilent(final Connection connection, final String fileName) {
		Statement statement = null;
		try {
			statement = connection.createStatement();
			final InputStream is = FileUtils
					.getInputStream(this.getClass(), fileName);
			final String sql = FileUtils.readText(is, "utf8");
			statement.execute(sql);
		} catch (final SQLException e) {
			e.printStackTrace();
		} finally {
			DbUtils.close(statement);
		}
	}

	protected SqlExecuteCommand createSqlExecuteCommand(final Connection connection) {
		final SqlExecuteCommand command=new SqlExecuteCommand();
		command.setConnection(connection);
		command.setEncoding("utf8");
		return command;
	}
	
	/**
	 * @return the includeSchemas
	 */
	public String[] getIncludeSchemas() {
		return includeSchemas;
	}

	/**
	 * @param includeSchemas the includeSchemas to set
	 */
	public void setIncludeSchemas(final String... includeSchemas) {
		this.includeSchemas = includeSchemas;
	}

	/**
	 * @return the includeRowDumpTables
	 */
	public String[] getIncludeRowDumpTables() {
		return includeRowDumpTables;
	}

	/**
	 * @param includeRowDumpTables the includeRowDumpTables to set
	 */
	public void setIncludeRowDumpTables(final String... includeRowDumpTables) {
		this.includeRowDumpTables = includeRowDumpTables;
	}

	/**
	 * @return the target
	 */
	public String getTarget() {
		return target;
	}

	/**
	 * @param target the target to set
	 */
	public void setTarget(final String target) {
		this.target = target;
	}

	/**
	 * @return the dumpRows
	 */
	public boolean isDumpRows() {
		return dumpRows;
	}

	/**
	 * @param dumpRows the dumpRows to set
	 */
	public void setDumpRows(final boolean dumpRows) {
		this.dumpRows = dumpRows;
	}
	
}
