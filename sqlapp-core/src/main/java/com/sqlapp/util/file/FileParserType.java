/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.io.Writer;
import java.util.function.Consumer;

import com.sqlapp.data.schemas.Table;
import com.univocity.parsers.common.CommonParserSettings;
import com.univocity.parsers.common.CommonWriterSettings;
import com.univocity.parsers.common.Format;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriterSettings;
import com.univocity.parsers.fixed.FixedWidthParserSettings;
import com.univocity.parsers.tsv.TsvParserSettings;

public enum FileParserType {
	CSV(){
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final Consumer<T> setting) {
			return new CsvParser((Consumer<CsvParserSettings>)setting);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final Table table, final Consumer<T> setting) {
			return new CsvParser(table, (Consumer<CsvParserSettings>)setting);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonWriterSettings<? extends Format>> CsvWriter createWriter(final Writer writer, final Consumer<T> setting) {
			return new CsvWriter(writer, (Consumer<CsvWriterSettings>)setting);
		}
	},
	SSV(){
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final Consumer<T> setting) {
			return new CsvParser(s->{
				s.getFormat().setDelimiter(";");
				((Consumer<CsvParserSettings>)setting).accept(s);
			});
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> CsvParser createParser(final Table table, final Consumer<T> setting) {
			return new CsvParser(table, s->{
				s.getFormat().setDelimiter(";");
				((Consumer<CsvParserSettings>)setting).accept(s);
			});
		}
	},
	TSV(){
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> TsvParser createParser(final Consumer<T> setting) {
			return new TsvParser((Consumer<TsvParserSettings>)setting);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> TsvParser createParser(final Table table, final Consumer<T> setting) {
			return new TsvParser(table, (Consumer<TsvParserSettings>)setting);
		}
	},
	FIXED_WIDTH(){
		@SuppressWarnings("unchecked")
		@Override
		public <T extends CommonParserSettings<? extends Format>> FixedWidthParser createParser(final Consumer<T> setting) {
			return new FixedWidthParser((Consumer<FixedWidthParserSettings>)setting);
		}
	},
	;

	public <T extends CommonParserSettings<? extends Format>> AbstractFileParser<?> createParser(final Consumer<T> setting) {
		return null;
	}

	public <T extends CommonParserSettings<? extends Format>> AbstractFileParser<?> createParser(final Table table, final Consumer<T> setting) {
		return null;
	}

	public <T extends CommonWriterSettings<? extends Format>> AbstractFileWriter<?> createWriter(final Writer writer, final Consumer<T> setting) {
		return null;
	}


}
