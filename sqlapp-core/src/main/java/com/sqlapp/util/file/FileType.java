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
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import com.univocity.parsers.common.CommonParserSettings;
import com.univocity.parsers.common.CommonWriterSettings;
import com.univocity.parsers.common.Format;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriterSettings;
import com.univocity.parsers.fixed.FixedWidthParserSettings;
import com.univocity.parsers.fixed.FixedWidthWriterSettings;
import com.univocity.parsers.tsv.TsvParserSettings;
import com.univocity.parsers.tsv.TsvWriterSettings;

public enum FileType {
	CSV(){
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final Reader reader, final Consumer<T> settingsConsumer) {
			return new CsvParser(reader, (Consumer<CsvParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final File file, final Charset charset, final Consumer<T> settingsConsumer) {
			return new CsvParser(file, charset, (Consumer<CsvParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final InputStream is, final Charset charset, final Consumer<T> settingsConsumer) {
			return new CsvParser(is, charset, (Consumer<CsvParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final File file, final String charset, final Consumer<T> settingsConsumer) {
			return new CsvParser(file, charset, (Consumer<CsvParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final InputStream is, final String charset, final Consumer<T> settingsConsumer) {
			return new CsvParser(is, charset, (Consumer<CsvParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> CsvWriter createWriter(final Writer writer, final Consumer<T> settingsConsumer) {
			return new CsvWriter(writer, (Consumer<CsvWriterSettings>)settingsConsumer);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> CsvWriter createWriter(final File file, final Charset charset, final Consumer<T> settingsConsumer) {
			return new CsvWriter(file, charset, (Consumer<CsvWriterSettings>)settingsConsumer);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> CsvWriter createWriter(final OutputStream os, final Charset charset, final Consumer<T> settingsConsumer) {
			return new CsvWriter(os, charset, (Consumer<CsvWriterSettings>)settingsConsumer);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> CsvWriter createWriter(final File file, final String charset, final Consumer<T> settingsConsumer) {
			return new CsvWriter(file, charset, (Consumer<CsvWriterSettings>)settingsConsumer);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> CsvWriter createWriter(final OutputStream os, final String charset, final Consumer<T> settingsConsumer) {
			return new CsvWriter(os, charset, (Consumer<CsvWriterSettings>)settingsConsumer);
		}
	},
	SSV(){
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final Reader reader, final Consumer<T> settingsConsumer) {
			return new CsvParser(reader, s->{
				s.getFormat().setDelimiter(";");
				((Consumer<CsvParserSettings>)settingsConsumer).accept(s);
			});
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final File file, final Charset charset, final Consumer<T> settingsConsumer) {
			return new CsvParser(file, charset, s->{
				s.getFormat().setDelimiter(";");
				((Consumer<CsvParserSettings>)settingsConsumer).accept(s);
			});
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final InputStream is, final Charset charset, final Consumer<T> settingsConsumer) {
			return new CsvParser(is, charset, s->{
				s.getFormat().setDelimiter(";");
				((Consumer<CsvParserSettings>)settingsConsumer).accept(s);
			});
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final File file, final String charset, final Consumer<T> settingsConsumer) {
			return new CsvParser(file, charset, s->{
				s.getFormat().setDelimiter(";");
				((Consumer<CsvParserSettings>)settingsConsumer).accept(s);
			});
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final InputStream is, final String charset, final Consumer<T> settingsConsumer) {
			return new CsvParser(is, charset, s->{
				s.getFormat().setDelimiter(";");
				((Consumer<CsvParserSettings>)settingsConsumer).accept(s);
			});
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> CsvWriter createWriter(final Writer writer, final Consumer<T> settingsConsumer) {
			return new CsvWriter(writer, s->{
				s.getFormat().setDelimiter(";");
				((Consumer<CsvWriterSettings>)settingsConsumer).accept(s);
			});
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> CsvWriter createWriter(final File file, final Charset charset, final Consumer<T> settingsConsumer) {
			return new CsvWriter(file, charset, s->{
				s.getFormat().setDelimiter(";");
				((Consumer<CsvWriterSettings>)settingsConsumer).accept(s);
			});
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> CsvWriter createWriter(final OutputStream os, final Charset charset, final Consumer<T> settingsConsumer) {
			return new CsvWriter(os, charset, s->{
				s.getFormat().setDelimiter(";");
				((Consumer<CsvWriterSettings>)settingsConsumer).accept(s);
			});
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> CsvWriter createWriter(final File file, final String charset, final Consumer<T> settingsConsumer) {
			return new CsvWriter(file, charset, s->{
				s.getFormat().setDelimiter(";");
				((Consumer<CsvWriterSettings>)settingsConsumer).accept(s);
			});
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> CsvWriter createWriter(final OutputStream os, final String charset, final Consumer<T> settingsConsumer) {
			return new CsvWriter(os, charset, s->{
				s.getFormat().setDelimiter(";");
				((Consumer<CsvWriterSettings>)settingsConsumer).accept(s);
			});
		}
	},
	TSV(){
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> TsvParser createParser(final Reader reader, final Consumer<T> settingsConsumer) {
			return new TsvParser(reader, (Consumer<TsvParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> TsvParser createParser(final File file, final Charset charset, final Consumer<T> settingsConsumer) {
			return new TsvParser(file, charset, (Consumer<TsvParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> TsvParser createParser(final InputStream is, final Charset charset, final Consumer<T> settingsConsumer) {
			return new TsvParser(is, charset, (Consumer<TsvParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> TsvParser createParser(final File file, final String charset, final Consumer<T> settingsConsumer) {
			return new TsvParser(file, charset, (Consumer<TsvParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> TsvParser createParser(final InputStream is, final String charset, final Consumer<T> settingsConsumer) {
			return new TsvParser(is, charset, (Consumer<TsvParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> TsvWriter createWriter(final Writer writer, final Consumer<T> settingsConsumer) {
			return new TsvWriter(writer, (Consumer<TsvWriterSettings>)settingsConsumer);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> TsvWriter createWriter(final File file, final Charset charset, final Consumer<T> settingsConsumer) {
			return new TsvWriter(file, charset, (Consumer<TsvWriterSettings>)settingsConsumer);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> TsvWriter createWriter(final OutputStream os, final Charset charset, final Consumer<T> settingsConsumer) {
			return new TsvWriter(os, charset, (Consumer<TsvWriterSettings>)settingsConsumer);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> TsvWriter createWriter(final File file, final String charset, final Consumer<T> settingsConsumer) {
			return new TsvWriter(file, charset, (Consumer<TsvWriterSettings>)settingsConsumer);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> TsvWriter createWriter(final OutputStream os, final String charset, final Consumer<T> settingsConsumer) {
			return new TsvWriter(os, charset, (Consumer<TsvWriterSettings>)settingsConsumer);
		}
	},
	FIXED_WIDTH(){
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> FixedWidthParser createParser(final Reader reader, final Consumer<T> setting) {
			return new FixedWidthParser(reader, (Consumer<FixedWidthParserSettings>)setting);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> FixedWidthParser createParser(final File file, final Charset charset, final Consumer<T> settingsConsumer) {
			return new FixedWidthParser(file, charset, (Consumer<FixedWidthParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> FixedWidthParser createParser(final InputStream is, final Charset charset, final Consumer<T> settingsConsumer) {
			return new FixedWidthParser(is, charset, (Consumer<FixedWidthParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> FixedWidthParser createParser(final File file, final String charset, final Consumer<T> settingsConsumer) {
			return new FixedWidthParser(file, charset, (Consumer<FixedWidthParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> FixedWidthParser createParser(final InputStream is, final String charset, final Consumer<T> settingsConsumer) {
			return new FixedWidthParser(is, charset, (Consumer<FixedWidthParserSettings>)settingsConsumer);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> FixedWidthWriter createWriter(final Writer writer, final Consumer<T> settingsConsumer) {
			return new FixedWidthWriter(writer, (Consumer<FixedWidthWriterSettings>)settingsConsumer);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> FixedWidthWriter createWriter(final File file, final Charset charset, final Consumer<T> settingsConsumer) {
			return new FixedWidthWriter(file, charset, (Consumer<FixedWidthWriterSettings>)settingsConsumer);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> FixedWidthWriter createWriter(final OutputStream os, final Charset charset, final Consumer<T> settingsConsumer) {
			return new FixedWidthWriter(os, charset, (Consumer<FixedWidthWriterSettings>)settingsConsumer);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> FixedWidthWriter createWriter(final File file, final String charset, final Consumer<T> settingsConsumer) {
			return new FixedWidthWriter(file, charset, (Consumer<FixedWidthWriterSettings>)settingsConsumer);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> FixedWidthWriter createWriter(final OutputStream os, final String charset, final Consumer<T> settingsConsumer) {
			return new FixedWidthWriter(os, charset, (Consumer<FixedWidthWriterSettings>)settingsConsumer);
		}
	},
	;

	public <T extends CommonParserSettings<? extends Format>> AbstractFileParser<?,?> createParser(final Reader reader, final Consumer<T> setting) {
		return null;
	}

	public <T extends CommonParserSettings<? extends Format>> AbstractFileParser<?,?> createParser(final File file, final Charset charset, final Consumer<T> settingsConsumer) {
		return null;
	}
	
	public <T extends CommonParserSettings<? extends Format>> AbstractFileParser<?,?> createParser(final InputStream is, final Charset charset, final Consumer<T> settingsConsumer) {
		return null;
	}

	public <T extends CommonParserSettings<? extends Format>> AbstractFileParser<?,?> createParser(final File file, final String charset, final Consumer<T> settingsConsumer) {
		return null;
	}

	public <T extends CommonParserSettings<? extends Format>> AbstractFileParser<?,?> createParser(final InputStream is, final String charset, final Consumer<T> settingsConsumer) {
		return null;
	}
	
	public <T extends CommonWriterSettings<? extends Format>> AbstractFileWriter<?,?> createWriter(final Writer writer, final Consumer<T> setting) {
		return null;
	}

	public <T extends CommonWriterSettings<? extends Format>> AbstractFileWriter<?,?> createWriter(final File file, final Charset charset, final Consumer<T> settingsConsumer) {
		return null;
	}
	
	public <T extends CommonWriterSettings<? extends Format>> AbstractFileWriter<?,?> createWriter(final OutputStream os, final Charset charset, final Consumer<T> settingsConsumer) {
		return null;
	}

	public <T extends CommonWriterSettings<? extends Format>> AbstractFileWriter<?,?> createWriter(final File file, final String charset, final Consumer<T> settingsConsumer) {
		return null;
	}

	public <T extends CommonWriterSettings<? extends Format>> AbstractFileWriter<?,?> createWriter(final OutputStream os, final String charset, final Consumer<T> settingsConsumer) {
		return null;
	}

}
