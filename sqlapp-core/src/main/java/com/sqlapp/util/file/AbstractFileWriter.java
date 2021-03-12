/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.util.function.Supplier;

import com.univocity.parsers.common.AbstractWriter;

public abstract class AbstractFileWriter<T extends AbstractWriter<?>> implements AutoCloseable{

	private final T writer;

	public AbstractFileWriter(final Supplier<T> writer) {
		this.writer = writer.get();
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
	
}
