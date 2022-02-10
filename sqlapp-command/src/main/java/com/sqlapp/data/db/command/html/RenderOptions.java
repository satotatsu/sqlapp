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

import java.util.Set;

import org.mvel2.ParserContext;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DateUtils;

import lombok.Data;

@Data
public class RenderOptions {
	private String cdnScheme="https:";
	
	//private String tableClass=null;
	//private String tableClass="datasheet";
	private String tableClass="outline-header border box-header outline";
		
	private ParserContext parserContext=new CustomParserContextFactory().getParserContext();
	
	private HighlightMethod highlightMethod=HighlightMethod.Prism;
	
	private String dateTimeFormat="yyyy-MM-dd HH:mm:ss";

	private String checkIconValue="<span class=\"icon icon-16 icon-check-sign\"/>";

	private String cssFrameworkPath="//cdnjs.cloudflare.com/ajax/libs/cascade-framework/1.5.0/css/build-full.min.css";
	
	private boolean withJquery=true;

	private boolean withRows=true;

	private String[] hideColumns=new String[]{SchemaProperties.REMARKS.getLabel(), SchemaProperties.DISPLAY_REMARKS.getLabel(), SchemaProperties.SPECIFICS.getLabel(), SchemaProperties.STATISTICS.getLabel(), SchemaProperties.CREATED_AT.getLabel(),SchemaProperties.LAST_ALTERED_AT.getLabel()};

	private Set<String> hideColumnsSet=null;
	
	public String formatDateTime(Object obj){
		if (obj==null){
			return "";
		}
		return DateUtils.format((java.util.Date)obj, dateTimeFormat);
	}
	
	public String checkIcon(Object obj){
		if (obj==null){
			return "";
		}
		Boolean bool=Converters.getDefault().convertObject(obj, Boolean.class);
		if (bool!=null&&bool.booleanValue()){
			return checkIconValue;
		} else{
			return "";
		}
	}
	
	public String tableHeaderColAttr(String... args){
		return tableBodyColAttr(args);
	}

	public String tableBodyColAttr(String... args){
		StringBuilder builder=new StringBuilder();
		StringBuilder childBuilder=new StringBuilder();
		for(String arg:args){
			childBuilder.append("_col_"+convertName(arg)+"_");
			childBuilder.append(' ');
		}
		builder.append(HtmlUtils.attr("class", childBuilder.substring(0, childBuilder.length()-1)));
		if (isHideTarget(args)){
			builder.append(" ");
			builder.append(HtmlUtils.attr("style", "display: none;"));
		}
		return builder.toString();
	}
	
	private boolean isHideTarget(String... args){
		for(String arg:args){
			if (getHideColumnsSet().contains(arg)){
				return true;
			}
		}
		return false;
	}

	private Set<String> getHideColumnsSet(){
		if (hideColumnsSet==null){
			hideColumnsSet=CommonUtils.lowerSet();
			for(String arg:hideColumns){
				hideColumnsSet.add(arg);
			}
		}
		return this.hideColumnsSet;
	}

	private String convertName(String name){
		if ("#".equals(name)){
			return "sharp";
		}
		return name.replace(" ", "");
	}

	public String getLanguage(Object obj){
		return this.getHighlightMethod().getLanguage(obj);
	}
	
	public String[] getHighlightJs(){
		return this.getHighlightMethod().getJs();
	}

	public String[] getHighlightCss(){
		return this.getHighlightMethod().getCss();
	}

	public String getHighlightPreClass(){
		return this.getHighlightMethod().getPreClass();
	}

	public String loadInitScript(){
		return this.getHighlightMethod().loadInitScript();
	}
}
