/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Year;

import org.junit.jupiter.api.Test;

class YearConverterTest {

	@Test
	void test() {
		final YearConverter converter=new YearConverter().setParseFormats("yyyy-MM-dd'T'HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ssZZ", "yyyy")
				.setFormat("yyyy");
		final Year p=Year.of(2021);
		assertEquals("2021", p.toString());
		final Year p2=converter.convertObject(p.toString());
		assertEquals(p, p2);
	}

}
