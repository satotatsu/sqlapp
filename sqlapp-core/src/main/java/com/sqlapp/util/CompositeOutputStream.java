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
import java.io.OutputStream;

/**
 * 複数のOutputStreamを束ねるOutputStream
 *
 */
public class CompositeOutputStream extends OutputStream{

	private OutputStream[] outputStreams;
	
	/**
	 * コンストラクタ
	 */
	protected CompositeOutputStream(){
	}
	
	/**
	 * コンストラクタ
	 * @param outputStreams
	 */
	public CompositeOutputStream(OutputStream... outputStreams){
		this.outputStreams=outputStreams;
	}
	
	/**
	 * @param outputStreams the outputStreams to set
	 */
	protected void setOutputStreams(OutputStream[] outputStreams) {
		this.outputStreams = outputStreams;
	}

	/* (non-Javadoc)
	 * @see java.io.OutputStream#write(byte[], int, int)
	 */
	@Override
	public void write(byte b[], int off, int len) throws IOException {
		int size=outputStreams.length;
		IOException ioe=null;
		for(int i=0;i<size;i++){
			try{
				outputStreams[i].write(b, off, len);
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
	 * @see java.io.OutputStream#write(int)
	 */
	@Override
	public void write(int b) throws IOException {
		int size=outputStreams.length;
		for(int i=0;i<size;i++){
			outputStreams[i].write(b);
		}
	}
	
	/* (non-Javadoc)
	 * @see java.io.OutputStream#flush()
	 */
	@Override
    public void flush() throws IOException {
		int size=outputStreams.length;
		IOException ioe=null;
		for(int i=0;i<size;i++){
			try{
				outputStreams[i].flush();
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
	 * @see java.io.OutputStream#close()
	 */
	@Override
    public void close() throws IOException {
		int size=outputStreams.length;
		IOException ioe=null;
		for(int i=0;i<size;i++){
			try{
				outputStreams[i].close();
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
