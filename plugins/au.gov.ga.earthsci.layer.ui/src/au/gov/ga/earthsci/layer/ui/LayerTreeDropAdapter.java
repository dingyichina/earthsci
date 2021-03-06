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
package au.gov.ga.earthsci.layer.ui;

import java.io.File;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerDropAdapter;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TransferData;

import au.gov.ga.earthsci.core.url.SystemIconURLStreamHandlerService;
import au.gov.ga.earthsci.layer.intent.IntentLayerLoader;
import au.gov.ga.earthsci.layer.tree.ILayerTreeNode;
import au.gov.ga.earthsci.layer.tree.LayerNode;
import au.gov.ga.earthsci.layer.ui.dnd.LayerTransfer;
import au.gov.ga.earthsci.layer.ui.dnd.LayerTransferData;
import au.gov.ga.earthsci.layer.ui.dnd.LayerTransferData.TransferredLayer;
import au.gov.ga.earthsci.layer.ui.dnd.LocalLayerTransfer;
import au.gov.ga.earthsci.layer.worldwind.ITreeModel;

/**
 * {@link DropTargetListener} implementation for the layer tree.
 * 
 * @author Michael de Hoog (michael.dehoog@ga.gov.au)
 */
public class LayerTreeDropAdapter extends ViewerDropAdapter
{
	private final ITreeModel model;
	private final IEclipseContext context;

	public LayerTreeDropAdapter(TreeViewer viewer, ITreeModel model, IEclipseContext context)
	{
		super(viewer);
		this.model = model;
		this.context = context;
	}

	@Override
	public boolean performDrop(Object d)
	{
		if (d == null)
		{
			return false;
		}

		int index;
		ILayerTreeNode target = (ILayerTreeNode) getCurrentTarget();
		if (target == null)
		{
			target = model.getRootNode();
			index = target.getChildCount();
		}
		else
		{
			int location = getCurrentLocation();
			if (location == LOCATION_ON || location == LOCATION_NONE)
			{
				index = target.getChildCount();
			}
			else
			{
				index = location == LOCATION_BEFORE ? target.index() : target.index() + 1;
				target = target.getParent();
			}
		}

		if (LocalLayerTransfer.getInstance().isSupportedType(getCurrentEvent().currentDataType) ||
				LayerTransfer.getInstance().isSupportedType(getCurrentEvent().currentDataType))
		{
			LayerTransferData data = (LayerTransferData) d;

			//cannot drop a gadget onto itself or a child
			TransferredLayer[] toDrop = data.getLayers();
			if (getCurrentOperation() == DND.DROP_MOVE)
			{
				for (TransferredLayer drop : toDrop)
				{
					if (!validDropTarget(target, drop))
					{
						return false;
					}
				}
			}
			for (int i = toDrop.length - 1; i >= 0; i--)
			{
				TransferredLayer layer = toDrop[i];
				ILayerTreeNode node = layer.getNode();
				target.addChild(index, node);
				getViewer().add(target, node);
				getViewer().reveal(node);
			}

			// Deselect all of the moved nodes so they dont get removed by the LayerTreeDragSourceListener#dragFinished
			getViewer().setSelection(null);

			return true;
		}
		else if (FileTransfer.getInstance().isSupportedType(getCurrentEvent().currentDataType))
		{
			String[] filenames = (String[]) d;
			for (String filename : filenames)
			{
				File file = new File(filename);
				if (file.isFile())
				{
					final LayerNode node = new LayerNode();
					node.setName(file.getName());
					node.setEnabled(true);
					node.setIconURL(SystemIconURLStreamHandlerService.createURL(file));

					target.addChild(index, node);
					getViewer().add(target, node);
					getViewer().reveal(node);

					IntentLayerLoader.load(file.toURI(), node, context);
				}
			}
		}
		return false;
	}

	protected boolean validDropTarget(ILayerTreeNode target, TransferredLayer drop)
	{
		int[] dropPath = drop.getTreePath();
		int[] targetPath = target.indicesToRoot();
		if (dropPath == null)
		{
			return true;
		}
		return dropPath.length > 0
				&& (targetPath.length < dropPath.length || targetPath[dropPath.length - 1] != dropPath[dropPath.length - 1]);
	}

	@Override
	protected TreeViewer getViewer()
	{
		return (TreeViewer) super.getViewer();
	}

	@Override
	public boolean validateDrop(Object target, int op, TransferData type)
	{
		return LocalLayerTransfer.getInstance().isSupportedType(type) ||
				LayerTransfer.getInstance().isSupportedType(type) ||
				FileTransfer.getInstance().isSupportedType(type);
	}

	@Override
	public void dragEnter(DropTargetEvent event)
	{
		if (event.detail == DND.DROP_DEFAULT || FileTransfer.getInstance().isSupportedType(event.currentDataType))
		{
			event.detail = DND.DROP_COPY;
		}
	}

	@Override
	public void dragOperationChanged(DropTargetEvent event)
	{
		if (event.detail == DND.DROP_DEFAULT || FileTransfer.getInstance().isSupportedType(event.currentDataType))
		{
			event.detail = DND.DROP_COPY;
		}
	}
}
