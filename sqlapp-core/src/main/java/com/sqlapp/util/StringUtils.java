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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文字列操作ユーティリティ
 * 
 * @author SATOH
 * 
 */
public final class StringUtils {
	private StringUtils() {
	};

	public static String transposition(final String value){
		return transposition(value, " ");
	}

	public static String transposition(final String value, final String space){
		final String[] args=value.split("\n");
		int maxCodePointCount=0;
		for(final String arg:args){
			final int val=arg.codePointCount(0, arg.length());
			if (val>maxCodePointCount){
				maxCodePointCount=val;
			}
		}
		final StringBuilder builder=new StringBuilder();
		for(int i=0;i<maxCodePointCount;i++){
			for(int j=0;j<args.length;j++){
				final String arg=args[j];
				if (i<arg.codePointCount(0, arg.length())){
					final int codePoint=arg.codePointAt(i);
					builder.appendCodePoint(codePoint);
				} else{
					builder.append(space);
				}
			}
			builder.append("\n");
		}
		return builder.substring(0, builder.length()-1);
	}
	
	/**
	 * アンダースコア記法からキャメル記法への変換
	 * 
	 * @param val
	 *            変換対象の文字列
	 * @return 変換後の文字列
	 * @deprecated 
	 */
	@Deprecated
	public static String snakeToCamelCase(final String val) {
		return snakeToCamel(val);
	}

	/**
	 * アンダースコア記法からキャメル記法への変換
	 * 
	 * @param val
	 *            変換対象の文字列
	 * @return 変換後の文字列
	 */
	public static String snakeToCamel(final String val) {
		if (CommonUtils.isEmpty(val)) {
			return val;
		}
		final String[] splits = val.split("_");
		final StringBuilder builder = new StringBuilder(val.length() - splits.length
				+ 1);
		if (CommonUtils.isEmpty(splits)) {
			return "";
		}
		builder.append(splits[0].toLowerCase());
		for (int i = 1; i < splits.length; i++) {
			if (splits[i].length()>0) {
				builder.append(splits[i].substring(0, 1).toUpperCase());
			}
			if (splits[i].length()>1) {
				builder.append(splits[i].substring(1).toLowerCase());
			}
		}
		return builder.toString();
	}

	/**
	 * キャメル記法からアンダースコア記法への変換
	 * 
	 * @param val
	 *            変換対象の文字列
	 * @return 変換後の文字列
	 * @deprecated
	 */
	@Deprecated
	public static String camelToSnakeCase(final String val) {
		return camelToSnake(val);
	}

	/**
	 * キャメル記法からアンダースコア記法への変換
	 * 
	 * @param val
	 *            変換対象の文字列
	 * @return 変換後の文字列
	 */
	public static String camelToSnake(final String val) {
		final String camel = pascalToUnderscore(camelToPascal(val));
		return camel;
	}

	/**
	 * キャメル記法からパスカル記法への変換
	 * 
	 * @param val
	 *            変換対象の文字列
	 * @return 変換後の文字列
	 */
	public static String camelToPascal(final String val) {
		if (isEmpty(val)) {
			return val;
		}
		if (val.length() == 1) {
			return val.toUpperCase();
		}
		return val.substring(0, 1).toUpperCase() + val.substring(1);
	}

	/**
	 * パスカル記法からアンダースコア記法への変換
	 * 
	 * @param val
	 *            変換対象の文字列
	 * @return 変換後の文字列
	 */
	public static String pascalToCamel(final String val) {
		if (isEmpty(val)) {
			return val;
		}
		if (val.length() == 1) {
			return val;
		}
		return val.substring(0, 1).toLowerCase() + val.substring(1);
	}

	/**
	 * アンダースコア記法からスネークケース記法へ変換します
	 * 
	 * @param val
	 *            変換対象の文字列
	 * @return 変換後の文字列
	 */
	public static String snakeToPascal(final String val) {
		if (isEmpty(val)) {
			return val;
		}
		final String[] splits = val.split("_");
		if (CommonUtils.isEmpty(splits)) {
			return "";
		}
		final StringBuilder builder = new StringBuilder(val.length() - splits.length
				+ 1);
		if (splits[0].length()>0) {
			builder.append(splits[0].substring(0, 1).toUpperCase());
		}
		if (splits[0].length()>1) {
			builder.append(splits[0].substring(1).toLowerCase());
		}
		for (int i = 1; i < splits.length; i++) {
			if (splits[i].length()>0) {
				builder.append(splits[i].substring(0, 1).toUpperCase());
			}
			if (splits[i].length()>1) {
				builder.append(splits[i].substring(1).toLowerCase());
			}
		}
		return builder.toString();
	}

	/**
	 * 先頭を大文字化します
	 * 
	 * @param text
	 */
	public static String capitalize(final String text) {
		if ((text == null) || (text.length() == 0)) {
			return text;
		}
		return Character.toTitleCase(text.charAt(0)) + text.substring(1);
	}

	/**
	 * 先頭を小文字化します
	 * 
	 * @param text
	 */
	public static String uncapitalize(final String text) {
		if ((text == null) || (text.length() == 0)) {
			return text;
		}
		return Character.toLowerCase(text.charAt(0)) + text.substring(1);
	}

	/**
	 * パスカル記法の正規表現
	 */
	private static final Pattern PASCAL_PATTERN = Pattern
			.compile("[A-Z]+[^a-z]*[a-z0-9]*");

	private static final Map<String, SoftReference<String>> PASCAL_TO_UNDERSCORE_CACHE = new HashMap<String, SoftReference<String>>();

	/**
	 * パスカル記法からアンダースコア記法へ変換します。
	 * 
	 * @param val
	 *            変換対象の文字列
	 * @return 変換後の文字列
	 */
	public static String pascalToUnderscore(final String val) {
		if (isEmpty(val)) {
			return val;
		}
		if (val.length() == 1) {
			return val;
		}
		SoftReference<String> ref=PASCAL_TO_UNDERSCORE_CACHE.get(val);
		if (ref!=null){
			final String ret=ref.get();
			if (ret!=null) {
				return ret;
			} else{
				synchronized(PASCAL_TO_UNDERSCORE_CACHE){
					final Set<String> set=CommonUtils.set();
					for(final Map.Entry<String, SoftReference<String>> entry:PASCAL_TO_UNDERSCORE_CACHE.entrySet()){
						if (entry.getValue()!=null&&entry.getValue().get()==null){
							set.add(entry.getKey());
						}
					}
					for(final String key:set){
						PASCAL_TO_UNDERSCORE_CACHE.remove(key);
					}
				}
			}
		}
		final StringBuilder buf = new StringBuilder(val.length() * 2);
		final Matcher matcher = PASCAL_PATTERN.matcher(val);
		boolean first = true;
		while (matcher.find()) {
			if (!first) {
				buf.append('_');
			}
			first = false;
			buf.append(matcher.group());
		}
		final String result = buf.toString().toUpperCase();
		ref=new SoftReference<String>(result);
		PASCAL_TO_UNDERSCORE_CACHE.putIfAbsent(val, ref);
		return result;
	}

	public static String printf(final String text, final Object... args) {
		final StringBuilder buf = new StringBuilder(text);
		for (int i = 0; i < args.length; i++) {
			final String serchStr = "{" + i + "}";
			final int pos = buf.indexOf(serchStr);
			if (pos >= 0) {
				buf.replace(pos, pos + serchStr.length(),
						convertNullToString(args[i]));
			}
		}
		return buf.toString();
	}

	/**
	 * 
	 * @param args
	 */
	public static String printfCsv(final Object... args) {
		final StringBuilder buf = new StringBuilder("");
		buf.append(convertNullToString(args[0]));
		for (int i = 1; i < args.length; i++) {
			buf.append(',');
			buf.append(convertNullToString(args[i]));
		}
		return buf.toString();
	}

	private static String convertNullToString(final Object arg) {
		if (arg == null) {
			return "";
		}
		return arg.toString();
	}

	/**
	 * 小文字が含まれるかの判定
	 * 
	 * @param value
	 */
	public static boolean containsLowerCase(final String value) {
		return containsRange(value, 'a', 'z');
	}

	/**
	 * 指定した範囲の文字が含まれるかの判定
	 * 
	 * @param value
	 * @param cStart
	 * @param cEnd
	 */
	public static boolean containsRange(final String value, final char cStart, final char cEnd) {
		if (value == null) {
			return false;
		}
		final int size = value.length();
		for (int i = 0; i < size; i++) {
			final char c = value.charAt(i);
			if (cStart <= c && c <= cEnd) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 指定した範囲の文字だけかの判定
	 * 
	 * @param value
	 * @param cStart
	 * @param cEnd
	 */
	public static boolean inRange(final String value, final char cStart,
			final char cEnd) {
		if (value == null) {
			return true;
		}
		final int size = value.length();
		for (int i = 0; i < size; i++) {
			final char c = value.charAt(i);
			if (c < cStart || c > cEnd) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 大文字が含まれるかの判定
	 * 
	 * @param value
	 */
	public static boolean containsUpperCase(final String value) {
		return containsRange(value, 'A', 'Z');
	}

	/**
	 * 正規表現のグループの値の取得
	 * 
	 * @param pattern
	 * @param value
	 * @param groupNo
	 */
	public static String getGroupString(final Pattern pattern,
			final String value, final int groupNo) {
		if (value == null) {
			return value;
		}
		final Matcher matcher = pattern.matcher(value);
		if (matcher.matches()) {
			return getGroupString(matcher, groupNo);
		}
		return null;
	}

	/**
	 * 正規表現のグループの値を取得します
	 * 
	 * @param matcher
	 * @param groupNo
	 */
	public static String getGroupString(final Matcher matcher, final int groupNo) {
		if (matcher.groupCount() < groupNo) {
			return null;
		}
		return matcher.group(groupNo);
	}

	private static final String[] EMPTY_STRING_ARRAY = new String[0];

	/**
	 * 文字列を指定したセパレーターで分割します
	 * 
	 * @param value
	 * @param regex
	 */
	public static final String[] split(final String value, final String regex) {
		if (value == null || value.length() == 0) {
			return EMPTY_STRING_ARRAY;
		}
		return value.split(regex);
	}

	/**
	 * 文字列をセパレーター(カンマ、スペース、セミコロン)で分割します
	 * 
	 * @param value
	 */
	public static final String[] split(final String value) {
		return trim(split(value, "[ ,;]"));
	}

	/**
	 * 文字列の配列をトリムします
	 * 
	 * @param args
	 */
	public static final String[] trim(final String... args) {
		if (CommonUtils.isEmpty(args)) {
			return args;
		}
		final List<String> result = CommonUtils.list(args.length);
		final int size = args.length;
		for (int i = 0; i < size; i++) {
			final String tr = CommonUtils.trim(args[i]);
			if (!CommonUtils.isEmpty(tr)) {
				result.add(tr);
			}
		}
		return result.toArray(EMPTY_STRING_ARRAY);
	}
	
	/**
	 * 表示上の幅を取得します。
	 * @param val
	 */
	public static int getDisplayWidth(final String val){
		if (val ==null){
			return 0;
		}
		int count=0;
		int i=0;
		final int codePointLen=val.codePointCount(0, val.length());
		while(i<codePointLen){
			final int codePoint=val.codePointAt(i++);
			if (isHalf(codePoint)){
				count++;
			} else{
				count=count+2;
			}
		}
		return count;
	}
	
	/**
	 * 表示上の幅を取得します。
	 * @param val
	 */
	public static int getDisplayWidth(final StringBuilder val){
		if (val ==null){
			return 0;
		}
		int count=0;
		int i=0;
		final int codePointLen=val.codePointCount(0, val.length());
		while(i<codePointLen){
			final int codePoint=val.codePointAt(i++);
			if (isHalf(codePoint)){
				count++;
			} else{
				count=count+2;
			}
		}
		return count;
	}

	/**
	 * 指定したコードポイントが半角かを返します。
	 * @param codePoint コードポイント
	 */
	public static boolean isHalf(final int codePoint){
		final Character.UnicodeBlock unicodeBlock = Character.UnicodeBlock.of(codePoint);
		//latin1
		if (Character.UnicodeBlock.BASIC_LATIN.equals(unicodeBlock)){
			return true;
		}else if (Character.UnicodeBlock.ARROWS.equals(unicodeBlock)){
			return true;
		}else if (Character.UnicodeBlock.LATIN_1_SUPPLEMENT.equals(unicodeBlock)){
			return true;
		}
		//half kana
		if (codePoint>=(0xFF61)&&codePoint<=(0xFF9F)){
			return true;
		}
		return false;
	}

}