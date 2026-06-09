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

package com.sqlapp.gradle.plugins;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import org.gradle.api.model.ObjectFactory;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.version.DbVersionFileHandler;
import com.sqlapp.data.db.command.version.DbVersionFileHandler.SqlFile;
import com.sqlapp.data.db.sql.SqlExecutor;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.gradle.plugins.extension.AbstractExtension;
import com.sqlapp.gradle.plugins.extension.AbstractGenerateSqlExtension;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.Java8DateUtils;

@DisableCachingByDefault
public abstract class AbstractGenerateSqlTask<T extends AbstractCommand, S extends AbstractExtension>
		extends AbstractTask<T, S> {
	@Inject
	public AbstractGenerateSqlTask(ObjectFactory objectFactory) {
		super(objectFactory);
	}

	protected String toString(SqlType sqlType) {
		return sqlType.toString().toLowerCase();
	}

	protected String getFilename(long current, int numberOfDigits, String name, String suffix) {
		return "" + getFormattedNumbers(current, numberOfDigits) + "_" + name + suffix;
	}

	protected long getCurrentNumber(AbstractGenerateSqlExtension obj) {
		final DbVersionFileHandler dbVersionFileHandler = new DbVersionFileHandler();
		if (obj.getOutputDirectory().isPresent()) {
			final File file = obj.getOutputDirectory().get().getAsFile();
			if (file.exists() && file.isDirectory()) {
				dbVersionFileHandler.setUpSqlDirectory(file);
				List<SqlFile> sqlFiles = dbVersionFileHandler.read();
				if (!sqlFiles.isEmpty()) {
					return CommonUtils.last(sqlFiles).getVersionNumber();
				}
			}
		}
		if (obj.getLastChangeNumber().isPresent()) {
			return obj.getOrElseLastChangeNumber();
		} else {
			String val = Java8DateUtils.format(LocalDateTime.now(), "yyyyMMddHHmmss");
			return Long.valueOf(val) * 1000;
		}
	}

	protected String getFileSuffix(AbstractGenerateSqlExtension obj) {
		String suffix;
		if (obj.getOutputFileExtension().isPresent() && CommonUtils.isEmpty(obj.getOutputFileExtension().get())) {
			suffix = "." + obj.getOutputFileExtension().get();
		} else {
			suffix = ".sql";
		}
		return suffix;
	}

	protected String getFormattedNumbers(Number num, int numOfDigits) {
		final StringBuilder builder = new StringBuilder(numOfDigits + 19);
		final String numText = "" + num;
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
		final DbCommonObject<?> obj = getObject(operation);
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
