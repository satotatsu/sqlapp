/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.OutputTextBuilder;

/**
 * クエリを実行して結果を標準出力に出力します。
 * 
 * @author tatsuo satoh
 *
 */
public class SqlQueryCommand extends AbstractSqlQueryCommand {

	@Override
	protected void outputTableData(final Dialect dialect, final Table table) {
		final OutputTextBuilder builder = new OutputTextBuilder();
		builder.append(table);
		this.info(builder.toString());
	}

	@Override
	protected void outputTableData(final Dialect dialect, final Table table, final ResultSet resultSet)
			throws SQLException {
		StringBuilder builder = new StringBuilder();
		final int size = table.getColumns().size();
		for (final Column column : table.getColumns()) {
			builder.append(column.getName());
			builder.append(this.getOutputFormatType().getSeparator());
		}
		this.info(builder.substring(0, builder.length() - 1));
		while (resultSet.next()) {
			builder = new StringBuilder();
			for (int i = 1; i <= size; i++) {
				final Object obj = resultSet.getObject(i);
				final Column column = table.getColumns().get(i - 1);
				final String text = dialect.getValueForDisplay(column, obj);
				builder.append(text);
				builder.append(this.getOutputFormatType().getSeparator());
			}
			this.info(builder.substring(0, builder.length() - 1));
		}
	}

}
