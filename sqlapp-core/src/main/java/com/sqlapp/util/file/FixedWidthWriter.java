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
