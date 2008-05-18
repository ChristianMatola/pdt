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
package org.eclipse.php.internal.ui.projectOutline;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.*;
import org.eclipse.php.internal.core.phpModel.PHPModelUtil;
import org.eclipse.php.internal.core.phpModel.parser.PHPProjectModel;
import org.eclipse.php.internal.core.phpModel.parser.PHPWorkspaceModelManager;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPCodeData;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPFileData;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPFunctionData;
import org.eclipse.php.internal.ui.IPHPHelpContextIds;
import org.eclipse.php.internal.ui.PHPUIMessages;
import org.eclipse.php.internal.ui.PHPUiPlugin;
import org.eclipse.php.internal.ui.actions.OpenAction;
import org.eclipse.php.internal.ui.editor.LinkingSelectionListener;
import org.eclipse.php.internal.ui.editor.PHPStructuredEditor;
import org.eclipse.php.internal.ui.explorer.IMultiElementTreeContentProvider;
import org.eclipse.php.internal.ui.explorer.PHPTreeViewer;
import org.eclipse.php.internal.ui.preferences.PreferenceConstants;
import org.eclipse.php.internal.ui.treecontent.TreeProvider;
import org.eclipse.php.internal.ui.util.*;
import org.eclipse.php.internal.ui.util.TreePath;
import org.eclipse.php.ui.treecontent.IPHPTreeContentProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.ViewPart;

public class ProjectOutlinePart extends ViewPart implements IMenuListener, FocusListener {

	final String MEMENTO_KEY_PROJECT = "ProjectOutlinePart.storedProjectName"; //$NON-NLS-1$

	/* (non-Javadoc)
	 * @see org.eclipse.swt.events.FocusListener#focusGained(org.eclipse.swt.events.FocusEvent)
	 */
	public void focusGained(FocusEvent e) {
		// TODO Auto-generated method stub
		((ProjectOutlineContentProvider) fViewer.getContentProvider()).postRefresh(fViewer.getInput(), true);

	}

	/* (non-Javadoc)
	 * @see org.eclipse.swt.events.FocusListener#focusLost(org.eclipse.swt.events.FocusEvent)
	 */
	public void focusLost(FocusEvent e) {
		// TODO Auto-generated method stub

	}

	private class ProjectOutlineTreeViewer extends PHPTreeViewer {
		java.util.List fPendingGetChildren;

		public ProjectOutlineTreeViewer(final Composite parent, final int style) {
			super(parent, style);
			fPendingGetChildren = Collections.synchronizedList(new ArrayList());
			setComparer(new PHPOutlineElementComparer());
		}

		public void add(final Object parentElement, final Object[] childElements) {
			if (fPendingGetChildren.contains(parentElement))
				return;
			super.add(parentElement, childElements);
		}

		private TreePath createTreePath(final TreeItem item) {
			final List result = new ArrayList();
			result.add(item.getData());
			TreeItem parent = item.getParentItem();
			while (parent != null) {
				result.add(parent.getData());
				parent = parent.getParentItem();
			}
			Collections.reverse(result);
			return new TreePath(result.toArray());
		}

		// Sends the object through the given filters
		private Object filter(final Object object, final Object parent, final ViewerFilter[] filters) {
			for (final ViewerFilter filter : filters) {
				if (!filter.select(fViewer, parent, object))
					return null;
			}
			return object;
		}

		/*
		 * @see org.eclipse.jface.viewers.StructuredViewer#filter(java.lang.Object[])
		 * @since 3.0
		 */
		protected Object[] filter(final Object[] elements) {
			final ViewerFilter[] filters = getFilters();
			if (filters == null || filters.length == 0)
				return elements;

			final ArrayList filtered = new ArrayList(elements.length);
			final Object root = getRoot();
			for (int i = 0; i < elements.length; i++) {
				boolean add = true;
				if (!isEssential(elements[i]))
					for (ViewerFilter element : filters) {
						add = element.select(this, root, elements[i]);
						if (!add)
							break;
					}
				if (add)
					filtered.add(elements[i]);
			}
			return filtered.toArray();
		}

		private Object getElement(final TreeItem item) {
			final Object result = item.getData();
			if (result == null)
				return null;
			return result;
		}

		/*
		 * @see org.eclipse.jface.viewers.StructuredViewer#filter(java.lang.Object)
		 */
		protected Object[] getFilteredChildren(final Object parent) {
			final List list = new ArrayList();
			final ViewerFilter[] filters = fViewer.getFilters();
			if (fViewer.getContentProvider() == null)
				return new Object[0];

			final Object[] children = ((ITreeContentProvider) fViewer.getContentProvider()).getChildren(parent);
			for (int i = 0; children != null && i < children.length; i++) {
				Object object = children[i];
				if (!isEssential(object)) {
					object = filter(object, parent, filters);
					if (object != null)
						list.add(object);
				} else
					list.add(object);
			}
			return list.toArray();
		}

		protected Object[] getRawChildren(final Object parent) {
			try {
				fPendingGetChildren.add(parent);
				return super.getRawChildren(parent);
			} finally {
				fPendingGetChildren.remove(parent);
			}
		}

		public ISelection getSelection() {
			final IContentProvider cp = getContentProvider();
			final Control control = getControl();
			if (control == null || control.isDisposed())
				return StructuredSelection.EMPTY;
			if (!(cp instanceof IMultiElementTreeContentProvider))
				return super.getSelection();
			final Tree tree = getTree();
			final TreeItem[] selection = tree.getSelection();
			final List result = new ArrayList(selection.length);
			final List treePaths = new ArrayList();
			for (final TreeItem item : selection) {
				final Object element = getElement(item);
				if (element == null)
					continue;
				if (!result.contains(element))
					result.add(element);
				treePaths.add(createTreePath(item));
			}
			return new MultiElementSelection(this, result, (TreePath[]) treePaths.toArray(new TreePath[treePaths.size()]));
		}

		protected void handleInvalidSelection(final ISelection invalidSelection, ISelection newSelection) {
			final IStructuredSelection is = (IStructuredSelection) invalidSelection;
			List ns = null;
			if (newSelection instanceof IStructuredSelection)
				ns = new ArrayList(((IStructuredSelection) newSelection).toList());
			else
				ns = new ArrayList();
			boolean changed = false;
			for (final Iterator iter = is.iterator(); iter.hasNext();) {
				final Object element = iter.next();
				if (element instanceof PHPProjectModel) {

					final IProject project = PHPWorkspaceModelManager.getInstance().getProjectForModel((PHPProjectModel) element);
					if (!project.isOpen()) {
						ns.add(project);
						changed = true;
					}
				} else if (element instanceof IProject) {
					final IProject project = (IProject) element;
					if (project.isOpen())
						changed = true;
				}
			}
			if (changed) {
				newSelection = new StructuredSelection(ns);
				setSelection(newSelection, true);
			}
			super.handleInvalidSelection(invalidSelection, newSelection);
		}

		/* Checks if a filtered object in essential (ie. is a parent that
		 * should not be removed).
		 */
		private boolean isEssential(final Object object) {
			if (object instanceof IContainer) {
				final IContainer folder = (IContainer) object;
				try {
					return folder.members().length > 0;
				} catch (final CoreException e) {
					e.printStackTrace();
				}
			}
			return false;
		}

		/*
		 * @see AbstractTreeViewer#isExpandable(java.lang.Object)
		 */
		public boolean isExpandable(final Object parent) {
			final ViewerFilter[] filters = fViewer.getFilters();
			final Object[] children = ((ITreeContentProvider) fViewer.getContentProvider()).getChildren(parent);
			for (Object object : children) {
				if (isEssential(object))
					return true;

				object = filter(object, parent, filters);
				if (object != null)
					return true;
			}
			return false;
		}
	}
	class UpdateViewJob extends Job {//implements Runnable {

		public UpdateViewJob() {
			super(PHPUIMessages.getString("ProjectOutlinePart.1")); //$NON-NLS-1$
			setSystem(true);
		}

		protected IStatus run(final IProgressMonitor monitor) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					if (getViewer().getInput() != currentProject)
						getViewer().setInput(currentProject);
				}
			});
			fContentProvider.postRefresh(currentProject, true);

			return Status.OK_STATUS;
		}

	}

	protected ProjectOutlineViewGroup actionGroup;

	protected IProject currentProject;
	private String initProjectName;
	protected ProjectOutlineContentProvider fContentProvider;
	private Menu fContextMenu;

	protected ProjectOutlineLabelProvider fLabelProvider;

	private ISelection fLastOpenSelection;
	private boolean fLinkingEnabled;
	private final IPartListener fPartListener = new IPartListener() {
		public void partActivated(IWorkbenchPart part) {
			if (part == ProjectOutlinePart.this) {
				return;
			}
			final PHPStructuredEditor structuredEditor = EditorUtility.getPHPStructuredEditor(part);
			if (getViewer().getTree().getVisible() && structuredEditor != null)
				updateInputForCurrentEditor(structuredEditor);
		}

		public void partBroughtToTop(IWorkbenchPart part) {
		}

		public void partClosed(IWorkbenchPart part) {
		}

		public void partDeactivated(IWorkbenchPart part) {
		}

		public void partOpened(IWorkbenchPart part) {
			final PHPStructuredEditor structuredEditor = EditorUtility.getPHPStructuredEditor(part);
			if (getViewer().getTree().getVisible() && structuredEditor != null)
				updateInputForCurrentEditor(structuredEditor);
		}
	};
	private ISelectionChangedListener fPostSelectionListener;

	private final LinkingSelectionListener fSelectionListener = new LinkingSelectionListener() {

		public void selectionChanged(IWorkbenchPart part, ISelection selection) {
			if (part instanceof IEditorPart) {
				handleUpdateInput((IEditorPart) part);
			}
			if (selection instanceof IStructuredSelection) {
				IStructuredSelection structuredSelection = (IStructuredSelection) selection;
				if (structuredSelection.size() > 0) {
					Object firstElement = structuredSelection.getFirstElement();
					if (firstElement instanceof IProject) {
						setProject((IProject) firstElement);
						return;
					}
				}
			}
			super.selectionChanged(part, selection);
		}
	};

	protected PHPTreeViewer fViewer;

	private String fWorkingSetName;

	OpenAction openEditorAction;

	private boolean showAll = false;

	private UpdateViewJob updateViewJob;

	public ProjectOutlinePart() {
	}

	private void addMouseTrackListener() {
		final Tree tree = fViewer.getTree();
		tree.addMouseTrackListener(new MouseTrackAdapter() {
			public void mouseHover(final MouseEvent e) {
				final TreeItem item = tree.getItem(new Point(e.x, e.y));
				if (item != null) {
					final Object o = item.getData();
					if (o instanceof PHPCodeData)
						tree.setToolTipText(fLabelProvider.getTooltipText(o));
				}
			}

		});
	}

	public void collapseAll() {
		try {
			fViewer.getControl().setRedraw(false);
			fViewer.collapseToLevel(getViewPartInput(), AbstractTreeViewer.ALL_LEVELS);
			fLastOpenSelection = null;
		} finally {
			fViewer.getControl().setRedraw(true);
		}
	}

	protected ProjectOutlineViewGroup createActionGroup() {
		return new ProjectOutlineViewGroup(this);
	}

	public ProjectOutlineContentProvider createContentProvider() {
		final IPreferenceStore store = PreferenceConstants.getPreferenceStore();
		final boolean showCUChildren = store.getBoolean(PreferenceConstants.SHOW_CU_CHILDREN);
		return new ProjectOutlineContentProvider(this, showCUChildren);
	}

	protected ProjectOutlineLabelProvider createLabelProvider() {
		return new ProjectOutlineLabelProvider(AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS | PHPElementLabels.M_PARAMETER_NAMES, AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS | PHPElementImageProvider.SMALL_ICONS | PHPElementImageProvider.OVERLAY_ICONS, fContentProvider);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.ViewPart#init(org.eclipse.ui.IViewSite, org.eclipse.ui.IMemento)
	 */
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		if (memento != null)
			initProjectName = memento.getString(MEMENTO_KEY_PROJECT);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.ViewPart#saveState(org.eclipse.ui.IMemento)
	 */
	public void saveState(IMemento memento) {
		if (currentProject != null)
			memento.putString(MEMENTO_KEY_PROJECT, currentProject.getName());
		super.saveState(memento);
	}

	public void createPartControl(final Composite parent) {
		getSite().getPage().addPartListener(fPartListener);
		fViewer = createViewer(parent);

		PHPElementSorter sorter = new PHPElementSorter();
		sorter.setUsingCategories(false);
		sorter.setUsingLocation(true);
		fViewer.setSorter(sorter);

		fViewer.getControl().addFocusListener(this);
		fSelectionListener.setViewer(getViewer());
		fSelectionListener.setResetEmptySelection(false);
		setProviders();
		fViewer.setUseHashlookup(true);

		setUpPopupMenu();
		initLinkingEnabled();
		actionGroup = createActionGroup();

		fPostSelectionListener = new ISelectionChangedListener() {
			public void selectionChanged(final SelectionChangedEvent event) {
				handlePostSelectionChanged(event);
			}
		};

		fViewer.addPostSelectionChangedListener(fPostSelectionListener);
		addMouseTrackListener();
		fViewer.addOpenListener(new IOpenListener() {
			public void open(final OpenEvent event) {
				fLastOpenSelection = event.getSelection();
				openEditorAction.run((IStructuredSelection) fLastOpenSelection);
			}
		});

		final IStatusLineManager slManager = getViewSite().getActionBars().getStatusLineManager();
		fViewer.addSelectionChangedListener(new StatusBarUpdater(slManager));
		updateTitle();

		final IEditorPart editorPart = getViewSite().getPage().getActiveEditor();
		updateInputForCurrentEditor(editorPart);

		openEditorAction = new OpenAction(getSite());
		fillActionBars();

		// refresh linking:
		setLinkingEnabled(isLinkingEnabled());
		if (initProjectName != null) {
			setProject(ResourcesPlugin.getWorkspace().getRoot().getProject(initProjectName));
		}

		PHPWorkspaceModelManager.getInstance().addModelListener(fContentProvider);
		fViewer.refresh();
		PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, IPHPHelpContextIds.PHP_PROJECT_OUTLINE_VIEW);
	}

	private PHPTreeViewer createViewer(final Composite composite) {
		return new ProjectOutlineTreeViewer(composite, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
	}

	public void dispose() {
		if (fContextMenu != null && !fContextMenu.isDisposed())
			fContextMenu.dispose();
		getSite().getPage().removePartListener(fPartListener);
		getSite().getPage().removePostSelectionListener(fSelectionListener);
		PHPWorkspaceModelManager.getInstance().removeModelListener(fContentProvider);
		super.dispose();
	}

	void editorActivated(final IEditorPart editor) {
	}

	private void fillActionBars() {
		final IActionBars actionBars = getViewSite().getActionBars();
		actionGroup.fillActionBars(actionBars);
	}

	String getFrameName(final Object element) {
		if (element instanceof PHPCodeData)
			return ((PHPCodeData) element).getName();
		return fLabelProvider.getText(element);
	}

	String getToolTipText(final Object element) {
		String result;
		if (!(element instanceof IResource)) {
			if (element instanceof PHPWorkspaceModelManager)
				result = PHPUIMessages.getString("PHPExplorerPart_workspace");
			else if (element instanceof PHPCodeData)
				result = PHPElementLabels.getTextLabel(element, AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS);
			else
				result = fLabelProvider.getText(element);
		} else {
			final IPath path = ((IResource) element).getFullPath();
			if (path.isRoot())
				result = PHPUIMessages.getString("PHPExplorer_title");
			else
				result = path.makeRelative().toString();
		}

		if (fWorkingSetName == null)
			return result;

		final String wsstr = MessageFormat.format(PHPUIMessages.getString("PHPExplorer_toolTip"), new String[] { fWorkingSetName });
		if (result.length() == 0)
			return wsstr;
		return MessageFormat.format(PHPUIMessages.getString("PHPExplorer_toolTip2"), new String[] { result, fWorkingSetName });
	}

	public PHPTreeViewer getViewer() {
		return fViewer;
	}

	public Object getViewPartInput() {
		if (fViewer != null)
			return fViewer.getInput();
		return null;
	}

	private void handlePostSelectionChanged(final SelectionChangedEvent event) {
		final ISelection selection = event.getSelection();
		// If the selection is the same as the one that triggered the last
		// open event then do nothing. The editor already got revealed.
		if (isLinkingEnabled() && !selection.equals(fLastOpenSelection))
			linkToEditor((IStructuredSelection) selection);
		fLastOpenSelection = selection;
	}

	public void handleUpdateInput(final IEditorPart editorPart) {
		IProject project = null;

		if (editorPart != null) {
			final PHPStructuredEditor phpEditor;
			if (editorPart instanceof PHPStructuredEditor) {
				phpEditor = (PHPStructuredEditor) editorPart;
			} else {
				phpEditor = EditorUtility.getPHPStructuredEditor(editorPart);
			}
			if (phpEditor != null) {
				PHPFileData fileData = phpEditor.getPHPFileData();
				project = PHPWorkspaceModelManager.getInstance().getProjectForFileData(fileData, currentProject);
			} else {
				final IEditorInput editorInput = editorPart.getEditorInput();
				if (editorInput instanceof FileEditorInput) {
					final FileEditorInput fileEditorInput = (FileEditorInput) editorInput;
					project = fileEditorInput.getFile().getProject();
				}
			}
		}
		if (project != null)
			setProject(project);
	}

	private void initLinkingEnabled() {
		setLinkingEnabled(PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.LINK_BROWSING_PROJECTS_TO_EDITOR));
	}

	public boolean isInCurrentProject(final Object element) {
		if (currentProject != null)
			return currentProject.equals(PHPModelUtil.getResource(element).getProject());
		return false;
	}

	boolean isLinkingEnabled() {
		return fLinkingEnabled;
	}

	public boolean isShowAll() {
		return showAll;
	}

	private void linkToEditor(final IStructuredSelection selection) {
		// ignore selection changes if the package explorer is not the active part.
		// In this case the selection change isn't triggered by a user.
		if (this != getSite().getPage().getActivePart())
			return;
		final Object obj = selection.getFirstElement();

		if (selection.size() == 1) {
			final IEditorPart part = EditorUtility.isOpenInEditor(obj);
			if (part != null) {
				final IWorkbenchPage page = getSite().getPage();
				page.bringToTop(part);
				if (obj instanceof PHPCodeData)
					EditorUtility.revealInEditor(part, (PHPCodeData) obj);
			}
		}
	}

	public void menuAboutToShow(final IMenuManager menu) {
		PHPUiPlugin.createStandardGroups(menu);
		actionGroup.setContext(new ActionContext(fViewer.getSelection()));
		actionGroup.fillContextMenu(menu);
		actionGroup.setContext(null);
	}

	void projectStateChanged(final Object root) {
		final Control ctrl = fViewer.getControl();
		if (ctrl != null && !ctrl.isDisposed() && ctrl.isVisible()) {
			fViewer.refresh(root, true);
			// trigger a syntetic selection change so that action refresh their
			// enable state.
			fViewer.setSelection(fViewer.getSelection());
		}
	}

	public void setFocus() {
		fViewer.getTree().setFocus();
	}

	public void setLinkingEnabled(final boolean enabled) {
		fLinkingEnabled = enabled;
		PreferenceConstants.getPreferenceStore().setValue(PreferenceConstants.LINK_BROWSING_PROJECTS_TO_EDITOR, enabled);

		final IWorkbenchPartSite site = getSite();
		if (site == null)
			return;
		final IWorkbenchPage page = site.getPage();
		if (page == null)
			return;

		if (enabled) {
			page.addPostSelectionListener(fSelectionListener);
			final IEditorPart editor = page.getActiveEditor();
			if (editor != null)
				editorActivated(editor);
		} else
			page.removePostSelectionListener(fSelectionListener);
	}

	public void setProject(final IProject project) {
		if (project == currentProject)
			return;
		currentProject = project;
		if (updateViewJob == null)
			updateViewJob = new UpdateViewJob();
		updateViewJob.schedule();
		actionGroup.updateActions();
	}

	private void setProviders() {
		fContentProvider = createContentProvider();
		final IPHPTreeContentProvider[] treeProviders = TreeProvider.getTreeProviders(getViewSite().getId());
		fContentProvider.setTreeProviders(treeProviders);
		fViewer.setContentProvider(fContentProvider);

		fLabelProvider = createLabelProvider();
		fLabelProvider.setTreeProviders(treeProviders);
		fViewer.setLabelProvider(fLabelProvider);
	}

	public void setShowAll(final boolean showAll) {
		if (showAll != this.showAll) {
			this.showAll = showAll;
			if (updateViewJob == null)
				updateViewJob = new UpdateViewJob();
			updateViewJob.schedule();
		}
	}

	private void setUpPopupMenu() {

		final MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(this);
		fContextMenu = menuMgr.createContextMenu(fViewer.getTree());
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(final IMenuManager mgr) {
				final ISelection selection = fViewer.getSelection();
				if (!selection.isEmpty()) {
					final IStructuredSelection s = (IStructuredSelection) selection;
					if (s.getFirstElement() instanceof PHPFunctionData) {
						//						mgr.add(action);
					}
				}
			}
		});
		fViewer.getTree().setMenu(fContextMenu);
		final IWorkbenchPartSite site = getSite();
		site.registerContextMenu(menuMgr, fViewer);
		site.setSelectionProvider(fViewer);
	}

	private void updateInputForCurrentEditor(final IEditorPart editorPart) {
		handleUpdateInput(editorPart);
	}

	void updateTitle() {
		final Object input = fViewer.getInput();
		if (input == null || input instanceof PHPWorkspaceModelManager) {
			setContentDescription(""); //$NON-NLS-1$
			setTitleToolTip(""); //$NON-NLS-1$
		} else {
			final String inputText = PHPElementLabels.getTextLabel(input, AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS);
			setContentDescription(inputText);
			setTitleToolTip(getToolTipText(input));
		}
	}
}
