/*******************************************************************************
 * Copyright (c) 2006 Zend Corporation and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Zend and IBM - Initial implementation
 *******************************************************************************/
package org.eclipse.php.internal.ui.explorer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.*;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.php.internal.core.phpModel.PHPModelUtil;
import org.eclipse.php.internal.core.phpModel.parser.ModelListener;
import org.eclipse.php.internal.core.phpModel.parser.PHPWorkspaceModelManager;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPFileData;
import org.eclipse.php.internal.ui.StandardPHPElementContentProvider;
import org.eclipse.php.internal.ui.SuperClassTreeContentProvider;
import org.eclipse.swt.widgets.Control;

public class ExplorerContentProvider extends StandardPHPElementContentProvider implements ITreeContentProvider, ModelListener, IResourceChangeListener {

	protected static final int GRANT_PARENT = 1 << 1;
	protected static final int ORIGINAL = 0;
	protected static final int PARENT = 1 << 0;
	protected static final int PROJECT = 1 << 2;

	private Object fInput;
	ExplorerPart fPart;
	private int fPendingChanges;
	TreeViewer fViewer;

	ITreeContentProvider superClassTreeContentProvider = new SuperClassTreeContentProvider(this);

	public ExplorerContentProvider(ExplorerPart part, boolean provideMembers) {
		super(provideMembers);
		fPart = part;
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
	}

	private synchronized void addPendingChange() {
		fPendingChanges++;
		// System.out.print(fPendingChanges);
	}

	public void dataCleared() {
	}

	public void dispose() {
		super.dispose();
		PHPWorkspaceModelManager.getInstance().removeModelListener(this);
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
	}

	public void fileDataAdded(PHPFileData fileData) {
		IResource res = PHPModelUtil.getResource(fileData);
		if (res != null)
			postRefresh(res);
		else
			postRefresh(fileData);
	}

	public void fileDataChanged(PHPFileData fileData) {
		IResource res = PHPModelUtil.getResource(fileData);
		if (res == null)
			return;
		postRefresh(res, true);
	}

	public void fileDataRemoved(PHPFileData fileData) {
	}

	protected Object[] getAllProjects() {
		return ResourcesPlugin.getWorkspace().getRoot().getProjects();
	}

	public Object[] getChildrenInternal(Object parentElement) {
		if (parentElement instanceof PHPWorkspaceModelManager) {
			return getAllProjects();
		}
		ArrayList children = new ArrayList(Arrays.asList(super.getChildrenInternal(parentElement)));
		children.addAll(Arrays.asList(superClassTreeContentProvider.getChildren(parentElement)));
		return children.toArray();
	}

	protected Object getViewerInput() {
		return fInput;
	}

	public synchronized boolean hasPendingChanges() {
		return fPendingChanges > 0;
	}

	/* (non-Javadoc)
	 * Method declared on IContentProvider.
	 */
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		super.inputChanged(viewer, oldInput, newInput);
		fViewer = (TreeViewer) viewer;
		if (oldInput == null && newInput != null) {
			PHPWorkspaceModelManager.getInstance().addModelListener(this);
		} else if (oldInput != null && newInput == null) {
			PHPWorkspaceModelManager.getInstance().removeModelListener(this);
		}
		fInput = newInput;
	}

	protected Object internalGetParent(final Object element) {
		final Object parent = super.internalGetParent(element);
		if (parent instanceof PHPFileData) {
			final IResource file = PHPModelUtil.getResource(element);
			if (file != null && file.getProject() != null && file.getProject().isAccessible())
				return file;
		}
		return parent;
	}

	boolean isParent(Object root, Object child) {
		Object parent = getParent(child);
		if (parent == null)
			return false;
		if (parent.equals(root))
			return true;
		return isParent(root, parent);
	}

	private void postAdd(final Object parent, final Object element) {
		postRunnable(new Runnable() {
			public void run() {
				if (fViewer == null)
					return;
				if (fViewer.testFindItem(element) == null)
					fViewer.add(parent, element);
			}
		});
	}

	private void postProjectStateChanged(final Object root) {
		postRunnable(new Runnable() {
			public void run() {
				fPart.projectStateChanged(root);
			}
		});
	}

	void postRefresh(final List toRefresh, final boolean updateLabels) {
		postRunnable(new Runnable() {
			public void run() {
				Control ctrl = fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed() && ctrl.isVisible()) {
					for (Iterator iter = toRefresh.iterator(); iter.hasNext();) {
						fViewer.refresh(iter.next(), updateLabels);
					}
				}
			}
		});
	}

	public void postRefresh(Object root) {
		// JFace doesn't refresh when object isn't part of the viewer
		// Therefore move the refresh start down to the viewer's input
		if (root instanceof IWorkspaceRoot)
			root = PHPWorkspaceModelManager.getInstance();
		if (isParent(root, fInput))
			root = fInput;
		postRefresh(root, true);
	}

	private void postRefresh(final Object root, final boolean updateLabels) {
		postRunnable(new Runnable() {
			public void run() {
				fViewer.refresh(root, updateLabels);
			}
		});
	}

	private void postRemove(final Object parent, final Object element) {
		postRunnable(new Runnable() {
			public void run() {
				fViewer.remove(parent, new Object[] { element });
			}
		});
	}

	private void postRunnable(final Runnable r) {
		if (fViewer == null)
			return;
		final Control ctrl = fViewer.getControl();
		final Runnable trackedRunnable = new Runnable() {
			public void run() {
				try {
					if (ctrl != null && !ctrl.isDisposed() && ctrl.isVisible())
						r.run();
				} finally {
					removePendingChange();
				}
			}
		};
		if (ctrl != null && !ctrl.isDisposed()) {
			addPendingChange();
			try {
				ctrl.getDisplay().asyncExec(trackedRunnable);
			} catch (RuntimeException e) {
				removePendingChange();
				throw e;
			} catch (Error e) {
				removePendingChange();
				throw e;
			}
		}
	}

	private boolean processResourceDelta(IResourceDelta delta, Object parent) {
		int status = delta.getKind();
		int flags = delta.getFlags();

		IResource resource = delta.getResource();
		// filter out changes affecting the output folder
		if (resource == null)
			return false;

		// this could be optimized by handling all the added children in the parent
		if ((status & IResourceDelta.REMOVED) != 0) {
			Object removeItem = resource;
			postRemove(parent, removeItem);
			//			}
		}
		if ((status & IResourceDelta.ADDED) != 0) {
			Object addItem = resource;
			postAdd(parent, addItem);
		}
		// open/close state change of a project
		if ((flags & IResourceDelta.OPEN) != 0) {
			postProjectStateChanged(internalGetParent(parent));
			return true;
		}
		processResourceDeltas(delta.getAffectedChildren(), resource);
		return false;
	}

	private boolean processResourceDeltas(IResourceDelta[] deltas, Object parent) {
		if (deltas == null)
			return false;

		if (parent instanceof IWorkspaceRoot) { // the workspaceRoot is not a part of the tree model
			// it is represnted by the PHPWorkspaceModelManager
			parent = PHPWorkspaceModelManager.getInstance();
		}

		if (deltas.length > 1) {
			// more than one child changed, refresh from here downwards
			postRefresh(parent);
			return true;
		}

		for (int i = 0; i < deltas.length; i++) {
			if (processResourceDelta(deltas[i], parent))
				return true;
		}
		return false;
	}

	synchronized void removePendingChange() {
		fPendingChanges--;
		if (fPendingChanges < 0)
			fPendingChanges = 0;
		// System.out.print(fPendingChanges);
	}

	public void resourceChanged(IResourceChangeEvent event) {
		IResourceDelta delta = event.getDelta();
		if (delta != null) {
			IResource resource = delta.getResource();
			processResourceDeltas(delta.getAffectedChildren(), resource);
		}
	}
}