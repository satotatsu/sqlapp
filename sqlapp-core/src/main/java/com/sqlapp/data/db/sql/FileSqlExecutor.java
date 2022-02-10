/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.sql;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * File SqlExecutor
 * 
 * @author tatsuo satoh
 * 
 */
public class FileSqlExecutor implements SqlExecutor {

	private final File file;

	private final String encoding;

	private final boolean append;
	
	public FileSqlExecutor(File file, String encoding, final boolean append){
		this.file=file;
		this.encoding=encoding;
		this.append=append;
	}

	public FileSqlExecutor(File file, String encoding){
		this(file, encoding, false);
	}

	@Override
	public void execute(SqlOperation... operations) throws Exception {
		try(OutputStream os=new FileOutputStream(file, append);
			OutputStreamWriter writer = new OutputStreamWriter(os, encoding);
			BufferedWriter bw=new BufferedWriter(writer);){
			WriterSqlExecutor internal=new WriterSqlExecutor(bw);
			internal.execute(operations);
		}
	}

}
