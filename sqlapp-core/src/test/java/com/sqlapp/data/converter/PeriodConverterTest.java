/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Period;

import org.junit.jupiter.api.Test;

class PeriodConverterTest {

	@Test
	void test() {
		final PeriodConverter converter=new PeriodConverter();
		final Period p=Period.of(2021, 4, 13);
		assertEquals("P2021Y4M13D", p.toString());
		final Period p2=converter.convertObject(p.toString());
		assertEquals(p, p2);
	}

}
