package com.sqlapp.elk.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SVGTextBuilderTest {

	@Test
	void test() {
		String text = "FK_NAME\nCASCADE=(UPD,DEL)\nVIRTURL";
		String[] args = text.split("\n");
		SVGTextBuilder builder = new SVGTextBuilder(args);
		String expect = """
				FK_NAME
				<tspan dx="-3em" dy="1.2em">CASCADE=(UPD,DEL)</tspan>
				<tspan dx="-3em" dy="2.4em">VIRTURL</tspan>""";
		assertEquals(expect, builder.getText());
	}

}
