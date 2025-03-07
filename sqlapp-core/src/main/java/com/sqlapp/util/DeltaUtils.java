/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.util;

import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.ChangeDelta;
import com.github.difflib.patch.DeleteDelta;
import com.github.difflib.patch.InsertDelta;
import com.sqlapp.data.schemas.DbCommonObject;

public class DeltaUtils {

	/**
	 * 差分を行数表示形式の文字列にします。
	 * 
	 * @param delta 差分
	 * @return 行数表示形式の文字列
	 */
	public static String toStringLine(AbstractDelta<?> delta) {
		if (delta == null) {
			return "";
		}
		return toStringLineNumber(delta, 0);
	}

	/**
	 * 差分を行数表示形式の文字列にします。
	 * 
	 * @param delta 差分
	 * @return 行数表示形式の文字列
	 */
	public static String toStringLineNumber(AbstractDelta<?> delta, int size) {
		if (delta == null) {
			return "";
		}
		StringBuilder builder = getBuilder2(delta);
		int scale = scale(size);
		String zero = CommonUtils.getString('0', scale);
		boolean first = true;
		if ((delta instanceof DeleteDelta) || (delta instanceof ChangeDelta)) {
			int pos = delta.getSource().getPosition() + 1;
			for (Object obj : delta.getSource().getLines()) {
				if (!first) {
					builder.append("\n");
				}
				first = false;
				builder.append("-");
				builder.append(CommonUtils.right((zero + (pos++)), scale));
				builder.append(":");
				builder.append(obj);
			}
		}
		if ((delta instanceof InsertDelta) || (delta instanceof ChangeDelta)) {
			int pos = delta.getTarget().getPosition() + 1;
			for (Object obj : delta.getTarget().getLines()) {
				if (!first) {
					builder.append("\n");
				}
				first = false;
				builder.append("+");
				builder.append(CommonUtils.right((zero + (pos++)), scale));
				builder.append(":");
				if (obj instanceof DbCommonObject) {
					builder.append(((DbCommonObject<?>) obj).toStringSimple());
				} else {
					builder.append(obj);

				}
			}
		}
		return builder.toString();
	}

	private static int scale(int size) {
		size = Math.abs(size);
		if (size == 0) {
			return 0;
		}
		int ret = (int) Math.log10(size) + 1;
		if (ret < 3) {
			return 3;
		}
		return ret;
	}

	private static StringBuilder getBuilder2(AbstractDelta<?> delta) {
		StringBuilder builder = new StringBuilder(256);
		return builder;
	}

	/**
	 * 差分をユニファイド形式の文字列にします。
	 * 
	 * @param delta 差分
	 * @return ユニファイド形式の文字列
	 */
	public static String toString(AbstractDelta<?> delta) {
		if (delta == null) {
			return "";
		}
		StringBuilder builder = getBuilder(delta);
		if ((delta instanceof DeleteDelta) || (delta instanceof ChangeDelta)) {
			for (Object obj : delta.getSource().getLines()) {
				builder.append("\n");
				builder.append("-");
				builder.append(obj);
			}
		}
		if ((delta instanceof InsertDelta) || (delta instanceof ChangeDelta)) {
			for (Object obj : delta.getTarget().getLines()) {
				builder.append("\n");
				builder.append("+");
				builder.append(obj);
			}
		}
		return builder.toString();
	}

	private static StringBuilder getBuilder(AbstractDelta<?> delta) {
		StringBuilder builder = new StringBuilder(256);
		builder.append("@@ -");
		builder.append(delta.getSource().getPosition());
		builder.append(",");
		builder.append(delta.getSource().last());
		builder.append(" +");
		builder.append(delta.getTarget().getPosition());
		builder.append(",");
		builder.append(delta.getTarget().last());
		builder.append(" @@");
		return builder;
	}

}
