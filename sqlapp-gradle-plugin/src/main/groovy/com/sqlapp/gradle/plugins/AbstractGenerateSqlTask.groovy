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

import com.sqlapp.data.db.command.version.DbVersionFileHandler
import com.sqlapp.data.db.command.version.DbVersionFileHandler.SqlFile
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractBaseDbObject
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.EqualsHandler;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.gradle.plugins.pojo.AbstractGenerateSqlPojo
import com.sqlapp.util.CommonUtils

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

abstract class AbstractGenerateSqlTask extends AbstractTask {


	protected String toString(SqlType sqlType){
		return sqlType.toString().toLowerCase();
	}
	
	protected String getFilename(long current, int numberOfDigits, String name, String suffix){
		return ""+getFormattedNumbers(current, numberOfDigits)+"_"+name+suffix;
	}
	
	protected long getCurrentNumber(AbstractGenerateSqlPojo pojo){
		DbVersionFileHandler dbVersionFileHandler=new DbVersionFileHandler();
		File file=getFile(pojo.outputPath);
		if (file.exists()&&file.isDirectory()){
			dbVersionFileHandler.upSqlDirectory=getFile(file);
			List<SqlFile> sqlFiles=dbVersionFileHandler.read();
			if (!sqlFiles.isEmpty()){
				return sqlFiles[sqlFiles.size()-1].versionNumber;
			}
		}
		if (pojo.lastChangeNumber!=null){
			return pojo.lastChangeNumber;
		} else{
			return System.currentTimeMillis();
		}
	}
	
	protected String getFileSuffix(AbstractGenerateSqlPojo pojo){
		String suffix;
		if (pojo.outputFileExtension!=null&&pojo.outputFileExtension!=""){
			suffix="."+pojo.outputFileExtension;
		} else{
			suffix="";
		}
		return suffix;
	}	
	
	protected String getFormattedNumbers(Number num, int numOfDigits){
		StringBuilder builder=new StringBuilder(numOfDigits+19);
		String numText=""+num;
		for(int i=0;i<numOfDigits;i++){
			builder.append("0");
		}
		builder.append(num);
		int len=builder.size();
		if (numText.length()>numOfDigits){
			return numText;
		}
		return builder.substring(len-numOfDigits, len);
	}
	
	protected String getName(SqlOperation operation){
		DbCommonObject<?> obj=getObject(operation);
		return SchemaUtils.getSimpleName(obj);
	}

	protected DbCommonObject<?> getObject(SqlOperation operation){
		if (operation.target!=null){
			return operation.target;
		}
		return operation.getOriginal();
	}
	
}
