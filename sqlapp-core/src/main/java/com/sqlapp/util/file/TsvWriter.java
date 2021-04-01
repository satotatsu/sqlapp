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
package com.sqlapp.util.file;

import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import com.univocity.parsers.tsv.TsvWriterSettings;

public class TsvWriter extends AbstractFileWriter<com.univocity.parsers.tsv.TsvWriter, TsvWriterSettings>{

	public TsvWriter(final Writer writer, final Consumer<TsvWriterSettings> settingsConsumer) {
		super(new TsvWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.tsv.TsvWriter(writer, settings);
		});
	}

	public TsvWriter(final File file, final Charset charset, final Consumer<TsvWriterSettings> settingsConsumer) {
		super(new TsvWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.tsv.TsvWriter(file, charset , settings);
		});
	}

	public TsvWriter(final File file, final String charset, final Consumer<TsvWriterSettings> settingsConsumer) {
		super(new TsvWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.tsv.TsvWriter(file, charset , settings);
		});
	}

	public TsvWriter(final OutputStream os, final Charset charset, final Consumer<TsvWriterSettings> settingsConsumer) {
		super(new TsvWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.tsv.TsvWriter(os, charset , settings);
		});
	}

	public TsvWriter(final OutputStream os, final String charset, final Consumer<TsvWriterSettings> settingsConsumer) {
		super(new TsvWriterSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.tsv.TsvWriter(os, charset , settings);
		});
	}

}
