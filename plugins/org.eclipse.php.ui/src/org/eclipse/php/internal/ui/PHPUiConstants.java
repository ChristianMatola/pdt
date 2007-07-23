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
package org.eclipse.php.internal.ui;

public interface PHPUiConstants {
	public static final int INTERNAL_ERROR = 10001;
	public static final String PHP_EDITOR_ID = "org.eclipse.php.editor";
	
			// disable cut/copy/paste/rename etc on php elements within file
	public static final boolean DISABLE_ELEMENT_REFACTORING=true;
	public static final String CONTENT_ASSIST_PROFERENCE_PAGE = "org.eclipse.php.ui.preferences.PHPContentAssistPreferencePage";
	public static final String RSE_TEMP_PROJECT_NATURE_ID = "org.eclipse.rse.ui.remoteSystemsTempNature"; //$NON-NLS-1$
	public static final String RSE_TEMP_PROJECT_NAME = "RemoteSystemsTempFiles"; //$NON-NLS-1$
}
