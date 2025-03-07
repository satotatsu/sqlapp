/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.html;

import java.util.function.Consumer;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class Menu extends AbstractHtmlElement{
	private String name;
	private String url;

	private boolean active=false;
	
	private MenuDefinition menuDefinition=null;

	private String relativePath=null;
	
	public void addChild(Consumer<Menu> c){
		Menu menu=new Menu();
		menu.setParent(this);
		add(menu);
		c.accept(menu);
	}

	public void addChild(Menu menu){
		menu.setParent(this);
		add(menu);
	}

	public void setActive(String name){
		for(Menu e:this.getChildren(Menu.class)){
			if (CommonUtils.eq(e.getId(), name)){
				e.setActive(true);
				return;
			}
		}
	}

	public void setActive(MenuDefinition menuDef){
		String name=menuDef.toString();
		for(Menu e:this.getChildren(Menu.class)){
			if (CommonUtils.eq(e.getId(), name)){
				e.setActive(true);
			}
		}
	}

	public void setActive(boolean active){
		if (active){
			if (this.getParent()!=null){
				for(Menu e:this.getParent().getChildren(Menu.class)){
					if (e!=this){
						((Menu)e).active=false;
					}
				}
			}
		}
		this.active=active;
	}

	public void setActiveRecursive(boolean active){
		this.active=active;
		for(Menu e:this.getChildren(Menu.class)){
			e.setActiveRecursive(active);
		}
	}
	
	public void setRelativePathRecursive(String relativePath){
		this.relativePath=relativePath;
		for(Menu e:this.getChildren(Menu.class)){
			e.setRelativePathRecursive(relativePath);
		}
	}
	
	@Override
	public String toString(){
		return super.toString();
	}
	
	protected void toString(ToStringBuilder builder){
		builder.add("name", name);
		builder.add("url", url);
		builder.add("active", active);
		builder.add("menuDefinition", menuDefinition);
	}
	
	public String getUrl(){
		if (this.relativePath!=null){
			return this.relativePath+this.url;
		}
		return this.url;
	}
	
	@Override
	public Menu clone(){
		Menu clone=(Menu)super.clone();
		return clone;
	}


}
