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
package org.eclipse.php.internal.debug.ui.preferences;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Preferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.jface.dialogs.IPageChangedListener;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.php.internal.debug.core.PHPDebugPlugin;
import org.eclipse.php.internal.debug.core.preferences.*;
import org.eclipse.php.internal.debug.ui.Logger;
import org.eclipse.php.internal.debug.ui.PHPDebugUIMessages;
import org.eclipse.php.internal.server.core.Server;
import org.eclipse.php.internal.server.core.manager.ServersManager;
import org.eclipse.php.internal.ui.preferences.AbstractPHPPreferencePageBlock;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDEEncoding;
import org.osgi.service.prefs.BackingStoreException;

/**
 * PHP debug options preferences add-on. This add-on specifies the default
 * debugger, executable and server for the workspace or the project specific.
 * 
 * @author Shalom Gibly
 */
public class PHPDebugPreferencesBlock extends AbstractPHPPreferencePageBlock {

	private static final String UNRESOLVED_PHP_VERSION = PHPDebugUIMessages.PHPDebugPreferencesBlock_3;
	private static final String SERVERS_PAGE_ID = "org.eclipse.php.server.internal.ui.PHPServersPreferencePage"; //$NON-NLS-1$
	private static final String PHP_EXE_PAGE_ID = "org.eclipse.php.debug.ui.preferencesphps.PHPsPreferencePage"; //$NON-NLS-1$

	private Button fStopAtFirstLine;
	private Combo fDefaultServer;
	private Combo fDefaultPHPExe;
	private Combo fDebugEncodingSettings;
	private Combo fOutputEncodingSettings;
	private PreferencePage propertyPage;
	private URL autoGeneratedURL;
	private Text fAutoGeneratedURLText;
	private Text fDefaultBasePath;
	private final boolean isPropertyPage;
	private String defaultBasePath;
	private IPageValidator pageValidator = null;
	private Button fEnableCLIDebug;
	private Label fServerDebugger;
	private Label fCLIDebugger;

	public boolean isPropertyPage() {
		return isPropertyPage;
	}

	public PHPDebugPreferencesBlock(boolean isPropertyPage) {
		this.isPropertyPage = !isPropertyPage;
	}

	public void setCompositeAddon(Composite parent) {
		Composite composite = addPageContents(parent);
		addProjectPreferenceSubsection(composite);
	}

	public void initializeValues(PreferencePage propertyPage) {
		this.propertyPage = propertyPage;
		Preferences prefs = PHPProjectPreferences.getModelPreferences();
		IScopeContext[] preferenceScopes = createPreferenceScopes(propertyPage);
		IProject project = getProject(propertyPage);
		boolean stopAtFirstLine = prefs
				.getBoolean(PHPDebugCorePreferenceNames.STOP_AT_FIRST_LINE);
		boolean enableCLIDebug = prefs
				.getBoolean(PHPDebugCorePreferenceNames.ENABLE_CLI_DEBUG);
		String serverName = ServersManager.getDefaultServer(null).getName();
		String phpExeName = prefs
				.getString(PHPDebugCorePreferenceNames.DEFAULT_PHP);
		if (phpExeName == null || phpExeName.isEmpty()) {
			phpExeName = PHPDebugUIMessages.PhpDebugPreferencePage_noExeDefined;
		}
		String transferEncoding = prefs
				.getString(PHPDebugCorePreferenceNames.TRANSFER_ENCODING);
		String outputEncoding = prefs
				.getString(PHPDebugCorePreferenceNames.OUTPUT_ENCODING);
		loadServers(fDefaultServer);
		String serverDebuggerId = PHPDebugPlugin.getDebuggerId(serverName);
		boolean exeLoaded = false;
		// Update the values in case we have a project-specific settings.
		if (preferenceScopes[0] instanceof ProjectScope) {
			IEclipsePreferences node = preferenceScopes[0]
					.getNode(getPreferenceNodeQualifier());
			if (node != null && project != null) {
				if (getBasePathValue(project) == null
						|| getBasePathValue(project) == "") { //$NON-NLS-1$
					setBasePathValue(project, project.getName());
				}
				String projectServerName = ServersManager.getDefaultServer(
						project).getName();
				if (!projectServerName.equals("")) { //$NON-NLS-1$
					serverName = projectServerName;
					stopAtFirstLine = node.getBoolean(
							PHPDebugCorePreferenceNames.STOP_AT_FIRST_LINE,
							stopAtFirstLine);
					enableCLIDebug = node.getBoolean(
							PHPDebugCorePreferenceNames.ENABLE_CLI_DEBUG,
							enableCLIDebug);
					transferEncoding = node.get(
							PHPDebugCorePreferenceNames.TRANSFER_ENCODING,
							transferEncoding);
					outputEncoding = node.get(
							PHPDebugCorePreferenceNames.OUTPUT_ENCODING,
							outputEncoding);
					PHPexeItem item = PHPDebugPlugin.getPHPexeItem(project);
					if (item != null && item.getName() != null) {
						phpExeName = item.getName();
					}
					serverDebuggerId = PHPDebugPlugin.getDebuggerId(serverName);
					loadPHPExes(fDefaultPHPExe, PHPexes.getInstance()
							.getAllItems());
					exeLoaded = true;
				}
			}
		}
		if (!exeLoaded) {
			loadPHPExes(fDefaultPHPExe, PHPexes.getInstance().getAllItems());
		}
		fStopAtFirstLine.setSelection(stopAtFirstLine);
		fEnableCLIDebug.setSelection(enableCLIDebug);
		fDefaultServer.select(fDefaultServer.indexOf(serverName));
		fServerDebugger.setText(PHPDebuggersRegistry
				.getDebuggerName(serverDebuggerId));
		if (fDefaultPHPExe.indexOf(phpExeName) != -1) {
			fDefaultPHPExe.select(fDefaultPHPExe.indexOf(phpExeName));
		} else {
			fDefaultPHPExe.select(0);
		}
		PHPexeItem item = PHPexes.getInstance().getItem(
				fDefaultPHPExe.getText());
		if (item != null) {
			fCLIDebugger.setText(PHPDebuggersRegistry.getDebuggerName(item
					.getDebuggerID()));
		} else {
			fCLIDebugger
					.setText(PHPDebugUIMessages.PhpDebugPreferencePage_noExeDefined);
		}
		fDebugEncodingSettings.setText(transferEncoding);
		fOutputEncodingSettings.setText(outputEncoding);

		if (isPropertyPage()) {
			defaultBasePath = getBasePathValue(project);
			initAutoGenerateListener();
			fDefaultBasePath.setText(defaultBasePath);
		}
	}

	private String getBasePathValue(IProject project) {
		String basepath = PHPProjectPreferences.getDefaultBasePath(project);
		return basepath;
	}

	private void setBasePathValue(IProject project, String value) {
		PHPProjectPreferences.setDefaultBasePath(project, value);
	}

	public void initAutoGenerateListener() {
		fDefaultBasePath.addModifyListener(new ModifyListener() {
			public void modifyText(final ModifyEvent e) {
				try {
					try {
						if (pageValidator != null)
							pageValidator.validate(new BasePathValidator(
									fDefaultBasePath));
						refreshAutoGeneratedBaseURLDisplay();
					} catch (ControlValidationException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}

				} catch (MalformedURLException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}

		});
	}

	private String getSelectedServerName() {
		return fDefaultServer.getText();
	}

	private void dispalyAutoGeneratedURL(URL url) {
		fAutoGeneratedURLText.setText(url.toString());

	}

	public boolean performOK(boolean isProjectSpecific) {
		savePreferences(isProjectSpecific);
		return true;
	}

	public void performApply(boolean isProjectSpecific) {
		performOK(isProjectSpecific);
	}

	public boolean performCancel() {
		return true;
	}

	public void performDefaults() {
		Preferences prefs = PHPProjectPreferences.getModelPreferences();
		fStopAtFirstLine
				.setSelection(prefs
						.getDefaultBoolean(PHPDebugCorePreferenceNames.STOP_AT_FIRST_LINE));
		fEnableCLIDebug
				.setSelection(prefs
						.getDefaultBoolean(PHPDebugCorePreferenceNames.ENABLE_CLI_DEBUG));
		loadServers(fDefaultServer);
		String serverDebuggerId = PHPDebugPlugin.getDebuggerId(fDefaultServer
				.getText());
		fServerDebugger.setText(PHPDebuggersRegistry
				.getDebuggerName(serverDebuggerId));
		loadPHPExes(fDefaultPHPExe, PHPexes.getInstance().getAllItems());
		PHPexeItem item = PHPexes.getInstance().getItem(
				fDefaultPHPExe.getText());
		if (item != null) {
			fCLIDebugger.setText(PHPDebuggersRegistry.getDebuggerName(item
					.getDebuggerID()));
		}
		fDebugEncodingSettings
				.setText(prefs
						.getDefaultString(PHPDebugCorePreferenceNames.TRANSFER_ENCODING));
		fOutputEncodingSettings.setText(prefs
				.getDefaultString(PHPDebugCorePreferenceNames.OUTPUT_ENCODING));
	}

	protected String getPreferenceNodeQualifier() {
		return PHPProjectPreferences.getPreferenceNodeQualifier();
	}

	private void addProjectPreferenceSubsection(Composite composite) {
		Group defultServerGroup = new Group(composite, SWT.NONE);
		defultServerGroup
				.setText(PHPDebugUIMessages.PHPDebugPreferencesBlock_0);
		defultServerGroup.setFont(composite.getFont());
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		layout.verticalSpacing = 10;
		defultServerGroup.setLayout(layout);
		defultServerGroup.setLayoutData(new GridData(GridData.FILL_BOTH));

		Label label = addLabelControl(defultServerGroup,
				PHPDebugUIMessages.PhpDebugPreferencePage_9,
				ServersManager.DEFAULT_SERVER_PREFERENCES_KEY);
		GridData gd = new GridData();
		gd.widthHint = 90;
		label.setLayoutData(gd);
		fDefaultServer = addCombo(defultServerGroup, 2);
		addLink(defultServerGroup,
				PHPDebugUIMessages.PhpDebugPreferencePage_serversLink,
				SERVERS_PAGE_ID);
		label = addLabelControl(defultServerGroup,
				PHPDebugUIMessages.PHPDebugPreferencesBlock_ServerDebugger,
				null);
		gd = new GridData();
		gd.widthHint = 90;
		label.setLayoutData(gd);
		fServerDebugger = new Label(defultServerGroup, SWT.NONE);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalIndent = 2;
		fServerDebugger.setLayoutData(gd);

		Group defultCLIGroup = new Group(composite, SWT.NONE);
		defultCLIGroup
				.setText(PHPDebugUIMessages.PHPDebugPreferencesBlock_CLISettings);
		defultCLIGroup.setFont(composite.getFont());
		layout = new GridLayout();
		layout.numColumns = 3;
		layout.verticalSpacing = 10;
		defultCLIGroup.setLayout(layout);
		defultCLIGroup.setLayoutData(new GridData(GridData.FILL_BOTH));

		label = addLabelControl(defultCLIGroup,
				PHPDebugUIMessages.PhpDebugPreferencePage_12,
				PHPDebugCorePreferenceNames.DEFAULT_PHP);
		gd = new GridData();
		gd.widthHint = 90;
		label.setLayoutData(gd);
		fDefaultPHPExe = addCombo(defultCLIGroup, 2);
		addLink(defultCLIGroup,
				PHPDebugUIMessages.PhpDebugPreferencePage_installedPHPsLink,
				PHP_EXE_PAGE_ID);
		label = addLabelControl(defultCLIGroup,
				PHPDebugUIMessages.PHPDebugPreferencesBlock_CLIDebugger, null);
		gd = new GridData();
		gd.widthHint = 90;
		label.setLayoutData(gd);
		fCLIDebugger = new Label(defultCLIGroup, SWT.NONE);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalIndent = 2;
		fCLIDebugger.setLayoutData(gd);

		new Label(defultCLIGroup, SWT.NONE); // dummy label
		new Label(defultCLIGroup, SWT.NONE); // dummy label
		fEnableCLIDebug = new Button(defultCLIGroup, SWT.CHECK);
		fEnableCLIDebug.setText(PHPDebugUIMessages.PhpDebugPreferencePage_13);

		gd = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalIndent = 1;
		gd.horizontalSpan = 2;

		fEnableCLIDebug.setLayoutData(gd);
		fEnableCLIDebug.setData(PHPDebugCorePreferenceNames.ENABLE_CLI_DEBUG);

		new Label(composite, SWT.NONE); // dummy label

		Group encodingGroup = new Group(composite, SWT.NONE);
		encodingGroup.setText(PHPDebugUIMessages.PHPDebugPreferencesBlock_2);
		encodingGroup.setLayout(new GridLayout(2, false));
		encodingGroup.setLayoutData(new GridData(GridData.FILL_BOTH));

		addLabelControl(
				encodingGroup,
				PHPDebugUIMessages.PHPDebugPreferencesAddon_debugTransferEncoding,
				PHPDebugCorePreferenceNames.TRANSFER_ENCODING);
		fDebugEncodingSettings = addEncodingSettings(encodingGroup);

		addLabelControl(
				encodingGroup,
				PHPDebugUIMessages.PHPDebugPreferencesAddon_debugOutputEncoding,
				PHPDebugCorePreferenceNames.OUTPUT_ENCODING);
		fOutputEncodingSettings = addEncodingSettings(encodingGroup);

		ModifyListener modifyListener = new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				boolean isValid = isValidEncoding(((Combo) e.getSource())
						.getText());
				if (isValid) {
					propertyPage.setErrorMessage(null);
				} else {
					propertyPage
							.setErrorMessage(PHPDebugUIMessages.PHPDebugPreferencesAddon_unsupportedEncoding);
				}
				propertyPage.setValid(isValid);
			}
		};
		fDebugEncodingSettings.addModifyListener(modifyListener);
		fOutputEncodingSettings.addModifyListener(modifyListener);

		new Label(composite, SWT.NONE); // dummy label
		if (isPropertyPage())
			createBaseURLGroup(composite);

		new Label(composite, SWT.NONE); // dummy label

		fStopAtFirstLine = addCheckBox(composite,
				PHPDebugUIMessages.PhpDebugPreferencePage_1,
				PHPDebugCorePreferenceNames.STOP_AT_FIRST_LINE, 0);

		fDefaultPHPExe.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				PHPexeItem item = PHPexes.getInstance().getItem(
						fDefaultPHPExe.getText());
				if (item != null) {
					fCLIDebugger.setText(PHPDebuggersRegistry
							.getDebuggerName(item.getDebuggerID()));
				} else {
					fCLIDebugger
							.setText(PHPDebugUIMessages.PhpDebugPreferencePage_noExeDefined);
				}
			}
		});

		fDefaultServer.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				try {
					PHPDebugPreferencesBlock.this
							.refreshAutoGeneratedBaseURLDisplay();
					String serverDebuggerId = PHPDebugPlugin
							.getDebuggerId(fDefaultServer.getText());
					fServerDebugger.setText(PHPDebuggersRegistry
							.getDebuggerName(serverDebuggerId));
				} catch (MalformedURLException e1) {
					// safe Malformed URL - exisiting server
				}
			}
		});
	}

	private void createBaseURLGroup(Composite composite) {
		Group baseURLGroup = createGroup(composite,
				PHPDebugUIMessages.PHPDebugPreferencesBlock_9);

		addBasePathLabelAndText(baseURLGroup);
		addBaseURLLabelAndText(baseURLGroup);
	}

	private void addBaseURLLabelAndText(Group baseURLGroup) {
		addLabelControl(baseURLGroup,
				PHPDebugUIMessages.PHPDebugPreferencesBlock_10,
				PHPDebugCorePreferenceNames.TRANSFER_ENCODING);

		addBaseURLText(baseURLGroup);

	}

	private URL generateBaseURL(Server server, IPath basePath)
			throws MalformedURLException {
		URL serverUrl = server.getRootURL();
		IPath file = new Path(serverUrl.getFile());
		if (!file.isEmpty()) {
			file = file.append(basePath);
		} else {
			file = basePath;
		}
		URL generatedBaseURL = new URL(serverUrl.getProtocol(),
				serverUrl.getHost(), serverUrl.getPort(), file.toString());
		return generatedBaseURL;
	}

	private Text addBaseURLText(Composite parent) {
		fAutoGeneratedURLText = addText(parent, 2, SWT.RESIZE | SWT.READ_ONLY);
		return fAutoGeneratedURLText;
	}

	private void addBasePathLabelAndText(Group baseURLGroup) {
		addLabelControl(baseURLGroup,
				PHPDebugUIMessages.PHPDebugPreferencesBlock_11,
				PHPDebugCorePreferenceNames.TRANSFER_ENCODING);
		addBasePathText(baseURLGroup);
	}

	private Text addBasePathText(Composite parent) {
		fDefaultBasePath = addText(parent, 2, SWT.BORDER | SWT.RESIZE);
		return fDefaultBasePath;
	}

	private IPath getDefaultBasePath() {
		if (fDefaultBasePath != null)
			return new Path(fDefaultBasePath.getText());

		return null;
	}

	private Server getDefaultServer() {
		return ServersManager.getServer(fDefaultServer.getText());
	}

	private void displayDefaultBaseURL(URL generatedBaseURL) {
		fAutoGeneratedURLText.setText(generatedBaseURL.toString());
	}

	private Group createGroup(Composite composite, String groupName) {
		Group newGroup = new Group(composite, SWT.NONE);
		newGroup.setText(groupName);
		newGroup.setLayout(new GridLayout(2, false));
		newGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
		return newGroup;
	}

	private void loadPHPExes(Combo combo, PHPexeItem[] items) {
		combo.removeAll();
		if (items == null || items.length == 0) {
			combo.add(PHPDebugUIMessages.PhpDebugPreferencePage_noExeDefined);
			combo.select(0);
			return;
		}
		for (PHPexeItem element : items) {
			String name = element.getName();
			if (null == name)
				name = UNRESOLVED_PHP_VERSION;
			combo.add(name);
		}
		combo.select(0);
	}

	private void loadServers(Combo combo) {
		combo.removeAll();
		Server[] servers = ServersManager.getServers();
		if (servers != null) {
			for (Server element : servers) {
				combo.add(element.getName());
			}
			// select first item in list
			if (combo.getItemCount() > 0) {
				combo.select(0);
			}
		}
	}

	private void addLink(Composite parent, String label,
			final String propertyPageID) {
		Link link = new Link(parent, SWT.NONE);
		link.setFont(parent.getFont());
		link.setLayoutData(new GridData(SWT.END, SWT.BEGINNING, false, false));
		link.setText(label);
		link.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(
						PHPDebugPreferencesBlock.this.propertyPage.getShell(),
						propertyPageID, null, null);
				dialog.setBlockOnOpen(true);
				dialog.addPageChangedListener(new IPageChangedListener() {
					public void pageChanged(PageChangedEvent event) {
						refreshDebuggers();
					}
				});
				dialog.open();
				if (!fDefaultServer.isDisposed()
						&& !fDefaultPHPExe.isDisposed()) {
					refreshDebuggers();
				}
			}
		});
	}

	private void refreshDebuggers() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				String selectedServer = fDefaultServer.getText();
				loadServers(fDefaultServer);
				int index = fDefaultServer.indexOf(selectedServer);
				if (index != -1) {
					fDefaultServer.select(index);
				}
				String serverDebuggerId = PHPDebugPlugin
						.getDebuggerId(fDefaultServer.getText());
				fServerDebugger.setText(PHPDebuggersRegistry
						.getDebuggerName(serverDebuggerId));
				String selectedPHP = fDefaultPHPExe.getText();
				loadPHPExes(fDefaultPHPExe, PHPexes.getInstance().getAllItems());
				index = fDefaultPHPExe.indexOf(selectedPHP);
				fDefaultPHPExe.select(index != -1 ? index : 0);
				PHPexeItem item = PHPexes.getInstance().getItem(
						fDefaultPHPExe.getText());
				if (item != null) {
					fCLIDebugger.setText(PHPDebuggersRegistry
							.getDebuggerName(item.getDebuggerID()));
				} else {
					fCLIDebugger
							.setText(PHPDebugUIMessages.PhpDebugPreferencePage_noExeDefined);
				}
			}
		});
	}

	private void selectComboItem(Combo combo, int itemIndex) {
		if (itemIndex < 0) {
			if (combo.getItemCount() > 0) {
				combo.select(0);
			}
		} else {
			combo.select(itemIndex);
		}
	}

	private Combo addEncodingSettings(Composite parent) {
		Combo encodingCombo = new Combo(parent, SWT.NONE);
		GridData data = new GridData();
		encodingCombo.setLayoutData(data);

		List encodings = IDEEncoding.getIDEEncodings();
		String[] encodingStrings = new String[encodings.size()];
		encodings.toArray(encodingStrings);
		encodingCombo.setItems(encodingStrings);

		return encodingCombo;
	}

	private Combo addCombo(Composite parent, int horizontalIndent) {
		Combo combo = new Combo(parent, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalIndent = horizontalIndent;
		combo.setLayoutData(gd);
		return combo;
	}

	private Text addText(Composite parent, int horizontalIndent, int style) {
		Text text = new Text(parent, style);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalIndent = horizontalIndent;
		text.setLayoutData(gd);
		return text;
	}

	private void savePreferences(boolean isProjectSpecific) {
		// TODO - Might do the same for the default server
		Preferences prefs = PHPProjectPreferences.getModelPreferences();
		IScopeContext[] preferenceScopes = createPreferenceScopes(propertyPage);
		IEclipsePreferences debugUINode = preferenceScopes[0]
				.getNode(getPreferenceNodeQualifier());
		String phpExe = fDefaultPHPExe.getText();
		IProject project = getProject(propertyPage);
		if (isProjectSpecific && debugUINode != null
				&& preferenceScopes[0] instanceof ProjectScope
				&& project != null) {
			debugUINode.putBoolean(
					PHPDebugCorePreferenceNames.STOP_AT_FIRST_LINE,
					fStopAtFirstLine.getSelection());
			debugUINode.putBoolean(
					PHPDebugCorePreferenceNames.ENABLE_CLI_DEBUG,
					fEnableCLIDebug.getSelection());
			if (!PHPDebugUIMessages.PhpDebugPreferencePage_noExeDefined
					.equals(phpExe)) {
				debugUINode
						.put(PHPDebugCorePreferenceNames.DEFAULT_PHP, phpExe);
			}
			debugUINode.put(PHPDebugCorePreferenceNames.TRANSFER_ENCODING,
					fDebugEncodingSettings.getText());
			debugUINode.put(PHPDebugCorePreferenceNames.OUTPUT_ENCODING,
					fOutputEncodingSettings.getText());

			ServersManager.setDefaultServer(project, fDefaultServer.getText());
			Server server = ServersManager.getServer(fDefaultServer.getText());
			setBasePathValue(project, fDefaultBasePath.getText());
		} else {
			if (project == null) {
				// Workspace settings
				prefs.setValue(PHPDebugCorePreferenceNames.STOP_AT_FIRST_LINE,
						fStopAtFirstLine.getSelection());
				prefs.setValue(PHPDebugCorePreferenceNames.ENABLE_CLI_DEBUG,
						fEnableCLIDebug.getSelection());
				prefs.setValue(PHPDebugCorePreferenceNames.TRANSFER_ENCODING,
						fDebugEncodingSettings.getText());
				prefs.setValue(PHPDebugCorePreferenceNames.OUTPUT_ENCODING,
						fOutputEncodingSettings.getText());
				if (!PHPDebugUIMessages.PhpDebugPreferencePage_noExeDefined
						.equals(phpExe)) {
					prefs.setValue(PHPDebugCorePreferenceNames.DEFAULT_PHP,
							phpExe);
				}
				ServersManager.setDefaultServer(project,
						fDefaultServer.getText());
			} else {
				if (debugUINode != null) {
					// Removed a project specific
					debugUINode
							.remove(PHPDebugCorePreferenceNames.STOP_AT_FIRST_LINE);
					debugUINode
							.remove(PHPDebugCorePreferenceNames.ENABLE_CLI_DEBUG);
					debugUINode.remove(PHPDebugCorePreferenceNames.DEFAULT_PHP);
					ServersManager.setDefaultServer(project, (Server) null);
					debugUINode
							.remove(PHPDebugCorePreferenceNames.TRANSFER_ENCODING);
					debugUINode
							.remove(PHPDebugCorePreferenceNames.OUTPUT_ENCODING);
					prefs.setValue(
							PHPDebugCorePreferenceNames.DEFAULT_BASE_PATH,
							defaultBasePath);
				}
			}
		}
		try {
			debugUINode.flush();
			PHPDebugPlugin.getDefault().savePluginPreferences();
		} catch (BackingStoreException e) {
			Logger.logException(e);
		}
	}

	private String getServerDebuggerId() {
		return PHPDebugPlugin.getDebuggerId(fDefaultServer.getText());
	}

	/**
	 * Returns whether or not the given encoding is valid.
	 * 
	 * @param enc
	 *            the encoding to validate
	 * @return <code>true</code> if the encoding is valid, <code>false</code>
	 *         otherwise
	 * @see org.eclipse.ui.ide.dialogs.AbstractEncodingFieldEditor.isValidEncoding
	 *      (String)
	 */
	private boolean isValidEncoding(String enc) {
		try {
			return Charset.isSupported(enc);
		} catch (IllegalCharsetNameException e) {
			// This is a valid exception
			return false;
		}
	}

	private void resolveAndSetBaseURL() throws MalformedURLException {
		Server server = getDefaultServer();
		IPath basePath = getDefaultBasePath();
		if (basePath != null) {
			URL baseURL = generateBaseURL(server, basePath);
			displayDefaultBaseURL(baseURL);
		}
	}

	private void refreshAutoGeneratedBaseURLDisplay()
			throws MalformedURLException {
		if (!isPropertyPage) {
			return;
		}
		IPath path = new Path(fDefaultBasePath.getText());
		defaultBasePath = fDefaultBasePath.getText();

		Server server = ServersManager.getServer(getSelectedServerName());
		autoGeneratedURL = generateBaseURL(server, path);
		dispalyAutoGeneratedURL(autoGeneratedURL);
	}

	public void setValidator(IPageValidator pageValidator) {
		this.pageValidator = pageValidator;
	}

}
