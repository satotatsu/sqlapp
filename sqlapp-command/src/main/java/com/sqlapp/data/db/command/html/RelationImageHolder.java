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

import lombok.Data;

@Data
public class RelationImageHolder {
	private String content;
	private File file;
	private String imageMapId;
	private String imageMap;

	public RelationImageHolder(File file, String imageMapId, String imageMap){
		this.file=file;
		this.imageMapId=imageMapId;
		this.imageMap=imageMap;
	}
	
	public void replaceImageMap(String oldText,String newText){
		if (this.imageMap!=null){
			this.imageMap=imageMap.replace(oldText, newText);
		}
	}
}
