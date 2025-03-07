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

package com.sqlapp.data.schemas;

import static com.sqlapp.util.CommonUtils.enumMap;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;

/**
 * パーティションタイプ
 * 
 * @author satoh
 * 
 */
public enum PartitioningType implements EnumProperties {
	/**
	 * Range Columns
	 */
	RangeColumns("RANGE COLUMNS", "RANGE.*COLUMNS"){
		@Override
		public boolean isPangePartitioning(){
			return true;
		}
	}
	/**
	 * Range
	 */
	, Range("RANGE", "RANGE.*"){
		@Override
		public boolean isPangePartitioning(){
			return true;
		}
	}
	/**
	 * ハッシュパーティション
	 */
	, Hash("HASH", "HASH.*"){
		@Override
		public boolean isHashPartitioning(){
			return true;
		}
		@Override
		public boolean isSizePartitioning(){
			return true;
		}
	}
	/**
	 * List
	 */
	, List("LIST", "LIST.*"){
		@Override
		public boolean isListPartitioning(){
			return true;
		}
	}
	/**
	 * List
	 */
	, RoundRobin("ROUNDROBIN", "ROUND\\s*ROBIN"){
		@Override
		public boolean isRoundRobinPartitioning(){
			return true;
		}
		@Override
		public boolean isSizePartitioning(){
			return true;
		}
	}
	/**
	 * List Columns(for MySQL)
	 */
	, ListColumns("LIST COLUMNS", "LIST.*COLUMNS"){
		@Override
		public boolean isListPartitioning(){
			return true;
		}
	}
	/**
	 * Linear Hash(for MySQL)
	 */
	, LinearHash("LINEAR HASH", "Linear.*Hash"){
		@Override
		public boolean isSizePartitioning(){
			return true;
		}
	}
	/**
	 * Key(for MySQL)
	 */
	, Key("KEY", "Key.*"){
		@Override
		public boolean isSizePartitioning(){
			return true;
		}
	}
	/**
	 * Linear Key(for MySQL)
	 */
	, LinearKey("LINEAR KEY", "Linear.*Key"){
		@Override
		public boolean isSizePartitioning(){
			return true;
		}
	}
	;
	/**
	 * 正規表現
	 */
	private final Pattern pattern;
	/**
	 * 正規表現
	 */
	private final String text;
	/**
	 * 代替型マップ
	 */
	private static final Map<PartitioningType, PartitioningType> surrogateMap = enumMap(PartitioningType.class);

	/**
	 * スタティックコンストラクタ
	 */
	static {
		initializeSurrogateMap();
	}

	/**
	 * 代替型マップの初期化
	 */
	static void initializeSurrogateMap() {
		surrogateMap.put(Key, Hash);
		surrogateMap.put(LinearHash, Hash);
		surrogateMap.put(LinearKey, Key);
		surrogateMap.put(RangeColumns, Range);
		surrogateMap.put(Range, RangeColumns);
		surrogateMap.put(ListColumns, List);
		surrogateMap.put(RoundRobin, Hash);
	}

	private PartitioningType(final String text, final String patternText) {
		this.text = text;
		this.pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
	}

	public static PartitioningType parse(final String value) {
		if (value == null) {
			return null;
		}
		for (final PartitioningType partition : PartitioningType.values()) {
			final Matcher matcher = partition.pattern.matcher(value);
			if (matcher.matches()) {
				return partition;
			}
		}
		return null;
	}

	public boolean isHashPartitioning(){
		return false;
	}

	public boolean isSizePartitioning(){
		return false;
	}

	public boolean isPangePartitioning(){
		return false;
	}

	public boolean isListPartitioning(){
		return false;
	}

	public boolean isRoundRobinPartitioning(){
		return false;
	}

	/**
	 * 代替型を取得します
	 * 
	 */
	public PartitioningType getSurrogate() {
		if (surrogateMap.containsKey(this)) {
			return surrogateMap.get(this);
		}
		return null;
	}
	
	public String toExpression(final Table table) {
		if (table.getPartitionParent()==null) {
			return null;
		}
		final Table parent=table.getPartitionParent().getTable();
		if (parent==null||parent.getPartitioning()==null) {
			return null;
		}
		final Partitioning partitioning=parent.getPartitioning();
		String column;
		if (CommonUtils.isEmpty(partitioning.getPartitioningColumns())) {
			column="x";
		} else {
			final SeparatedStringBuilder sep=new SeparatedStringBuilder();
			if (partitioning.getPartitioningColumns().size()>1) {
				sep.setStart("(").setEnd(")");
			}
			partitioning.getPartitioningColumns().forEach(c->{
				sep.add(c.getName());
			});
			column=sep.toString();
		}
		final StringBuilder builder=new StringBuilder();
		if (this.isPangePartitioning()) {
			appendRangeExpression(table, column, partitioning, builder);
		} else if (this.isListPartitioning()) {
			appendListExpression(table, column, partitioning, builder);
		}
		return builder.toString();
	}
	
	protected void appendRangeExpression(final Table table, final String column, final Partitioning partitioning, final StringBuilder builder) {
		if (!CommonUtils.isEmpty(table.getPartitionParent().getLowValue())) {
			if (partitioning.getPartitioningColumns().size()>1) {
				if (table.getPartitionParent().getLowValue().startsWith("(")&&table.getPartitionParent().getLowValue().endsWith(")")) {
					builder.append(table.getPartitionParent().getLowValue());
				} else {
					builder.append("(");
					builder.append(table.getPartitionParent().getLowValue());
					builder.append(")");
				}
			} else {
				builder.append(table.getPartitionParent().getLowValue());
			}
			builder.append("<=");
		}
		builder.append(column);
		if (!CommonUtils.isEmpty(table.getPartitionParent().getHighValue())) {
			builder.append("<");
			if (partitioning.getPartitioningColumns().size()>1) {
				if (table.getPartitionParent().getHighValue().startsWith("(")&&table.getPartitionParent().getHighValue().endsWith(")")) {
					builder.append(table.getPartitionParent().getHighValue());
				} else {
					builder.append("(");
					builder.append(table.getPartitionParent().getHighValue());
					builder.append(")");
				}
			} else {
				builder.append(table.getPartitionParent().getHighValue());
			}
		}
	}

	protected void appendListExpression(final Table table, final String column, final Partitioning partitioning, final StringBuilder builder) {
		if (!CommonUtils.isEmpty(table.getPartitionParent().getHighValue())) {
			builder.append(column);
			builder.append(" IN ");
			if (table.getPartitionParent().getHighValue().startsWith("(")&&table.getPartitionParent().getHighValue().endsWith(")")) {
				builder.append(table.getPartitionParent().getHighValue());
			} else {
				builder.append("(");
				builder.append(table.getPartitionParent().getHighValue());
				builder.append(")");
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return text;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.EnumProperties#getDisplayName(java.util.Locale)
	 */
	@Override
	public String getDisplayName(final Locale locale) {
		return getDisplayName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getSqlValue()
	 */
	@Override
	public String getSqlValue() {
		return getDisplayName();
	}
}
