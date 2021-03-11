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
package com.sqlapp.data.db.command;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;

/**
 * スキーマ同期コマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class SynchronizeSchemaCommand extends AbstractSynchronizeCommand {

	public SynchronizeSchemaCommand() {
		this.setEqualsHandler(DefaultSchemaEqualsHandler.getInstance());
	}

	@Override
	protected void handle(final DbObjectDifference diff,
			final SqlFactoryRegistry operationRegistry, final Connection connection, final Dialect dialect) throws Exception {
		final SqlFactory<?> operation = operationRegistry.getSqlFactory(
				diff, SqlType.ALTER);
		final Options operationOption = operation.getOptions()
				.clone();
		operation.setOptions(operationOption);
		final List<SqlOperation> operations = operation.createDiffSql(diff);
		getSqlExecutor().execute(operations);
	}
}
