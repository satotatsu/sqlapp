/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.elk.util;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.TextWidthUtils;

public class MaxLengthCalculator {
	private double current;
	private double fontSize;

	public MaxLengthCalculator(double min, double fontSize) {
		this.current = min;
		this.fontSize = fontSize;
	}

	public void add(String value) {
		if (CommonUtils.isEmpty(value)) {
			return;
		}
		double len = TextWidthUtils.estimateTextWidth(value, fontSize);
		if (len > current) {
			current = len;
		}
	}

	public void add(String... args) {
		if (args == null) {
			return;
		}
		for (String arg : args) {
			add(arg);
		}
	}

	public double calc() {
		return current;
	}
}
