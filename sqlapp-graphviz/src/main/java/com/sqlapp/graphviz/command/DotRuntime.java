/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-graphviz.
 *
 * sqlapp-graphviz is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-graphviz is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-graphviz.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.graphviz.command;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

public class DotRuntime {

	private static final String LINE_SEPARATOR=System.getProperty("file.separator");
	
	private static final boolean WIN32 = "\\".equals(LINE_SEPARATOR);
	
    private String dot;
	
	private File dir=new File("./");
	
	private OutputFormat outputFormat=OutputFormat.png;

	private final ExecutorService executorService;
	
	public DotRuntime(){
		executorService=Executors.newCachedThreadPool(new ThreadFactory(){
			@Override
			public Thread newThread(Runnable arg0) {
				Thread thread=new Thread(arg0);
				thread.setDaemon(true);
				return thread;
			}
		});
	}

	public String execute(String dotFile, String diagramFile){
		Process process;
		List<String> commandList=new ArrayList<>();
		try {
			commandList.add(getDotExe());
			commandList.add("-T" + getOutputFormat());
			commandList.add(dotFile);
			commandList.add("-o" + diagramFile);
			commandList.add("-Tcmapx");
			//commandList.add("-Timap");
			process = Runtime.getRuntime().exec(commandList.toArray(new String[0]), null, dir);
			ThreadReader threadReader=new ThreadReader(process.getInputStream());
			ThreadReader errorThreadReader=new ThreadReader(process.getErrorStream());
			Future<?> future1=executorService.submit(threadReader);
			Future<?> future2=executorService.submit(errorThreadReader);
			int ret = process.waitFor();
			if (ret == 0){
				future1.get();
				return threadReader.getResult();
			} else{
				future2.get();
				throw new DotCommandException(ret, errorThreadReader.getResult());
			}
		} catch (IOException e) {
			throw new DotRuntimeException(e);
		} catch (InterruptedException e) {
			throw new DotRuntimeException(e);
		} catch (ExecutionException e) {
			throw new DotRuntimeException(e);
		}
	}
	
	private static final Pattern DOT_VERSION_PATTERN=Pattern.compile("dot.*\\s+version\\s+(\\S+).*");
	
	public String getVersion(){
		Process process;
		List<String> commandList=new ArrayList<>();
		try {
			commandList.add(getDotExe());
			commandList.add("-V");
			process = Runtime.getRuntime().exec(commandList.toArray(new String[0]));
			ThreadReader threadReader=new ThreadReader(process.getInputStream());
			ThreadReader errorThreadReader=new ThreadReader(process.getErrorStream());
			Future<?> future=executorService.submit(threadReader);
			Future<?> futureError=executorService.submit(errorThreadReader);
			int ret = process.waitFor();
			if (ret == 0){
				futureError.get();
				future.cancel(true);
				String version=errorThreadReader.getResult();
				Matcher matcher=DOT_VERSION_PATTERN.matcher(version);
				if (matcher.matches()){
					return matcher.group(1);
				}
				return null;
			} else{
				return null;
			}
		} catch (IOException e) {
			throw new DotRuntimeException(e);
		} catch (InterruptedException e) {
			throw new DotRuntimeException(e);
		} catch (ExecutionException e) {
			throw new DotRuntimeException(e);
		}
	}
	
	static class ThreadReader implements Runnable{

		private final InputStream is;
		
		private BufferedReader bufReader;
		private StringBuilder builder=new StringBuilder();
		
		ThreadReader(InputStream is){
			this.is=is;
			bufReader = new BufferedReader(new InputStreamReader(is));
		}
		
		@Override
		public void run() {
			String line;
			try {
				while ((line = bufReader.readLine()) != null) {
					builder.append(line);
					builder.append(LINE_SEPARATOR);
				}
			} catch (IOException e) {
				throw new DotRuntimeException(e);
			} finally{
				FileUtils.close(bufReader);
				FileUtils.close(is);
			}
		}

		public String getResult(){
			return builder.toString();
		}
		
	}
	
	
	private String getDotExe(){
		if (WIN32){
			File file=getWin32GraphvizPath();
			if (file==null){
				return dot!=null?dot:"dot";
			}
			return file.getAbsolutePath();
		} else{
			return dot!=null?dot:"dot";
		}
	}

	private File getWin32GraphvizPath(){
		String programFiles=System.getenv("ProgramFiles");
		File programFilesPath=new File(programFiles);
		File graphvizDirecroty=getWin32GraphvizDirectoryFromProgramFiles(programFilesPath);
		if (graphvizDirecroty==null){
			graphvizDirecroty=getWin32GraphvizDirectory(programFiles, programFilesPath);
		}
		if (graphvizDirecroty!=null){
			return new File(graphvizDirecroty, "bin"+System.getProperty("file.separator")+"dot.exe");
		}
		return null;
	}

	
	private File getWin32GraphvizDirectory(String programFiles, File programFilesPath){
		File programFilesPathParent=programFilesPath.getParentFile();
		File[] programFilesOthers=programFilesPathParent.listFiles(f->{
			return f.isDirectory()&&f.getAbsolutePath().startsWith(programFiles+" ");
		});
		if (programFilesOthers==null) {
			return null;
		}
		for(File programFilesOther:programFilesOthers){
			File graphvizDirecroty=getWin32GraphvizDirectoryFromProgramFiles(programFilesOther);
			if (graphvizDirecroty!=null){
				return graphvizDirecroty;
			}
		}
		return null;
	}
	
	private File getWin32GraphvizDirectoryFromProgramFiles(File programFilesPath){
		File[] files=programFilesPath.listFiles(f->{
			return f.isDirectory()&&f.getName().startsWith("Graphviz");
		});
		if (CommonUtils.isEmpty(files)){
			return null;
		}
		Arrays.sort(files);
		return files[files.length-1];
	}

	/**
	 * @return the dot
	 */
	public String getDot() {
		return dot;
	}

	/**
	 * @param dot the dot to set
	 */
	public void setDot(String dot) {
		this.dot = dot;
	}

	/**
	 * @return the dir
	 */
	public File getDir() {
		return dir;
	}

	/**
	 * @param dir the dir to set
	 */
	public void setDir(File dir) {
		this.dir = dir;
	}

	/**
	 * @return the outputFormat
	 */
	public OutputFormat getOutputFormat() {
		return outputFormat;
	}

	/**
	 * @param outputFormat the outputFormat to set
	 */
	public void setOutputFormat(OutputFormat outputFormat) {
		this.outputFormat = outputFormat;
	}
	
}
