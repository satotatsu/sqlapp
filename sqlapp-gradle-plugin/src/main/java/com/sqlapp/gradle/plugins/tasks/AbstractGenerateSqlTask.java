/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins.tasks;

import java.io.File;
import java.util.Collection;
import java.util.List;

import com.sqlapp.data.db.command.version.DbVersionFileHandler;
import com.sqlapp.data.db.command.version.DbVersionFileHandler.SqlFile;
import com.sqlapp.data.db.sql.SqlExecutor;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.gradle.plugins.extension.AbstractGenerateSqlExtension;
import com.sqlapp.util.CommonUtils;

public abstract class AbstractGenerateSqlTask extends AbstractTask {

	protected String toString(SqlType sqlType) {
		return sqlType.toString().toLowerCase();
	}

	protected String getFilename(long current, int numberOfDigits, String name, String suffix) {
		return "" + getFormattedNumbers(current, numberOfDigits) + "_" + name + suffix;
	}

	protected long getCurrentNumber(AbstractGenerateSqlExtension obj) {
		DbVersionFileHandler dbVersionFileHandler = new DbVersionFileHandler();
		File file = obj.getOutputPath().get().getAsFile();
		if (file.exists() && file.isDirectory()) {
			dbVersionFileHandler.setUpSqlDirectory(file);
			List<SqlFile> sqlFiles = dbVersionFileHandler.read();
			if (!sqlFiles.isEmpty()) {
				return CommonUtils.last(sqlFiles).getVersionNumber();
			}
		}
		if (obj.getLastChangeNumber().isPresent()) {
			return obj.getLastChangeNumber().get();
		} else {
			return System.currentTimeMillis();
		}
	}

	protected String getFileSuffix(AbstractGenerateSqlExtension obj) {
		String suffix;
		if (obj.getOutputFileExtension().isPresent() && CommonUtils.isEmpty(obj.getOutputFileExtension().get())) {
			suffix = "." + obj.getOutputFileExtension().get();
		} else {
			suffix = "";
		}
		return suffix;
	}

	protected String getFormattedNumbers(Number num, int numOfDigits) {
		StringBuilder builder = new StringBuilder(numOfDigits + 19);
		String numText = "" + num;
		for (int i = 0; i < numOfDigits; i++) {
			builder.append("0");
		}
		builder.append(num);
		int len = builder.length();
		if (numText.length() > numOfDigits) {
			return numText;
		}
		return builder.substring(len - numOfDigits, len);
	}

	protected String getName(SqlOperation operation) {
		DbCommonObject<?> obj = getObject(operation);
		return SchemaUtils.getSimpleName(obj);
	}

	protected DbCommonObject<?> getObject(SqlOperation operation) {
		if (operation.getTarget() != null) {
			return operation.getTarget();
		}
		return operation.getOriginal();
	}

	protected void execute(SqlExecutor sqlExecutor, SqlOperation... operations) {
		try {
			for (SqlOperation operation : operations) {
				sqlExecutor.execute(operation);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 指定されたオペレーションを実行します
	 * 
	 * @param operations
	 */
	protected void execute(SqlExecutor sqlExecutor, Collection<SqlOperation> operations) {
		try {
			sqlExecutor.execute(operations.toArray(new SqlOperation[0]));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
