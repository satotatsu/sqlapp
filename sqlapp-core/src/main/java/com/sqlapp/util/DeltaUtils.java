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

import com.sqlapp.data.schemas.DbCommonObject;

import difflib.ChangeDelta;
import difflib.DeleteDelta;
import difflib.Delta;
import difflib.InsertDelta;

public class DeltaUtils {

	/**
	 * 差分を行数表示形式の文字列にします。
	 * 
	 * @param delta
	 *            差分
	 * @return 行数表示形式の文字列
	 */
	public static String toStringLine(Delta<?> delta) {
		if (delta == null) {
			return "";
		}
		return toStringLineNumber(delta, 0);
	}
	
	/**
	 * 差分を行数表示形式の文字列にします。
	 * 
	 * @param delta
	 *            差分
	 * @return 行数表示形式の文字列
	 */
	public static String toStringLineNumber(Delta<?> delta, int size) {
		if (delta == null) {
			return "";
		}
		StringBuilder builder = getBuilder2(delta);
		int scale = scale(size);
		String zero = CommonUtils.getString('0', scale);
		boolean first = true;
		if ((delta instanceof DeleteDelta) || (delta instanceof ChangeDelta)) {
			int pos = delta.getOriginal().getPosition() + 1;
			for (Object obj : delta.getOriginal().getLines()) {
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
			int pos = delta.getRevised().getPosition() + 1;
			for (Object obj : delta.getRevised().getLines()) {
				if (!first) {
					builder.append("\n");
				}
				first = false;
				builder.append("+");
				builder.append(CommonUtils.right((zero + (pos++)), scale));
				builder.append(":");
				if (obj instanceof DbCommonObject){
					builder.append(((DbCommonObject<?>)obj).toStringSimple());
				} else{
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

	private static StringBuilder getBuilder2(Delta<?> delta) {
		StringBuilder builder = new StringBuilder(256);
		return builder;
	}

	/**
	 * 差分をユニファイド形式の文字列にします。
	 * 
	 * @param delta
	 *            差分
	 * @return ユニファイド形式の文字列
	 */
	public static String toString(Delta<?> delta) {
		if (delta == null) {
			return "";
		}
		StringBuilder builder = getBuilder(delta);
		if ((delta instanceof DeleteDelta) || (delta instanceof ChangeDelta)) {
			for (Object obj : delta.getOriginal().getLines()) {
				builder.append("\n");
				builder.append("-");
				builder.append(obj);
			}
		}
		if ((delta instanceof InsertDelta) || (delta instanceof ChangeDelta)) {
			for (Object obj : delta.getRevised().getLines()) {
				builder.append("\n");
				builder.append("+");
				builder.append(obj);
			}
		}
		return builder.toString();
	}

	private static StringBuilder getBuilder(Delta<?> delta) {
		StringBuilder builder = new StringBuilder(256);
		builder.append("@@ -");
		builder.append(delta.getOriginal().getPosition());
		builder.append(",");
		builder.append(delta.getOriginal().last());
		builder.append(" +");
		builder.append(delta.getRevised().getPosition());
		builder.append(",");
		builder.append(delta.getRevised().last());
		builder.append(" @@");
		return builder;
	}

}
