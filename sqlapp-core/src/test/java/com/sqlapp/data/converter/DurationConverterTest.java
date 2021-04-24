/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class DurationConverterTest {

	@Test
	void test() {
		final DurationConverter converter=new DurationConverter();
		final Duration p=Duration.ofSeconds(253);
		assertEquals("PT4M13S", p.toString());
		final Duration p2=converter.convertObject(p.toString());
		assertEquals(p, p2);
	}

}
