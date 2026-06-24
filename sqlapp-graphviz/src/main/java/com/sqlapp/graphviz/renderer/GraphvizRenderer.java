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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;

public class GraphvizRenderer {

	private OutputFormat outputFormat = OutputFormat.svg;

	public GraphvizRenderer() {
	}

	public String render(String graphVizText) {
		Format format = outputFormat.getNidiFormat();
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Graphviz.fromString(graphVizText).render(format).toOutputStream(out);
			String svg = out.toString("UTF-8");
			return svg;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void render(String graphVizText, String filename) {
		File file = new File(filename + "." + outputFormat.getExtension());
		render(graphVizText, file);
	}

	public File render(String graphVizText, File file) {
		try {
			Format format = outputFormat.getNidiFormat();
			Graphviz.fromString(graphVizText).render(format).toFile(file);
			return file;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @return the outputFormat
	 */
	public OutputFormat getOutputFormat() {
		return outputFormat;
	}

	/**
	 * @param outputFormat the outputFormat to set
	 */
	public void setOutputFormat(OutputFormat outputFormat) {
		this.outputFormat = outputFormat;
	}

}
