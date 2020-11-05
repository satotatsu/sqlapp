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

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

public class OutputTextBuilder {
	private StringBuilder builder=new StringBuilder();
	/**インデント文字*/
	private String indentString="\t";
	/**インデントレベル*/
	private int indentLevel=0;
	/**lineセパレーター*/
	private String lineSeparator="=";
	
	private Converters converters=Converters.getDefault();

	/**
	 * オブジェクトを追加します。
	 * @param val
	 * @return this
	 */
	public OutputTextBuilder append(Object val){
		appendInternal(val);
		return instance();
	}

	/**
	 * テーブルを追加します。
	 * @param table
	 * @return this
	 */
	public OutputTextBuilder append(Table table){
		return append(table, (a,b)->b.get(a));
	}

	/**
	 * テーブルを追加します。
	 * @param table
	 * @param p 表示するカラムを決めるPredicate
	 * @return this
	 */
	public OutputTextBuilder append(Table table, Predicate<Column> p){
		return append(table, p, (a,b)->b.get(a));
	}

	/**
	 * テーブルを追加します。
	 * @param table 追加するテーブル
	 * @param bifunc 表示する値を取得する関数
	 * @return this
	 */
	public OutputTextBuilder append(Table table, BiFunction<Column, Row, Object> bifunc){
		return append(table, c->true, bifunc);
	}
	
	/**
	 * テーブルを追加します。
	 * @param table 追加するテーブル
	 * @param p 表示するカラムを決めるPredicate
	 * @param bifunc 表示する値を取得する関数
	 * @return this
	 */
	public OutputTextBuilder append(Table table, Predicate<Column> p, BiFunction<Column, Row, Object> bifunc){
		List<Column> columns=CommonUtils.list();
		for(int i=0;i<table.getColumns().size();i++){
			Column column=table.getColumns().get(i);
			if (p.test(column)){
				columns.add(column);
			}
		}
		Object[] objects=new Object[columns.size()];
		for(int i=0;i<columns.size();i++){
			Column column=columns.get(i);
			objects[i]=column.getName();
		}
		List<Line> columnLines=toLine(objects);
		List<Line> totalRowLines=CommonUtils.list();
		for(Row row:table.getRows()){
			Object[] values=new Object[columns.size()];
			for(int i=0;i<columns.size();i++){
				Column column=columns.get(i);
				values[i]=bifunc.apply(column, row);
			}
			List<Line> rowLines=toLine(values);
			totalRowLines.addAll(rowLines);
		}
		List<Line> totalLines=CommonUtils.list();
		totalLines.addAll(columnLines);
		totalLines.addAll(totalRowLines);
		setLineSize(totalLines);
		int len=outputLines(columnLines);
		lineBreak();
		append(lineSeparator, len);
		lineBreak();
		outputLines(totalRowLines);
		return instance();
	}

	private int outputLines(List<Line> lines){
		int len=0;
		int j=0;
		for(Line line:lines){
			lineBreak(j>0);
			len=0;
			for(int i=0;i<line.values.length;i++){
				String name=line.values[i];
				append(" | ", i>0);
				appendFix(name, line.sizes[i]);
				len=len+line.sizes[i];
				if (i>0){
					len=len+3;
				}
			}
			j++;
		}
		return len;
	}

	private List<Line> toLine(Object... args){
		String[] texts=new String[args.length];
		for(int i=0;i<args.length;i++){
			Object obj=args[i];
			texts[i]=toString(obj);
		}
		int row=1;
		for(String text:texts){
			row=Math.max(row, row(text));
		}
		List<Line> lines=CommonUtils.list();
		for(int i=0;i<row;i++){
			Line line=new Line();
			lines.add(line);
			line.values=new String[texts.length];
			line.sizes=new int[texts.length];
		}
		String[] empty=new String[row];
		for(int i=0;i<texts.length;i++){
			String text=texts[i];
			String[] splits;
			if (text!=null){
				splits=text.split("\n");
			} else{
				splits=empty;
			}
			for(int j=0;j<row;j++){
				Line line=lines.get(j);
				if (splits.length>j){
					line.values[i]=splits[j];
				}
			}
		}
		return lines;
	}

	private void setLineSize(List<Line> lines){
		if (CommonUtils.isEmpty(lines)){
			return;
		}
		int length=lines.get(0).values.length;
		for(int i=0;i<length;i++){
			int size=0;
			for(Line line:lines){
				String text=line.values[i];
				size=Math.max(size, length(text));
			}
			for(Line line:lines){
				line.sizes[i]=size;
			}
		}
	}

	
	static class Line{
		public String[] values=null;
		public int[] sizes=null;
	}
	
	private int length(Object obj){
		if (obj ==null){
			return 0;
		}
		String val= toString(obj);
		String[] args=val.split("\n");
		int len=0;
		for(String arg:args){
			len=Math.max(len, lengthSimple(arg));
		}
		return len;
	}

	private int row(Object obj){
		if (obj ==null){
			return 0;
		}
		String val= toString(obj);
		String[] args=val.split("\n");
		return args.length;
	}
	
	private int lengthSimple(String val){
		return StringUtils.getDisplayWidth(val);
	}
	
	/**
	 * オブジェクトを指定した長さになるように空白文字列を付加して追加します。
	 * @param val
	 * @param length
	 * @return this
	 */
	public OutputTextBuilder appendFix(Object val, int length){
		return appendFix(val, ' ', length);
	}

	/**
	 * オブジェクトを指定した長さになるように空白文字列を付加して追加します。
	 * @param val
	 * @param length
	 * @return this
	 */
	public OutputTextBuilder appendFix(Object val, char c, int length){
		String txt=toString(val);
		StringBuilder builder=new StringBuilder(length);
		if (txt!=null){
			builder.append(txt);
		}
		while(StringUtils.getDisplayWidth(builder)<length){
			builder.append(c);
		}
		appendInternal(builder.toString());
		return instance();
	}
	
	/**
	 * @return the converters
	 */
	public Converters getConverters() {
		return converters;
	}

	/**
	 * @param converters the converters to set
	 */
	public void setConverters(Converters converters) {
		this.converters = converters;
	}

	private String toString(Object val){
		return converters.convertString(val);
	}

	/**
	 * オブジェクトを指定した回数だけ追加します。
	 * @param val
	 * @param iterate
	 * @return this
	 */
	public OutputTextBuilder append(Object val, int iterate){
		for(int i=0;i<iterate;i++){
			appendInternal(val);
		}
		return instance();
	}

	/**
	 * 文字を指定した回数だけ追加します。
	 * @param val
	 * @param iterate
	 * @return this
	 */
	public OutputTextBuilder append(char val, int iterate){
		for(int i=0;i<iterate;i++){
			appendInternal(val);
		}
		return instance();
	}

	/**
	 * 文字を条件がtrueの場合に追加します。
	 * @param val
	 * @param bool
	 * @return this
	 */
	public OutputTextBuilder append(char val, boolean bool){
		if(!bool){
			return instance();
		}
		return appendInternal(val);
	}

	/**
	 * 文字を条件がtrueの場合に追加します。
	 * @param val
	 * @param bool
	 * @return this
	 */
	public OutputTextBuilder append(Object val, boolean bool){
		if(!bool){
			return instance();
		}
		return appendInternal(val);
	}

	private OutputTextBuilder appendInternal(Object val){
		if (val==null){
			return instance();
		}
		builder.append(toString(val));
		return instance();
	}

	private OutputTextBuilder appendInternal(char val){
		builder.append(val);
		return instance();
	}
	
	/**
	 * 改行します。
	 */
	public OutputTextBuilder lineBreak(){
		return lineBreak(true);
	}

	/**
	 * 条件がtrueの場合に改行します。
	 * @param bool
	 */
	public OutputTextBuilder lineBreak(boolean bool){
		if (bool){
			builder.append("\n");
			indent();
		}
		return instance();
	}

	
	private OutputTextBuilder indent(){
		append(indentString, indentLevel);
		return instance();
	}
	
	
	private OutputTextBuilder instance(){
		return this;
	}
	
	@Override
	public String toString(){
		return this.builder.toString();
	}
}
