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

import java.io.IOException;
import java.io.Writer;
/**
 * 複数のWriterを束ねるWriter
 *
 */
public class CompositeWriter extends Writer{

	private Writer[] writers;
	
	/**
	 * コンストラクタ
	 */
	protected CompositeWriter(){
	}
	
	/**
	 * コンストラクタ
	 * @param writers
	 */
	public CompositeWriter(Writer... writers){
		this.writers=writers;
	}
	
	
	/**
	 * @param writers the writers to set
	 */
	protected void setWriters(Writer[] writers) {
		this.writers = writers;
	}


	/* (non-Javadoc)
	 * @see java.io.Writer#write(char[], int, int)
	 */
	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		int size=writers.length;
		IOException ioe=null;
		for(int i=0;i<size;i++){
			try{
				writers[i].write(cbuf, off, len);
			}catch(IOException e){
				if (ioe==null){
					ioe=e;
				}
			}
		}
		if (ioe!=null){
			throw ioe;
		}
	}

	/* (non-Javadoc)
	 * @see java.io.Writer#flush()
	 */
	@Override
	public void flush() throws IOException {
		int size=writers.length;
		IOException ioe=null;
		for(int i=0;i<size;i++){
			try{
				writers[i].flush();
			}catch(IOException e){
				if (ioe==null){
					ioe=e;
				}
			}
		}
		if (ioe!=null){
			throw ioe;
		}
	}

	@Override
	public void close() throws IOException {
		int size=writers.length;
		IOException ioe=null;
		for(int i=0;i<size;i++){
			try{
				writers[i].close();
			}catch(IOException e){
				if (ioe==null){
					ioe=e;
				}
			}
		}
		if (ioe!=null){
			throw ioe;
		}
	}

}
