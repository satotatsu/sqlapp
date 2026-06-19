package com.sqlapp.graphviz.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.graphviz.AbstractTest;

class GraphvizRendererTest extends AbstractTest {

	@Test
	void test() {
		String dot = getResource("graph.dot");
		String expected = getResource("graph.svg");
		GraphvizRenderer graphvizRenderer = new GraphvizRenderer();
		String result = graphvizRenderer.render(dot).replace("\r\n", "\n").trim();
		assertEquals(expected, result);
	}

}
