/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.util.function.Consumer;
import java.util.function.Function;

import com.univocity.parsers.common.AbstractWriter;
import com.univocity.parsers.common.CommonWriterSettings;

public abstract class AbstractFileWriter<T extends AbstractWriter<?>, S extends CommonWriterSettings<?>> implements AutoCloseable{

	private final T writer;

	public AbstractFileWriter(final S settings, final Consumer<S> settingsConsumer, final Function<S,T> writerFunction) {
		initialize(settings);
		settingsConsumer.accept(settings);
		this.writer=writerFunction.apply(settings);
	}
	
	@Override
	public void close() {
		writer.close();
	}

	public void writeHeader(final String... arg) {
		this.writer.writeHeaders(arg);
	}


	public void writeRow(final String... arg)  {
		this.writer.writeRow(arg);
	}

	public void writeEmptyRow()  {
		this.writer.writeEmptyRow();
	}

	protected void initialize(final S settings) {
		settings.setMaxCharsPerColumn(8192);
	}
}
