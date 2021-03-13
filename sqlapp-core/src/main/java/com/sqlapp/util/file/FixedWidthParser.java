/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util.file;

import java.util.function.Consumer;

import com.univocity.parsers.fixed.FixedWidthParserSettings;

public class FixedWidthParser extends AbstractFileParser<com.univocity.parsers.fixed.FixedWidthParser, FixedWidthParserSettings>{

	public FixedWidthParser(final Consumer<FixedWidthParserSettings> settingConsumer) {
		super(new FixedWidthParserSettings(), settingConsumer, (settings)->{
			return new com.univocity.parsers.fixed.FixedWidthParser(settings);
		});
	}

}
