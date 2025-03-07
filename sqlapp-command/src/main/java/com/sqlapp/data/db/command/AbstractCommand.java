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

import java.io.PrintStream;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ExceptionHandler;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.ToRuntimeExceptionHandler;
import com.sqlapp.util.YamlConverter;

public abstract class AbstractCommand implements Runnable {
	protected static final Logger logger = LogManager
			.getLogger(AbstractCommand.class);
	private ExceptionHandler exceptionHandler = new ToRuntimeExceptionHandler();
	/**
	 * スキーマオブジェクトの変換用ハンドラー
	 */
	private ConvertHandler convertHandler = new SimpleConvertHandler();

	private PrintStream out=System.out;

	private PrintStream err=System.err;
	
	private Map<String,Object> context=CommonUtils.linkedMap();

	private Converters converters=Converters.getDefault();
	
	protected JsonConverter createJsonConverter(){
		JsonConverter jsonConverter=new JsonConverter();
		jsonConverter.setIndentOutput(true);
		return jsonConverter;
	}

	protected YamlConverter createYamlConverter(){
		YamlConverter jsonConverter=new YamlConverter();
		jsonConverter.setIndentOutput(true);
		return jsonConverter;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		initialize();
		doRun();
	}

	protected void initialize(){
		initializeContext();
	}

	protected void initializeContext(){
		context.putAll(System.getenv());
		System.getProperties().forEach((k,v)->{
			context.put(converters.convertString(k), converters.convertString(v));
		});
	}

	protected abstract void doRun();

	/**
	 * @return the out
	 */
	private PrintStream getOut() {
		return out;
	}

	protected void println(Object obj){
		if (obj!=null){
			this.getOut().println(obj.toString());
		}
	}

	/**
	 * @return the err
	 */
	protected PrintStream getErr() {
		return err;
	}

	/**
	 * @return the exceptionHandler
	 */
	public ExceptionHandler getExceptionHandler() {
		return exceptionHandler;
	}

	/**
	 * @param exceptionHandler
	 *            the exceptionHandler to set
	 */
	public void setExceptionHandler(ExceptionHandler exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
	}

	/**
	 * @return the convertHandler
	 */
	public ConvertHandler getConvertHandler() {
		return convertHandler;
	}

	/**
	 * @return the context
	 */
	public Map<String, Object> getContext() {
		return context;
	}

	/**
	 * @param convertHandler
	 *            the convertHandler to set
	 */
	public void setConvertHandler(ConvertHandler convertHandler) {
		this.convertHandler = convertHandler;
	}

}
