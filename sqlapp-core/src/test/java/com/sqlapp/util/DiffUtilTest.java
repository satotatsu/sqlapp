/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.util;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;

/**
 * Diffクラステスト
 * 
 * @author 竜夫
 *
 */
public class DiffUtilTest {

	@Test
	public void testDelta() {
		final List<String> original = getResource("originalFile.txt");
		final List<String> revised = getResource("revisedFile.txt");

		// Compute diff. Get the Patch object. Patch is the container for
		// computed deltas.
		final Patch<String> patch = DiffUtils.diff(original, revised);
		for (final Delta<?> delta : patch.getDeltas()) {
			System.out.println(delta);
		}
	}

	@Test
	public void testDelta2() {
		final List<String> original = getResource("originalFile.txt");
		final List<String> revised = getResource("revisedFile.txt");

		// Compute diff. Get the Patch object. Patch is the container for
		// computed deltas.
		final Patch<String> patch = DiffUtils.diff(original, revised);
		for (final Delta<?> delta : patch.getDeltas()) {
			System.out.println(DeltaUtils.toString(delta));
		}
	}

	protected List<String> getResource(final String fileName) {
		final InputStream is = FileUtils.getInputStream(this.getClass(), fileName);
		return FileUtils.readTextList(is, "utf8");
	}
}
