/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 *
 * sqlapp-core-dialect-test is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-dialect-test is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-dialect-test.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.command;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.test.AbstractTest;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

public abstract class AbstractGenerateCreateSqlTest extends AbstractTest{
	
	private final String packageName=CommonUtils.last(this.getClass().getPackage().getName().split("\\."));
	
	protected String tempPath=FileUtils.combinePath("temp", packageName);
	
	protected String outputPath=FileUtils.combinePath("out", packageName);

	private final String outputDumpFileName="dump.xml";
	private final String outputSqlFileName=FileUtils.combinePath(outputPath, "createCatalog.sql");

	@BeforeEach
	public void before(){
	}
	
	@AfterEach
	public void after(){
	}
	
	@Test
	public void generateSql() throws SQLException, XMLStreamException, IOException {
		try{
			final Dialect dialect = getDialect();
			final SqlFactoryRegistry sqlFactoryRegistry = dialect.createSqlFactoryRegistry();
			final SqlFactory<Catalog> createCatalogOperationFactory=sqlFactoryRegistry.getSqlFactory(new Catalog(""), SqlType.CREATE);
			final File file=new File(FileUtils.combinePath(outputPath, outputDumpFileName));
			if (!file.exists()){
				return;
			}
			final Catalog catalog=SchemaUtils.readXml(file);
			final List<SqlOperation> operations=createCatalogOperationFactory.createSql(catalog);
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
		}
	}

	protected void initialize(final ExportXmlCommand command) throws SQLException {
	}
	
	protected abstract Dialect getDialect();
	
}
