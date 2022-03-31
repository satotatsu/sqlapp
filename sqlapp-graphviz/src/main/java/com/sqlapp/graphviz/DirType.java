/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-graphviz.
 *
 * sqlapp-graphviz is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-graphviz is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-graphviz.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.graphviz;
/**
 * 
 * @author tatsuo satoh
 * {@see <a href="https://graphviz.org/docs/attrs/dir/">dir</a>}
 */
public enum DirType {
	forward("start-→end")
	,back  ("start←-end")
	,both  ("start←→end")
	,none  ("start--end")
	,;
	
	private  final String comment;
	private DirType(String comment){
		this.comment=comment;
	}
	/**
	 * @return the comment
	 */
	public String getComment() {
		return comment;
	}
}
