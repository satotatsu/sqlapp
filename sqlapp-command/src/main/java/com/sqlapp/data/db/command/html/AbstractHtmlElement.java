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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class AbstractHtmlElement implements Cloneable{
	private String id=null;
	
	@Getter(lombok.AccessLevel.PUBLIC)
	@Setter(lombok.AccessLevel.PROTECTED)
	private AbstractHtmlElement parent;
	
	private List<AbstractHtmlElement> children=new ArrayList<>();
	
	protected void add(AbstractHtmlElement element){
		if (children==null){
			children=new ArrayList<>();
		}
		children.add(element);
	}
	
	protected <T extends AbstractHtmlElement> List<T> getChildren(Class<T> clazz){
		if (children==null){
			return Collections.emptyList();
		}
		@SuppressWarnings("unchecked")
		List<T> list=children.stream().filter(e->e!=null&&clazz.isInstance(e)).map(e->(T)e).collect(Collectors.toList());
		return list;
	}
	
	@Override
	public String toString(){
		ToStringBuilder builder=new ToStringBuilder();
		builder.add("id", id);
		builder.add("children", children);
		toString(builder);
		return builder.toString();
	}
	
	protected void toString(ToStringBuilder builder){
		
	}
	
	@Override
	public AbstractHtmlElement clone(){
		AbstractHtmlElement clone;
		try {
			clone = (AbstractHtmlElement)super.clone();
			List<AbstractHtmlElement> cloneChildren=CommonUtils.list();
			for(AbstractHtmlElement child:children){
				cloneChildren.add(child.clone());
			}
			clone.children=cloneChildren;
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
}
