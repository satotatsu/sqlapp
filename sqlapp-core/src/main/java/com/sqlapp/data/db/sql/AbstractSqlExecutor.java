/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import java.io.IOException;

/**
 * Writer SqlExecutor
 * 
 * @author tatsuo satoh
 * 
 */
public abstract class AbstractSqlExecutor implements SqlExecutor {


	@Override
	public void execute(SqlOperation... operations) throws Exception {
		for(SqlOperation operation:operations){
			if (operation.getSqlType()==null){
				writeStartStatementTerminator(operation);
				write(operation.getSqlText());
				write(";");
				write("\n");
				writeEndStatementTerminator(operation);
			} else if (operation.getSqlType().isEmptyLine()){
				write("\n");
			} else if (operation.getSqlType().isComment()){
				write(operation.getSqlText());
				write("\n");
			} else{
				writeStartStatementTerminator(operation);
				write(operation.getSqlText());
				write(";");
				write("\n");
				writeEndStatementTerminator(operation);
			}
			write("\n");
		}
	}

	private void writeStartStatementTerminator(SqlOperation operation) throws Exception{
		if (operation.getStartStatementTerminator()!=null){
			write(operation.getStartStatementTerminator());
			write("\n");
		}
	}
	
	protected abstract void write(String value) throws IOException;

	private void writeEndStatementTerminator(SqlOperation operation) throws Exception{
		if (operation.getEndStatementTerminator()!=null){
			write(operation.getEndStatementTerminator());
			write("\n");
		}
	}

}
