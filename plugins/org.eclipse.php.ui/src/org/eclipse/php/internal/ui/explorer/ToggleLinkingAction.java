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

import org.eclipse.jface.action.Action;
import org.eclipse.php.internal.ui.IPHPHelpContextIds;
import org.eclipse.php.internal.ui.PHPUIMessages;
import org.eclipse.php.internal.ui.util.PHPPluginImages;
import org.eclipse.ui.PlatformUI;

/**
 * This action toggles whether this php explorer links its selection to the active
 * editor.
 * 
 * @since 2.1
 */
public class ToggleLinkingAction extends Action {

	ExplorerPart fExplorerPart;

	/**
	 * Constructs a new action.
	 */
	public ToggleLinkingAction(ExplorerPart explorer) {
		super(PHPUIMessages.getString("ToggleLinkingAction_label"));
		setDescription(PHPUIMessages.getString("ToggleLinkingAction_description"));
		setToolTipText(PHPUIMessages.getString("ToggleLinkingAction_tooltip"));
		PHPPluginImages.setLocalImageDescriptors(this, "synced.gif"); //$NON-NLS-1$		
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IPHPHelpContextIds.PHP_EXPLORER_VIEW);

		setChecked(explorer.isLinkingEnabled());
		fExplorerPart = explorer;
	}

	/**
	 * Runs the action.
	 */
	public void run() {
		fExplorerPart.setLinkingEnabled(isChecked());
	}

}
