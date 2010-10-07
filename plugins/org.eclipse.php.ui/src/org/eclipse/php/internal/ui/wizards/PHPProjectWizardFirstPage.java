/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Zend Technologies
 *******************************************************************************/
package org.eclipse.php.internal.ui.wizards;

import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.*;
import org.eclipse.dltk.core.DLTKCore;
import org.eclipse.dltk.core.environment.EnvironmentManager;
import org.eclipse.dltk.core.environment.IEnvironment;
import org.eclipse.dltk.core.environment.IFileHandle;
import org.eclipse.dltk.core.internal.environment.LocalEnvironment;
import org.eclipse.dltk.internal.ui.wizards.NewWizardMessages;
import org.eclipse.dltk.internal.ui.wizards.dialogfields.*;
import org.eclipse.dltk.ui.DLTKUIPlugin;
import org.eclipse.dltk.ui.environment.IEnvironmentUI;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.php.internal.core.PHPVersion;
import org.eclipse.php.internal.server.core.Server;
import org.eclipse.php.internal.server.core.manager.ServersManager;
import org.eclipse.php.internal.ui.IPHPHelpContextIds;
import org.eclipse.php.internal.ui.PHPUIMessages;
import org.eclipse.php.internal.ui.PHPUiPlugin;
import org.eclipse.php.internal.ui.preferences.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

/**
 * The first page of the <code>SimpleProjectWizard</code>.
 */
public class PHPProjectWizardFirstPage extends WizardPage implements
		IPHPProjectCreateWizardPage {
	public PHPProjectWizardFirstPage() {
		super(PAGE_NAME);
		setPageComplete(false);
		setTitle(NewWizardMessages.ScriptProjectWizardFirstPage_page_title);
		setDescription(NewWizardMessages.ScriptProjectWizardFirstPage_page_description);
		fInitialName = ""; //$NON-NLS-1$
	}

	private static final String PAGE_NAME = NewWizardMessages.ScriptProjectWizardFirstPage_page_title;
	public static final String ERROR_MESSAGE = "ErrorMessage";

	protected Validator fPdtValidator;
	protected String fInitialName;
	protected NameGroup fNameGroup;
	protected DetectGroup fDetectGroup;
	protected VersionGroup fVersionGroup;
	protected JavaScriptSupportGroup fJavaScriptSupportGroup;
	protected LayoutGroup fLayoutGroup;
	protected LocationGroup fPHPLocationGroup;
	protected WizardFragment fragment;

	public void createControl(Composite parent) {
		initializeDialogUnits(parent);
		final Composite composite = new Composite(parent, SWT.NULL);
		composite.setFont(parent.getFont());
		composite.setLayout(initGridLayout(new GridLayout(1, false), false));
		composite.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		// create UI elements
		fNameGroup = new NameGroup(composite, fInitialName);
		fPHPLocationGroup = new LocationGroup(composite);

		CompositeData data = new CompositeData();
		data.setParetnt(composite);
		data.setSettings(getDialogSettings());
		data.setObserver(fPHPLocationGroup);
		fragment = (WizardFragment) Platform.getAdapterManager().loadAdapter(
				data, PHPProjectWizardFirstPage.class.getName());

		fVersionGroup = new VersionGroup(composite);
		fLayoutGroup = new LayoutGroup(composite);
		fJavaScriptSupportGroup = new JavaScriptSupportGroup(composite, this);

		fDetectGroup = new DetectGroup(composite);

		// establish connections
		fNameGroup.addObserver(fPHPLocationGroup);
		fDetectGroup.addObserver(fLayoutGroup);

		fPHPLocationGroup.addObserver(fDetectGroup);
		// initialize all elements
		fNameGroup.notifyObservers();
		// create and connect validator
		fPdtValidator = new Validator();

		fNameGroup.addObserver(fPdtValidator);
		fPHPLocationGroup.addObserver(fPdtValidator);

		setControl(composite);
		Dialog.applyDialogFont(composite);

		// set the focus to the project name
		fNameGroup.postSetFocus();

		setHelpContext(composite);
	}

	protected void setHelpContext(Composite parent) {
		PlatformUI.getWorkbench().getHelpSystem().setHelp(parent,
				IPHPHelpContextIds.CREATING_PHP_PROJECTS);
	}

	public URI getLocationURI() {
		IEnvironment environment = getEnvironment();
		return environment.getURI(fPHPLocationGroup.getLocation());
	}

	public IEnvironment getEnvironment() {
		return fPHPLocationGroup.getEnvironment();
	}

	/**
	 * Creates a project resource handle for the current project name field
	 * value.
	 * <p>
	 * This method does not create the project resource; this is the
	 * responsibility of <code>IProject::create</code> invoked by the new
	 * project resource wizard.
	 * </p>
	 * 
	 * @return the new project resource handle
	 */
	public IProject getProjectHandle() {
		return ResourcesPlugin.getWorkspace().getRoot().getProject(
				fNameGroup.getName());
	}

	public String getProjectName() {
		return fNameGroup.getName();
	}

	public boolean isInWorkspace() {
		return fPHPLocationGroup.isInWorkspace();
	}

	public boolean isInLocalServer() {
		return fPHPLocationGroup.isInLocalServer();
	}

	public boolean getDetect() {
		return fDetectGroup.mustDetect();
	}

	/**
	 * returns whether this project layout is "detailed" - meaning tree
	 * structure - one folder for source, one for resources
	 * 
	 * @return
	 */
	public boolean hasPhpSourceFolder() {
		return fLayoutGroup != null && fLayoutGroup.isDetailedLayout();
	}

	/**
	 * Initialize a grid layout with the default Dialog settings.
	 */
	public GridLayout initGridLayout(GridLayout layout, boolean margins) {
		layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		if (margins) {
			layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
			layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		} else {
			layout.marginWidth = 0;
			layout.marginHeight = 0;
		}
		return layout;
	}

	/**
	 * Show a warning when the project location contains files.
	 */
	protected final class DetectGroup extends Observable implements Observer,
			SelectionListener {
		private final Link fHintText;
		private Label fIcon;
		private boolean fDetect;

		public DetectGroup(Composite parent) {

			Composite composite = new Composite(parent, SWT.NONE);
			composite
					.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
			GridLayout layout = new GridLayout(2, false);
			layout.horizontalSpacing = 10;
			composite.setLayout(layout);

			fIcon = new Label(composite, SWT.LEFT);
			fIcon.setImage(Dialog.getImage(Dialog.DLG_IMG_MESSAGE_WARNING));
			GridData gridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
			fIcon.setLayoutData(gridData);
			fIcon.setVisible(false);

			fHintText = new Link(composite, SWT.WRAP);
			fHintText.setFont(composite.getFont());
			gridData = new GridData(GridData.FILL, SWT.FILL, true, true);
			gridData.widthHint = convertWidthInCharsToPixels(50);
			gridData.heightHint = convertHeightInCharsToPixels(3);
			fHintText.setLayoutData(gridData);
		}

		private boolean isValidProjectName(String name) {
			if (name.length() == 0) {
				return false;
			}
			final IWorkspace workspace = DLTKUIPlugin.getWorkspace();
			return workspace.validateName(name, IResource.PROJECT).isOK()
					&& workspace.getRoot().findMember(name) == null;
		}

		public void update(Observable o, Object arg) {
			if (o instanceof LocationGroup) {
				boolean oldDetectState = fDetect;
				IPath location = fPHPLocationGroup.getLocation();
				if (fPHPLocationGroup.isInWorkspace()) {
					if (!isValidProjectName(getProjectName())) {
						fDetect = false;
					} else {
						IEnvironment environment = fPHPLocationGroup
								.getEnvironment();
						final IFileHandle directory = environment
								.getFile(location.append(getProjectName()));
						fDetect = directory.isDirectory();
					}
				} else {
					IEnvironment environment = fPHPLocationGroup
							.getEnvironment();
					if (location.toPortableString().length() > 0) {
						final IFileHandle directory = environment
								.getFile(location);
						fDetect = directory.isDirectory();
					}
				}
				if (oldDetectState != fDetect) {
					setChanged();
					notifyObservers();
					if (fDetect) {
						fHintText.setVisible(true);
						fHintText
								.setText(NewWizardMessages.ScriptProjectWizardFirstPage_DetectGroup_message);
					} else {
						fHintText.setVisible(false);
					}

				}
			}
		}

		public boolean mustDetect() {
			return fDetect;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.swt.events.SelectionListener#widgetSelected(org.eclipse
		 * .swt.events.SelectionEvent)
		 */
		public void widgetSelected(SelectionEvent e) {
			widgetDefaultSelected(e);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.swt.events.SelectionListener#widgetDefaultSelected(org
		 * .eclipse.swt.events.SelectionEvent)
		 */
		public void widgetDefaultSelected(SelectionEvent e) {
			if (DLTKCore.DEBUG) {
				System.err
						.println("DetectGroup show compilancePreferencePage..."); //$NON-NLS-1$
			}

		}
	}

	/**
	 * Request a project name. Fires an event whenever the text field is
	 * changed, regardless of its content.
	 */
	public final class NameGroup extends Observable implements
			IDialogFieldListener {
		protected final StringDialogField fNameField;

		public NameGroup(Composite composite, String initialName) {
			final Composite nameComposite = new Composite(composite, SWT.NONE);
			nameComposite.setFont(composite.getFont());
			nameComposite.setLayout(initGridLayout(new GridLayout(2, false),
					true));
			nameComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			// text field for project name
			fNameField = new StringDialogField();
			fNameField
					.setLabelText(NewWizardMessages.ScriptProjectWizardFirstPage_NameGroup_label_text);
			fNameField.setDialogFieldListener(this);
			setName(initialName);
			fNameField.doFillIntoGrid(nameComposite, 2);
			LayoutUtil.setHorizontalGrabbing(fNameField.getTextControl(null));
		}

		protected void fireEvent() {
			setChanged();
			notifyObservers();
		}

		public String getName() {
			return fNameField.getText().trim();
		}

		public void postSetFocus() {
			fNameField.postSetFocusOnDialogField(getShell().getDisplay());
		}

		public void setName(String name) {
			fNameField.setText(name);
		}

		public void dialogFieldChanged(DialogField field) {
			fireEvent();
		}
	}

	/**
	 * Validate this page and show appropriate warnings and error
	 * NewWizardMessages.
	 */
	public final class Validator implements Observer {
		public void update(Observable o, Object arg) {
			final IWorkspace workspace = DLTKUIPlugin.getWorkspace();
			final String name = fNameGroup.getName();
			// check whether the project name field is empty
			if (name.length() == 0) {
				setErrorMessage(null);
				setMessage(NewWizardMessages.ScriptProjectWizardFirstPage_Message_enterProjectName);
				setPageComplete(false);
				return;
			}
			// check whether the project name is valid
			final IStatus nameStatus = workspace.validateName(name,
					IResource.PROJECT);
			if (!nameStatus.isOK()) {
				setErrorMessage(nameStatus.getMessage());
				setPageComplete(false);
				return;
			}
			// check whether project already exists
			final IProject handle = getProjectHandle();

			if (!isInLocalServer()) {
				if (handle.exists()) {
					setErrorMessage(NewWizardMessages.ScriptProjectWizardFirstPage_Message_projectAlreadyExists);
					setPageComplete(false);
					return;
				}
			}

			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot()
					.getProjects();
			String newProjectNameLowerCase = name.toLowerCase();
			for (IProject currentProject : projects) {
				String existingProjectName = currentProject.getName();
				if (existingProjectName.toLowerCase().equals(
						newProjectNameLowerCase)) {
					setErrorMessage(NewWizardMessages.ScriptProjectWizardFirstPage_Message_projectAlreadyExists);
					setPageComplete(false);
					return;
				}
			}

			final String location = fPHPLocationGroup.getLocation()
					.toOSString();
			// check whether location is empty
			if (location.length() == 0) {
				setErrorMessage(null);
				setMessage(NewWizardMessages.ScriptProjectWizardFirstPage_Message_enterLocation);
				setPageComplete(false);
				return;
			}
			// check whether the location is a syntactically correct path
			if (!Path.EMPTY.isValidPath(location)) {
				setErrorMessage(NewWizardMessages.ScriptProjectWizardFirstPage_Message_invalidDirectory);
				setPageComplete(false);
				return;
			}
			// check whether the location has the workspace as prefix
			IPath projectPath = Path.fromOSString(location);
			if (!fPHPLocationGroup.isInWorkspace()
					&& Platform.getLocation().isPrefixOf(projectPath)) {
				setErrorMessage(NewWizardMessages.ScriptProjectWizardFirstPage_Message_cannotCreateInWorkspace);
				setPageComplete(false);
				return;
			}
			// If we do not place the contents in the workspace validate the
			// location.
			if (!fPHPLocationGroup.isInWorkspace()) {
				IEnvironment environment = getEnvironment();
				if (EnvironmentManager.isLocal(environment)) {
					final IStatus locationStatus = workspace
							.validateProjectLocation(handle, projectPath);
					if (!locationStatus.isOK()) {
						setErrorMessage(locationStatus.getMessage());
						setPageComplete(false);
						return;
					}
				}
			}

			if (fragment != null) {
				fragment.getWizardModel().putObject("ProjectName",
						fNameGroup.getName());
				if (!fragment.isComplete()) {
					setErrorMessage((String) fragment.getWizardModel()
							.getObject(ERROR_MESSAGE));
					setPageComplete(false);
					return;
				}
			}

			setPageComplete(true);
			setErrorMessage(null);
			setMessage(null);
		}
	}

	/**
	 * GUI for controlling whether a new PHP project should include JavaScript
	 * support or not
	 * 
	 * @author alon
	 * 
	 */
	public class JavaScriptSupportGroup implements SelectionListener {

		private final Group fGroup;
		protected Button fEnableJavaScriptSupport;

		public boolean shouldSupportJavaScript() {
			return PHPUiPlugin.getDefault().getPreferenceStore().getBoolean(
					(PreferenceConstants.JavaScriptSupportEnable));
		}

		public JavaScriptSupportGroup(Composite composite,
				WizardPage projectWizardFirstPage) {
			final int numColumns = 3;
			fGroup = new Group(composite, SWT.NONE);
			fGroup.setFont(composite.getFont());

			fGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			fGroup.setLayout(initGridLayout(new GridLayout(numColumns, false),
					true));
			fGroup.setText(PHPUIMessages.JavaScriptSupportGroup_OptionBlockTitle); //$NON-NLS-1$

			fEnableJavaScriptSupport = new Button(fGroup, SWT.CHECK | SWT.RIGHT);
			fEnableJavaScriptSupport
					.setText(PHPUIMessages.JavaScriptSupportGroup_EnableSupport); //$NON-NLS-1$
			fEnableJavaScriptSupport.setLayoutData(new GridData(SWT.BEGINNING,
					SWT.CENTER, false, false));
			fEnableJavaScriptSupport.addSelectionListener(this);
			fEnableJavaScriptSupport.setSelection(PHPUiPlugin.getDefault()
					.getPreferenceStore().getBoolean(
							(PreferenceConstants.JavaScriptSupportEnable)));
		}

		public void widgetDefaultSelected(SelectionEvent e) {
		}

		public void widgetSelected(SelectionEvent e) {
			// PHPUiPlugin.getDefault().getPreferenceStore().setValue(
			// (PreferenceConstants.JavaScriptSupportEnable),
			// fEnableJavaScriptSupport.getSelection());
		}

		public boolean getSelection() {
			return fEnableJavaScriptSupport.getSelection();
		}

	}

	/**
	 * Request a project layout.
	 */
	public class LayoutGroup implements Observer, SelectionListener,
			IDialogFieldListener {

		private final SelectionButtonDialogField fStdRadio, fSrcBinRadio;
		private Group fGroup;
		private Link fPreferenceLink;

		public LayoutGroup(Composite composite) {
			final int numColumns = 3;

			fStdRadio = new SelectionButtonDialogField(SWT.RADIO);
			fStdRadio
					.setLabelText(PHPUIMessages.LayoutGroup_OptionBlock_ProjectSrc); //$NON-NLS-1$
			fStdRadio.setDialogFieldListener(this);

			fSrcBinRadio = new SelectionButtonDialogField(SWT.RADIO);
			fSrcBinRadio
					.setLabelText(PHPUIMessages.LayoutGroup_OptionBlock_SrcResources); //$NON-NLS-1$
			fSrcBinRadio.setDialogFieldListener(this);

			// getting Preferences default choice
			boolean useSrcBin = PreferenceConstants.getPreferenceStore()
					.getBoolean(PreferenceConstants.SRCBIN_FOLDERS_IN_NEWPROJ);

			fSrcBinRadio.setSelection(useSrcBin);
			fStdRadio.setSelection(!useSrcBin);

			// createContent
			fGroup = new Group(composite, SWT.NONE);
			fGroup.setFont(composite.getFont());
			fGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			fGroup.setLayout(initGridLayout(new GridLayout(numColumns, false),
					true));
			fGroup.setText(PHPUIMessages.LayoutGroup_OptionBlock_Title); //$NON-NLS-1$

			fStdRadio.doFillIntoGrid(fGroup, 3);
			LayoutUtil
					.setHorizontalGrabbing(fStdRadio.getSelectionButton(null));

			fSrcBinRadio.doFillIntoGrid(fGroup, 2);

			fPreferenceLink = new Link(fGroup, SWT.NONE);
			fPreferenceLink
					.setText(PHPUIMessages.ToggleLinkingAction_link_description); //$NON-NLS-1$
			fPreferenceLink.setLayoutData(new GridData(SWT.END, SWT.BEGINNING,
					true, false));
			fPreferenceLink.addSelectionListener(this);
			fPreferenceLink.setEnabled(true);

			updateEnableState();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Observer#update(java.util.Observable,
		 * java.lang.Object)
		 */
		public void update(Observable o, Object arg) {
			updateEnableState();
		}

		private void updateEnableState() {
			if (fDetectGroup == null)
				return;

			final boolean detect = fDetectGroup.mustDetect();
			fStdRadio.setEnabled(!detect);
			fSrcBinRadio.setEnabled(!detect);

			if (fGroup != null) {
				fGroup.setEnabled(!detect);
			}
		}

		/**
		 * Return <code>true</code> if the user specified to create
		 * 'application' and 'public' folders.
		 * 
		 * @return returns <code>true</code> if the user specified to create
		 *         'source' and 'bin' folders.
		 */
		public boolean isDetailedLayout() {
			return fSrcBinRadio.isSelected();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.swt.events.SelectionListener#widgetSelected(org.eclipse
		 * .swt.events.SelectionEvent)
		 */
		public void widgetSelected(SelectionEvent e) {
			widgetDefaultSelected(e);
		}

		/*
		 * @see
		 * org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener
		 * #dialogFieldChanged(org.eclipse.jdt.internal.ui.wizards.dialogfields.
		 * DialogField)
		 * 
		 * @since 3.5
		 */
		public void dialogFieldChanged(DialogField field) {
			updateEnableState();
		}

		public void widgetDefaultSelected(SelectionEvent e) {

			String prefID = PHPProjectLayoutPreferencePage.PREF_ID;

			Map data = null;
			PreferencesUtil.createPreferenceDialogOn(getShell(), prefID,
					new String[] { prefID }, data).open();
		}
	}

	/**
	 * Request a location. Fires an event whenever the checkbox or the location
	 * field is changed, regardless of whether the change originates from the
	 * user or has been invoked programmatically.
	 */
	public class LocationGroup extends Observable implements Observer,
			IStringButtonAdapter, IDialogFieldListener {
		protected final SelectionButtonDialogField fWorkspaceRadio;
		protected final SelectionButtonDialogField fExternalRadio;
		protected final StringButtonDialogField fLocation;
		// protected final ComboDialogField fEnvironment;
		private IEnvironment[] environments;

		private String fPreviousExternalLocation;
		private int localEnv;
		protected SelectionButtonDialogField fLocalServerRadio;
		protected ComboDialogField fSeverLocationList;
		private String[] docRootArray;

		private static final String DIALOGSTORE_LAST_EXTERNAL_LOC = DLTKUIPlugin.PLUGIN_ID
				+ ".last.external.project"; //$NON-NLS-1$

		public LocationGroup(Composite composite) {
			final int numColumns = 3;
			final Group group = new Group(composite, SWT.None);
			group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			group.setLayout(initGridLayout(new GridLayout(numColumns, false),
					true));
			group.setText(NewWizardMessages.ScriptProjectWizardFirstPage_LocationGroup_title);
			fWorkspaceRadio = new SelectionButtonDialogField(SWT.RADIO);
			fWorkspaceRadio.setDialogFieldListener(this);
			fWorkspaceRadio
					.setLabelText(NewWizardMessages.ScriptProjectWizardFirstPage_LocationGroup_workspace_desc);
			fExternalRadio = new SelectionButtonDialogField(SWT.RADIO);
			fExternalRadio.setDialogFieldListener(this);
			fExternalRadio
					.setLabelText(NewWizardMessages.ScriptProjectWizardFirstPage_LocationGroup_external_desc);
			fLocation = new StringButtonDialogField(this);
			fLocation.setDialogFieldListener(this);
			fLocation
					.setLabelText(NewWizardMessages.ScriptProjectWizardFirstPage_LocationGroup_locationLabel_desc);
			fLocation
					.setButtonLabel(NewWizardMessages.ScriptProjectWizardFirstPage_LocationGroup_browseButton_desc);
			// fExternalRadio.attachDialogField(fLocation);
			fWorkspaceRadio.setSelection(true);
			fExternalRadio.setSelection(false);
			fPreviousExternalLocation = ""; //$NON-NLS-1$
			fWorkspaceRadio.doFillIntoGrid(group, numColumns);
			fExternalRadio.doFillIntoGrid(group, numColumns);
			fLocation.doFillIntoGrid(group, numColumns);
			LayoutUtil.setHorizontalGrabbing(fLocation.getTextControl(null));
			fExternalRadio.attachDialogFields(new DialogField[] { fLocation });

			environments = EnvironmentManager.getEnvironments();
			String[] items = new String[environments.length];
			localEnv = 0;
			for (int i = 0; i < items.length; i++) {
				items[i] = environments[i].getName();
				if (environments[i].isLocal()) {
					localEnv = i;
				}
			}

			// check if any of the server can provide local doc root.

			Server[] servers = ServersManager.getServers();
			List<String> docRoots = new ArrayList<String>();
			for (int i = 0; i < servers.length; i++) {
				String docRoot = servers[i].getDocumentRoot();
				String isLocal = servers[i].getAttribute(Server.LOCALSERVER,
						null);
				if (isLocal != null && docRoot != null
						&& !"".equals(docRoot.trim())) { //$NON-NLS-1$
					docRoots.add(docRoot);
				}
			}

			if (docRoots.size() > 0) {
				fLocalServerRadio = new SelectionButtonDialogField(SWT.RADIO);
				fLocalServerRadio.setDialogFieldListener(this);
				fLocalServerRadio
						.setLabelText(PHPUIMessages.PHPProjectWizardFirstPage_localServerLabel); //$NON-NLS-1$
				fLocalServerRadio.setSelection(false);
				fLocalServerRadio.doFillIntoGrid(group, numColumns);
				fSeverLocationList = new ComboDialogField(SWT.READ_ONLY);
				fSeverLocationList
						.setLabelText(NewWizardMessages.ScriptProjectWizardFirstPage_LocationGroup_locationLabel_desc);
				fSeverLocationList.doFillIntoGrid(group, numColumns);
				fSeverLocationList.setEnabled(false);
				docRootArray = new String[docRoots.size()];
				docRoots.toArray(docRootArray);
				fSeverLocationList.setItems(docRootArray);
				fSeverLocationList.selectItem(0);
				fLocalServerRadio.attachDialogField(fSeverLocationList);
				fWorkspaceRadio.setSelection(false);
				fLocalServerRadio.setSelection(true);
			}

		}

		public boolean isInLocalServer() {
			return fLocalServerRadio != null && fLocalServerRadio.isSelected();
		}

		protected void fireEvent() {
			setChanged();
			notifyObservers();
		}

		protected String getDefaultPath(String name) {
			IEnvironment environment = this.getEnvironment();
			if (environment != null && environment.isLocal()) {
				final IPath path = Platform.getLocation().append(name);
				return path.toOSString();
			} else {
				return ""; //$NON-NLS-1$
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Observer#update(java.util.Observable,
		 * java.lang.Object)
		 */
		public void update(Observable o, Object arg) {
			if (isInWorkspace()) {
				fLocation.setText(getDefaultPath(fNameGroup.getName()));
			}
			if (docRootArray != null && docRootArray.length > 0) {
				int index = fSeverLocationList.getSelectionIndex();
				String[] items = getDocItems(docRootArray);
				fSeverLocationList.setItems(items);
				fSeverLocationList.selectItem(index);
			}

			fireEvent();
		}

		private String[] getDocItems(String[] docRootArray) {
			String[] items = new String[docRootArray.length];
			for (int i = 0; i < docRootArray.length; i++) {
				items[i] = docRootArray[i] + File.separator
						+ fNameGroup.getName(); //$NON-NLS-1$
			}
			return items;
		}

		public IPath getLocation() {
			if (isInWorkspace()) {
				return Platform.getLocation();
			}
			if (isInLocalServer()) {
				return new Path(fSeverLocationList.getText());
			}
			return new Path(fLocation.getText().trim());
		}

		public boolean isInWorkspace() {
			return fWorkspaceRadio.isSelected();
		}

		public IEnvironment getEnvironment() {
			if (fWorkspaceRadio.isSelected()) {
				return EnvironmentManager
						.getEnvironmentById(LocalEnvironment.ENVIRONMENT_ID);
			}
			// return environments[fEnvironment.getSelectionIndex()];
			return environments[localEnv];
		}

		public void changeControlPressed(DialogField field) {
			IEnvironment environment = getEnvironment();
			IEnvironmentUI environmentUI = (IEnvironmentUI) environment
					.getAdapter(IEnvironmentUI.class);
			if (environmentUI != null) {
				String selectedDirectory = environmentUI
						.selectFolder(getShell());

				if (selectedDirectory != null) {
					fLocation.setText(selectedDirectory);
					DLTKUIPlugin.getDefault().getDialogSettings().put(
							DIALOGSTORE_LAST_EXTERNAL_LOC, selectedDirectory);
				}
			}
		}

		public void dialogFieldChanged(DialogField field) {
			if (field == fWorkspaceRadio) {
				final boolean checked = fWorkspaceRadio.isSelected();
				if (checked) {
					fPreviousExternalLocation = fLocation.getText();
					fLocation.setText(getDefaultPath(fNameGroup.getName()));
				} else {
					IEnvironment environment = this.getEnvironment();
					if (environment != null && environment.isLocal()) {
						fLocation.setText(fPreviousExternalLocation);
					} else {
						fLocation.setText(""); //$NON-NLS-1$
					}
				}
			}

			fireEvent();
		}

	}

	/**
	 * Request a location. Fires an event whenever the checkbox or the location
	 * field is changed, regardless of whether the change originates from the
	 * user or has been invoked programmatically.
	 */
	public class VersionGroup extends Observable implements Observer,
			IStringButtonAdapter, IDialogFieldListener, SelectionListener {
		public final SelectionButtonDialogField fDefaultValues;
		protected final SelectionButtonDialogField fCustomValues;

		public PHPVersionConfigurationBlock fConfigurationBlock;

		private static final String DIALOGSTORE_LAST_EXTERNAL_LOC = DLTKUIPlugin.PLUGIN_ID
				+ ".last.external.project"; //$NON-NLS-1$
		private Link fPreferenceLink;

		public VersionGroup(Composite composite) {
			final int numColumns = 3;
			final Group group = new Group(composite, SWT.NONE);
			group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			group.setLayout(initGridLayout(new GridLayout(numColumns, false),
					true));
			group.setText(PHPUIMessages.VersionGroup_OptionBlock_Title);//$NON-NLS-1$ 
			fDefaultValues = new SelectionButtonDialogField(SWT.RADIO);
			fDefaultValues.setDialogFieldListener(this);
			fDefaultValues
					.setLabelText(PHPUIMessages.VersionGroup_OptionBlock_fDefaultValues);//$NON-NLS-1$ 
			fCustomValues = new SelectionButtonDialogField(SWT.RADIO);
			fCustomValues.setDialogFieldListener(this);
			fCustomValues
					.setLabelText(PHPUIMessages.VersionGroup_OptionBlock_fCustomValues);//$NON-NLS-1$ 

			fDefaultValues.setSelection(true);
			fCustomValues.setSelection(false);

			fDefaultValues.doFillIntoGrid(group, numColumns);
			fCustomValues.doFillIntoGrid(group, 2);

			fConfigurationBlock = createConfigurationBlock(
					new IStatusChangeListener() {
						public void statusChanged(IStatus status) {
						}
					}, (IProject) null, null);
			fConfigurationBlock.createContents(group);
			fConfigurationBlock.setEnabled(false);
			// fPreferenceLink = new Link(fGroup, SWT.NONE);
			// fPreferenceLink.setText(PHPUIMessages.getString("ToggleLinkingAction_link_description"));
			// //fPreferenceLink.setLayoutData(new GridData(GridData.END,
			// GridData.END, false, false));
			// fPreferenceLink.setLayoutData(new GridData(SWT.END,
			// SWT.BEGINNING, true, false));
			// fPreferenceLink.addSelectionListener(this);
			// fPreferenceLink.setEnabled(true);

		}

		protected PHPVersionConfigurationBlock createConfigurationBlock(
				IStatusChangeListener listener, IProject project,
				IWorkbenchPreferenceContainer container) {
			return new PHPVersionConfigurationBlock(listener, project,
					container, true);
		}

		protected void fireEvent() {
			setChanged();
			notifyObservers();
		}

		//

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Observer#update(java.util.Observable,
		 * java.lang.Object)
		 */
		public void update(Observable o, Object arg) {
			fireEvent();
		}

		public void changeControlPressed(DialogField field) {
			IEnvironment environment = getEnvironment();
			IEnvironmentUI environmentUI = (IEnvironmentUI) environment
					.getAdapter(IEnvironmentUI.class);
			if (environmentUI != null) {
				String selectedDirectory = environmentUI
						.selectFolder(getShell());

				if (selectedDirectory != null) {
					// fLocation.setText(selectedDirectory);
					DLTKUIPlugin.getDefault().getDialogSettings().put(
							DIALOGSTORE_LAST_EXTERNAL_LOC, selectedDirectory);
				}
			}
		}

		public void dialogFieldChanged(DialogField field) {
			if (field == fDefaultValues) {
				final boolean checked = fDefaultValues.isSelected();
				if (null != fConfigurationBlock)
					this.fConfigurationBlock.setEnabled(!checked);
			}

			fireEvent();
		}

		public void widgetSelected(SelectionEvent e) {
			widgetDefaultSelected(e);
		}

		public void widgetDefaultSelected(SelectionEvent e) {
			String prefID = PHPInterpreterPreferencePage.PREF_ID;
			Map data = null;
			PreferencesUtil.createPreferenceDialogOn(getShell(), prefID,
					new String[] { prefID }, data).open();
			if (!fCustomValues.isSelected()) {
				fConfigurationBlock.performRevert();
			}
		}

	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);

		IWizardPage currentPage = getContainer().getCurrentPage();
		if (!visible && currentPage != null) {
			// going forward from 1st page to 2nd one
			if (currentPage instanceof IPHPProjectCreateWizardPage) {
				((IPHPProjectCreateWizardPage) currentPage).initPage();
			}
		}

	}

	public void initPage() {
	}

	public WizardModel getWizardData() {
		if (fragment != null) {
			return fragment.getWizardModel();
		}
		return null;
	}

	public void performFinish(IProgressMonitor monitor) {
		Display.getDefault().asyncExec(new Runnable() {

			public void run() {
				PHPUiPlugin.getDefault().getPreferenceStore().setValue(
						(PreferenceConstants.JavaScriptSupportEnable),
						fJavaScriptSupportGroup.getSelection());
			}
		});
	}

	public boolean shouldSupportJavaScript() {

		return fJavaScriptSupportGroup != null
				&& fJavaScriptSupportGroup.shouldSupportJavaScript();
	}

	public boolean isDefaultVersionSelected() {
		return fVersionGroup != null
				&& fVersionGroup.fDefaultValues.isSelected();
	}

	public boolean getUseAspTagsValue() {
		return fVersionGroup != null
				&& fVersionGroup.fConfigurationBlock.getUseAspTagsValue();
	}

	public PHPVersion getPHPVersionValue() {
		if (fVersionGroup != null) {
			return fVersionGroup.fConfigurationBlock.getPHPVersionValue();
		}
		return null;
	}
}
