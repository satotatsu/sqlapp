/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import com.univocity.parsers.fixed.FixedWidthWriterSettings;

public class FixedWidthWriter extends AbstractFileWriter<com.univocity.parsers.fixed.FixedWidthWriter, FixedWidthWriterSettings>{

	public FixedWidthWriter(final Writer writer, final Consumer<FixedWidthWriterSettings> settingsConsumer) {
		super(new FixedWidthWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.fixed.FixedWidthWriter(writer, settings);
		});
	}

	public FixedWidthWriter(final File file, final Charset charset, final Consumer<FixedWidthWriterSettings> settingsConsumer) {
		super(new FixedWidthWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.fixed.FixedWidthWriter(file, charset, settings);
		});
	}

	public FixedWidthWriter(final File file, final String charset, final Consumer<FixedWidthWriterSettings> settingsConsumer) {
		super(new FixedWidthWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.fixed.FixedWidthWriter(file, charset, settings);
		});
	}

	public FixedWidthWriter(final OutputStream os, final Charset charset, final Consumer<FixedWidthWriterSettings> settingsConsumer) {
		super(new FixedWidthWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.fixed.FixedWidthWriter(os, charset, settings);
		});
	}

	public FixedWidthWriter(final OutputStream os, final String charset, final Consumer<FixedWidthWriterSettings> settingsConsumer) {
		super(new FixedWidthWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.fixed.FixedWidthWriter(os, charset, settings);
		});
	}

}
