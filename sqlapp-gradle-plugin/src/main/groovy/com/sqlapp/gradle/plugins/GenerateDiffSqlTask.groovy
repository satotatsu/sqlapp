/*
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.gradle.plugins

import com.sqlapp.data.db.command.GenerateDiffSqlCommand
import com.sqlapp.data.db.command.version.DbVersionFileHandler
import com.sqlapp.data.db.command.version.DbVersionFileHandler.SqlFile
import com.sqlapp.data.db.sql.FileSqlExecutor
import com.sqlapp.data.db.sql.SqlOperation
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType
import com.sqlapp.data.db.sql.StandardOutSqlExecutor
import com.sqlapp.data.db.sql.WriterSqlExecutor
import com.sqlapp.data.schemas.AbstractDbObject
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.EqualsHandler;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.gradle.plugins.pojo.GenerateDiffSqlPojo
import com.sqlapp.util.CommonUtils
import com.sqlapp.util.FileUtils;

import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.List;

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class GenerateDiffSqlTask extends AbstractGenerateSqlTask {

	@TaskAction
	def exec() {
		GenerateDiffSqlCommand command=new GenerateDiffSqlCommand();
		GenerateDiffSqlPojo pojo=project.generateDiffSql;
		this.parameters=pojo.parameters;
		this.debug=pojo.debug;
		command.original=SchemaUtils.readXml(getFile(pojo.originalFile));
		command.target=SchemaUtils.readXml(getFile(pojo.targetFile));
		command.setSchemaOptions(pojo.schemaOptions);
		File outputPath;
		if (pojo.outputPath!=null){
			outputPath=getFile(pojo.outputPath);
		} else{
			outputPath=getFile("./");
		}
		String encoding=pojo.encoding;
		command.equalsHandler=pojo.equalsHandler;
		run(command);
		if (command.sqlOperations.isEmpty()){
			return;
		}
		if (outputPath==null){
			StandardOutSqlExecutor executor=new StandardOutSqlExecutor();
			executor.execute(command.sqlOperations);
		} else{
			if (this.debug){
				StandardOutSqlExecutor executor=new StandardOutSqlExecutor();
				executor.execute(command.sqlOperations.toArray(new SqlOperation[0]));
			}
			int step=10;
			if (pojo.changeNumberStep!=null){
				step=pojo.changeNumberStep;
			}
			if (pojo.outputAsMultiFiles){
				if (!outputPath.exists()){
					outputPath.mkdirs()
				}
				long current=getCurrentNumber(pojo);
				String suffix=getFileSuffix(pojo);
				for(SqlOperation operation:command.sqlOperations){
					current=current+step;
					File file=new File(""+getFilename(current, pojo.numberOfDigits, toString(operation.getSqlType())+"_"+getName(operation), suffix), outputPath);
					FileSqlExecutor executor=new FileSqlExecutor(file, encoding);
					executor.execute(operation);
				}
			} else{
				FileUtils.createParentDirectory(outputPath);
				List<SqlOperation> sqlOperations=command.sqlOperations;
				if (sqlOperations.isEmpty()){
					return;
				}
				List<SqlOperation> reverseSqlOperations=null;
				if (pojo.isWithVersionDown()){
					command.swap();
					run(command);
					reverseSqlOperations=command.sqlOperations;
					reverseSqlOperations.add(0, SqlOperation.EMPTY_LINE_OPERATION);
					reverseSqlOperations.add(0, SqlOperation.UNDO_OPERATION);
					reverseSqlOperations.add(0, SqlOperation.EMPTY_LINE_OPERATION);
					reverseSqlOperations.add(0, SqlOperation.COMMENT_SEPARATOR_OPERATION);
					sqlOperations.addAll(reverseSqlOperations);
				}
				SqlOperation operation=getSqlOperation(sqlOperations);
				if (outputPath.exists()){
					if (outputPath.isDirectory()){
						long current=getCurrentNumber(pojo);
						current=current+step;
						String suffix=getFileSuffix(pojo);
						File file=new File(""+getFilename(current, pojo.numberOfDigits, toString(operation.getSqlType())+"_"+getName(operation), suffix), outputPath);
						FileSqlExecutor executor=new FileSqlExecutor(file, encoding);
						executor.execute(sqlOperations);
						return;
					}
				}
				FileSqlExecutor executor=new FileSqlExecutor(outputPath, encoding);
				executor.execute(sqlOperations);
			}
		}
	}
	
	private SqlOperation getSqlOperation(List<SqlOperation> sqlOperations){
		for(SqlOperation sqlOperation:sqlOperations){
			if (sqlOperation.getSqlType()!=SqlType.SET_SEARCH_PATH_TO_SCHEMA){
				return sqlOperation;
			}
		}
		return sqlOperations.get(0);
	}
	
}
