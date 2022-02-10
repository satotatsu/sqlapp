/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.command.html;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.InvalidTextException;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

import lombok.Data;

public class VirtualForeignKeyLoader {
	
	private final String encoding="utf8";
	
	public VirtualForeignKeyLoader(){
	}

	public void load(final Catalog catalog, final File file){
		if (file==null||!file.exists()){
			return;
		}
		if (file.listFiles()==null) {
			return;
		}
		for(final File child:file.listFiles()){
			final List<String> texts=FileUtils.readTextList(child, encoding);
			loadInternal(catalog, texts);
		}
	}

	private void loadInternal(final Catalog catalog, final List<String> texts){
		for(int i=0;i<texts.size();i++){
			final String text=texts.get(i);
			if (isComment(text)){
				continue;
			}
			final TablePair pair=parse(text, i+1);
			final Table from=getTable(pair, pair.getFrom(), catalog);
			final Table to=getTable(pair, pair.getTo(), catalog);
			final Column[] columns=getColumns(pair, pair.getFrom(), from);
			final Column[] pkColumns=getColumns(pair, pair.getTo(), to);
			final ForeignKeyConstraint fk=new ForeignKeyConstraint("fk_"+from.getName()+"_virtual"+(from.getConstraints().getForeignKeyConstraints().size()+1), columns, pkColumns);
			fk.setVirtual(true);
			from.getConstraints().add(fk);
		}
	}

	private Table getTable(final TablePair pair, final Table table,final Catalog catalog){
		Table from;
		if (!CommonUtils.isEmpty(table.getSchemaName())){
			final Schema schema=catalog.getSchemas().get(pair.getFrom().getSchemaName());
			if (schema==null){
				throw new InvalidTextException(pair.getLine(), pair.getLineNo(), table+"(Schema) does not found.");
			}
			from=schema.getTable(table.getName());
			if (from==null){
				throw new InvalidTextException(pair.getLine(), pair.getLineNo(), table+" does not found.");
			}
			return from;
		} else{
			for(final Schema schema:catalog.getSchemas()){
				from=schema.getTable(table.getName());
				if (from!=null){
					return from;
				}
			}
		}
		throw new InvalidTextException(pair.getLine(), pair.getLineNo(), table+" does not found.");
	}
	
	private Column[] getColumns(final TablePair pair, final Table ref, final Table table){
		final List<Column> columns=CommonUtils.list();
		if (ref.getColumns().isEmpty()){
			for(final Column column:table.getColumns()){
				if (column.isPrimaryKey()){
					columns.add(column);
				}
			}
		} else{
			for(final Column col:ref.getColumns()){
				final Column column=table.getColumns().get(col.getName());
				if (column==null){
					throw new InvalidTextException(pair.getLine(), pair.getLineNo(), col+" does not found.");
				}
				columns.add(column);
			}
		}
		return columns.toArray(new Column[0]);
	}
	

	private static Pattern COMMENT_PATTERN=Pattern.compile("\\s*#.*");

	private boolean isComment(final String text){
		final Matcher matcher=COMMENT_PATTERN.matcher(text);
		return matcher.matches();
	}
	
	private TablePair parse(String text, final int lineNo){
		final String base=text;
		text=text.trim();
		final String[] texts=text.split("\\s*->\\s*");
		if (texts.length==0){
			throw new InvalidTextException(base, lineNo, "No relations(->) found.");
		} if (texts.length!=2){
			throw new InvalidTextException(base, lineNo, "Multiple relations(->) found. count="+texts.length);
		}
		final Table from=parseTable(base, texts[0], lineNo);
		final Table to=parseTable(base, texts[1], lineNo);
		final TablePair pair=new TablePair();
		pair.setFrom(from);
		pair.setTo(to);
		pair.setLine(text);
		pair.setLineNo(lineNo);
		return pair;
	}

	private Table parseTable(final String base, final String tableText, final int lineNo){
		final int start=tableText.indexOf('(');
		String tablePart=null;
		final Table table=new Table();
		if (start>0){
			tablePart=tableText.substring(0, start).trim();
			if (!tableText.endsWith(")")){
				throw new InvalidTextException(base, lineNo, "aaaaa(id,val)->bbbbb");
			}
			final String[] columns=tableText.substring(start+1, tableText.length()-1).split("\\s*,\\s*");
			for(String col:columns){
				final Column column=table.newColumn();
				col=col.trim();
				if (CommonUtils.isEmpty(col)){
					throw new InvalidTextException(base, lineNo, "Invalid column definition. value="+tableText);
				}
				column.setName(col);
				table.getColumns().add(column);
			}
		} else{
			tablePart=tableText;
		}
		final String[] names=tablePart.split("\\.");
		if (names.length==1){
			table.setName(names[0]);
		} else if (names.length==2){
			table.setSchemaName(names[0]);
			table.setName(names[1]);
		} else if (names.length==3){
			table.setCatalogName(names[0]);
			table.setSchemaName(names[1]);
			table.setName(names[2]);
		} else{
			throw new InvalidTextException(base, lineNo, "Invalid tableName. value="+tablePart);
		}
		return table;
	}

	@Data
	static class TablePair{
		private Table from;
		private Table to;
		private String line;
		private int lineNo;
	}
	
}
