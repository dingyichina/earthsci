/*******************************************************************************
 * Copyright 2012 Geoscience Australia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package au.gov.ga.earthsci.application.parts;

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;

import java.awt.BorderLayout;
import java.awt.Frame;

import javax.inject.Inject;

import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.widgets.Composite;

import au.gov.ga.earthsci.core.worldwind.TreeModel;
import au.gov.ga.earthsci.newt.awt.NewtInputHandlerAWT;
import au.gov.ga.earthsci.newt.awt.WorldWindowNewtAutoDrawableAWT;
import au.gov.ga.earthsci.newt.awt.WorldWindowNewtCanvasAWT;

/**
 * Part which displays a {@link WorldWindow}.
 * 
 * @author Michael de Hoog (michael.dehoog@ga.gov.au)
 */
public class WorldWindowPart
{
	@Inject
	private TreeModel model;

	@Inject
	public void init(Composite parent)
	{
		Composite composite = new Composite(parent, SWT.EMBEDDED);
		Frame frame = SWT_AWT.new_Frame(composite);
		frame.setLayout(new BorderLayout());

		Configuration.setValue(AVKey.INPUT_HANDLER_CLASS_NAME, NewtInputHandlerAWT.class.getName());
		Configuration.setValue(AVKey.WORLD_WINDOW_CLASS_NAME, WorldWindowNewtAutoDrawableAWT.class.getName());
		WorldWindowNewtCanvasAWT wwd = new WorldWindowNewtCanvasAWT();
		frame.add(wwd, BorderLayout.CENTER);

		wwd.setModel(model);
	}
}