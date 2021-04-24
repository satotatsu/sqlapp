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
		final Duration p3=converter.convertObject("Interval 4:13 minute to second");
		assertEquals(p3, p2);
		final Duration p4=converter.convertObject("Interval '10 12:4:13' day to second");
		assertEquals("PT12H4M13S", p4.toString());//日付は無視される
		final Duration p5=converter.convertObject("Interval '12:4:13' hour to second");
		assertEquals(p5, p4);
	}

}
