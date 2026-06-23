/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-graphviz.
 *
 * sqlapp-graphviz is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-graphviz is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-graphviz.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.graphviz.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.graphviz.AbstractTest;

class GraphvizRendererTest2 extends AbstractTest {

	@Test
	void test() {
        ScriptEngineManager manager = new ScriptEngineManager();
        for (var factory : manager.getEngineFactories()) {
            System.out.println(factory.getEngineName() + " : " + factory.getNames());
        }

        ScriptEngine engine = manager.getEngineByName("nashorn");
        System.out.println(engine);
	}

}
