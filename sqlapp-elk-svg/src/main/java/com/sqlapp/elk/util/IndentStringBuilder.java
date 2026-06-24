/**
 * Copyright (C) 2007-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.elk.util;

public class IndentStringBuilder {
	private StringBuilder builder = new StringBuilder();
	private String indent = "\t";;
	private int indentLevel = 0;

	public int getIndentLevel() {
		return indentLevel;
	}

	public void setIndentLevel(int indentLevel) {
		this.indentLevel = indentLevel;
	}

	public void append(Object value) {
		builder.append(value);
	}

	public void lineBreak() {
		builder.append("\n");
	}

	public void appendLine(Object value) {
		lineBreak();
		addIndent();
		append(value);
	}

	public void appendLine(Runnable runnable) {
		addIndentLevel(1);
		runnable.run();
		addIndentLevel(-1);
	}

	public void addIndentLevel(int i) {
		indentLevel = indentLevel + i;
		if (indentLevel < 0) {
			throw new RuntimeException("indent level=" + indentLevel);
		}
	}

	private void addIndent() {
		for (int i = 0; i < indentLevel; i++) {
			builder.append(indent);
		}
	}

	public String toString() {
		return builder.toString();
	}
}
