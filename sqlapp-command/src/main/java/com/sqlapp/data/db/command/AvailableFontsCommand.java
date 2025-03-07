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

package com.sqlapp.data.db.command;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.FontUtils;
import com.sqlapp.util.OutputTextBuilder;

public class AvailableFontsCommand extends AbstractCommand{

	@Override
	protected void doRun() {
		Table table=FontUtils.getFontsAsTable();
		OutputTextBuilder builder=new OutputTextBuilder();
		builder.append(table);
		System.out.println(builder.toString());
	}

}
