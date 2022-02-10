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

package com.sqlapp.util;

import java.io.PrintWriter;
import java.io.Writer;
/**
 * デバッグ用のWriter
 *
 */
public class DebugWriter extends CompositeWriter{
	
	/**
	 * コンストラクタ
	 * @param writers
	 */
	public DebugWriter(Writer... writers){
		super();
		Writer[] addWriters=new Writer[writers.length+1];
		PrintWriter writer=new PrintWriter(System.out);
		addWriters[0]=writer;
		System.arraycopy(writers, 0, addWriters, 1, writers.length);
		this.setWriters(addWriters);
	}

}
