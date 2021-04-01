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
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import com.univocity.parsers.tsv.TsvParserSettings;

public class TsvParser extends AbstractFileParser<com.univocity.parsers.tsv.TsvParser, TsvParserSettings>{

	public TsvParser(final Reader reader, final Consumer<TsvParserSettings> settingsConsumer) {
		super(new TsvParserSettings(), settingsConsumer, (settings)->{
			return new com.univocity.parsers.tsv.TsvParser(settings);
		}, reader);
	}

	public TsvParser(final File file, final Charset charset, final Consumer<TsvParserSettings> settingsConsumer) {
		super(new TsvParserSettings(), settingsConsumer, (settings)->{
			return new com.univocity.parsers.tsv.TsvParser(settings);
		}, file, charset);
	}
	
	public TsvParser(final InputStream is, final Charset charset, final Consumer<TsvParserSettings> settingsConsumer) {
		super(new TsvParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.tsv.TsvParser(settings);
		}, is, charset);
	}

	public TsvParser(final File file, final String charset, final Consumer<TsvParserSettings> settingsConsumer) {
		super(new TsvParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.tsv.TsvParser(settings);
		}, file, charset);
	}

	public TsvParser(final InputStream is, final String charset, final Consumer<TsvParserSettings> settingsConsumer) {
		super(new TsvParserSettings(), settingsConsumer, settings->{
			return new com.univocity.parsers.tsv.TsvParser(settings);
		}, is, charset);
	}
}
