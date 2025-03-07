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

package com.sqlapp.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.sqlapp.util.FileUtils.*;

/**
 * Runtime実行クラス
 *
 */
public class RuntimeExec {

	private ProcessBuilder processBuilder=null;
	private String charsetName=null;

	private List<String> stdout=null;

	private List<String> stderr=null;

	public RuntimeExec(String... commands){
		processBuilder=new ProcessBuilder(commands);
	}

	public RuntimeExec(List<String> command){
		processBuilder=new ProcessBuilder(command);
	}
	
	public void setCharset(String charsetName){
		this.charsetName=charsetName;
	}

	/**
	 * 標準出力
	 */
	public List<String> stdout(){
		return stdout;
	}

	/**
	 * 標準エラー
	 */
	public List<String> stderr(){
		return stderr;
	}

	public Map<String, String> environment(){
		return processBuilder.environment();
	}

	public int exec(){
		Process process=null;
		try {
			process=processBuilder.start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		StreamReaderThread stdoutThread=new StreamReaderThread(process.getInputStream(), charsetName);
		StreamReaderThread stderrThread=new StreamReaderThread(process.getErrorStream(), charsetName);
		stdoutThread.start();
		stderrThread.start();
		int ret=0;
		try {
			ret=process.waitFor();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		try {
			stdoutThread.join();
			stderrThread.join();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		stdout=stdoutThread.getResult();
		stderr=stderrThread.getResult();
		return ret;
	}

	/**
	 * 標準出力、エラー読み取りスレッドクラス
	 */
	static class StreamReaderThread extends Thread{
		private BufferedReader reader=null;
		private List<String> result=new ArrayList<String>();
		
		public List<String> getResult(){
			return result;
		}
		public StreamReaderThread(InputStream stream, String charsetName){
			try {
				if (charsetName==null) {
					reader=new BufferedReader(new InputStreamReader(stream));
				} else {
					reader=new BufferedReader(new InputStreamReader(stream, charsetName));					
				}
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}

		public StreamReaderThread(InputStream stream){
			reader=new BufferedReader(new InputStreamReader(stream));
		}
		
		@Override
	    public void run(){
	    	String line=null;
	    	try {
				while(reader.ready()){
					line=reader.readLine();
					result.add(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			close(reader);
	    }
	}
}
