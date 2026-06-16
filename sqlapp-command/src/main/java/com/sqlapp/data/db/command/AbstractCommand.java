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
import java.time.LocalDateTime;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.properties.ConsoleOutputLevelProperty;
import com.sqlapp.data.db.command.properties.ContextProperty;
import com.sqlapp.data.db.command.properties.ConvertersProperty;
import com.sqlapp.jdbc.function.ExceptionRunnable;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ExceptionHandler;
import com.sqlapp.util.Java8DateUtils;
import com.sqlapp.util.ToRuntimeExceptionHandler;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AbstractCommand
		implements Runnable, ConsoleOutputLevelProperty, ConvertersProperty, ContextProperty {
	protected static final Logger logger = LogManager.getLogger(AbstractCommand.class);
	private ExceptionHandler exceptionHandler = new ToRuntimeExceptionHandler();
	/**
	 * スキーマオブジェクトの変換用ハンドラー
	 */
	private ConvertHandler convertHandler = new SimpleConvertHandler();

	private PrintStream out = System.out;

	private PrintStream err = System.err;

	private final Map<String, Object> context = CommonUtils.linkedMap();

	private Converters converters = Converters.getDefault();

	private ConsoleOutputLevel consoleOutputLevel = ConsoleOutputLevel.INFO;

	protected static final String LOG_SEPARATOR_START = "<<=============| ";
	protected static final String LOG_SEPARATOR_END = " |=============>>";
	protected static final String MESSAGE_SEPARATOR_START = "----| ";
	protected static final String MESSAGE_SEPARATOR_END = " |----";

	/**
	 * 処理を行い、例外を処理します
	 * 
	 * @param dataSource DataSource
	 * @param runnable   行う処理
	 */
	protected void execute(ExceptionRunnable runnable) {
		try {
			runnable.run();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			exceptionHandler.handle(e);
		}
	}

	/**
	 * @return the exceptionHandler
	 */
	protected ExceptionHandler getExceptionHandler() {
		return exceptionHandler;
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

	protected void initialize() {
		initializeContext();
	}

	protected void initializeContext() {
		context.putAll(System.getenv());
		System.getProperties().forEach((k, v) -> {
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

	/**
	 * @return the err
	 */
	protected PrintStream getErr() {
		return err;
	}

	protected void debug(Object obj) {
		if (this.getConsoleOutputLevel().compareTo(ConsoleOutputLevel.DEBUG) >= 0) {
			println(obj);
		}
	}

	protected void info(Object obj) {
		if (this.getConsoleOutputLevel().compareTo(ConsoleOutputLevel.INFO) >= 0) {
			println(obj);
		}
	}

	protected void debug(Object... args) {
		if (this.getConsoleOutputLevel().compareTo(ConsoleOutputLevel.DEBUG) >= 0) {
			println(args);
		}
	}

	protected void info(Object... args) {
		if (this.getConsoleOutputLevel().compareTo(ConsoleOutputLevel.INFO) >= 0) {
			println(args);
		}
	}

	protected void error(Object... args) {
		if (this.getConsoleOutputLevel().compareTo(ConsoleOutputLevel.ERROR) >= 0) {
			printError(args);
		}
	}

	protected void error(Throwable t, Object... args) {
		if (this.getConsoleOutputLevel().compareTo(ConsoleOutputLevel.ERROR) >= 0) {
			t.printStackTrace(this.getErr());
			printError(args);
		}
	}

	private void println(Object obj) {
		if (obj != null) {
			this.getOut().println(obj.toString());
		}
	}

	private void printError(Object obj) {
		if (obj != null) {
			this.getErr().println(obj.toString());
		}
	}

	private void println(Object... args) {
		StringBuilder builder = new StringBuilder();
		for (Object arg : args) {
			if (arg instanceof LocalDateTime) {
				builder.append(Java8DateUtils.format((LocalDateTime) arg, "yyyy-MM-dd HH:mm:ss"));
				continue;
			}
			builder.append(arg);
		}
		println(builder.toString());
	}

	private void printError(Object... args) {
		StringBuilder builder = new StringBuilder();
		for (Object arg : args) {
			if (arg instanceof LocalDateTime) {
				builder.append(Java8DateUtils.format((LocalDateTime) arg, "yyyy-MM-dd HH:mm:ss"));
				continue;
			}
			builder.append(arg);
		}
		printError(builder.toString());
	}

}
