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
package org.eclipse.php.internal.core.filenetwork;

import java.util.*;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.dltk.ast.ASTVisitor;
import org.eclipse.dltk.ast.declarations.ModuleDeclaration;
import org.eclipse.dltk.ast.expressions.Expression;
import org.eclipse.dltk.core.*;
import org.eclipse.dltk.core.search.IDLTKSearchScope;
import org.eclipse.dltk.core.search.SearchEngine;
import org.eclipse.php.internal.core.Logger;
import org.eclipse.php.internal.core.compiler.ast.nodes.Include;
import org.eclipse.php.internal.core.compiler.ast.nodes.Scalar;
import org.eclipse.php.internal.core.compiler.ast.parser.ASTUtils;
import org.eclipse.php.internal.core.filenetwork.ReferenceTree.Node;
import org.eclipse.php.internal.core.model.IncludeField;
import org.eclipse.php.internal.core.model.PhpModelAccess;
import org.eclipse.php.internal.core.util.PHPSearchEngine;
import org.eclipse.php.internal.core.util.PHPSearchEngine.IncludedFileResult;
import org.eclipse.php.internal.core.util.PHPSearchEngine.ResourceResult;
import org.eclipse.php.internal.core.util.PHPSearchEngine.Result;

/**
 * This utility is used for resolving reference dependencies between files.
 * Usage examples:
 * <p>
 * I. Filter model elements that accessible from current source module:
 * 
 * <pre>
 * ReferenceTree referenceTree = FileNetworkUtility.buildReferencedFilesTree(currentSourceModule, null);
 * List&lt;IModelElement&gt; filteredElements = new LinkedList&lt;IModelElement&amp;gt();
 * for (IModelElement element : elements) {
 *   if (referenceTree.find(element.getSourceModule()) {
 *     filteredElements.add(element);
 *   }
 * }
 * </pre>
 * 
 * </p>
 * <p>
 * II. Find all files that reference current file and rebuild them
 * 
 * <pre>
 * ReferenceTree referenceTree = FileNetworkUtility.buildReferencingFilesTree(currentSourceModule, null);
 * LinkedList&lt;Node&gt; nodesQ = new  LinkedList&lt;Node&amp;gt();
 * nodesQ.addFirst(referenceTree.getRoot());
 * while (!nodesQ.isEmpty()) {
 *   Node node = nodesQ.removeLast();
 *   rebuildFile (node.getFile());
 *   if (node.getChildren() != null) {
 *     for (Node child : node.getChildren()) {
 *       nodesQ.addFirst(child);
 *     }
 *   }
 * }
 * </pre>
 * 
 * </p>
 */
public class FileNetworkUtility {

	/**
	 * Analyzes file dependences, and builds tree of all source modules that
	 * reference the given source module.
	 * 
	 * @param file
	 *            Source module
	 * @param monitor
	 *            Progress monitor
	 * @return reference tree
	 */
	public static ReferenceTree buildReferencingFilesTree(ISourceModule file,
			IProgressMonitor monitor) {

		HashSet<ISourceModule> processedFiles = new HashSet<ISourceModule>();
		processedFiles.add(file);

		Node root = new Node(file);
		internalBuildReferencingFilesTree(root, processedFiles, monitor);

		return new ReferenceTree(root);
	}

	private static void internalBuildReferencingFilesTree(Node root,
			Set<ISourceModule> processedFiles, IProgressMonitor monitor) {

		ISourceModule file = root.getFile();

		IDLTKSearchScope scope = SearchEngine.createSearchScope(file
				.getScriptProject());

		// Find all includes to the current source module in mixin:
		IField[] includes = PhpModelAccess.getDefault().findIncludes(
				file.getPath().lastSegment(), scope);
		Arrays.sort(includes, new Comparator<IField>() {
			public int compare(IField o1, IField o2) {
				return o1.getElementName().compareTo(o2.getElementName());
			}
		});

		for (IField include : includes) {

			// Candidate that includes the original source module:
			ISourceModule referencingFile = include.getSourceModule();

			// Try to resolve include:
			ISourceModule testFile = findSourceModule(referencingFile,
					((IncludeField) include).getFilePath());

			// If this is the correct include (that means that included file is
			// the original file):
			if (file.equals(testFile)
					&& !processedFiles.contains(referencingFile)) {
				processedFiles.add(referencingFile);
				Node node = new Node(referencingFile);
				root.addChild(node);
			}
		}

		Collection<Node> children = root.getChildren();
		if (children != null) {
			for (Node child : children) {
				internalBuildReferencingFilesTree(child, processedFiles,
						monitor);
			}
		}
	}

	/**
	 * Analyzes file dependences, and builds tree of all source modules, which
	 * are referenced by the given source module.
	 * 
	 * @param file
	 *            Source module
	 * @param monitor
	 *            Progress monitor
	 * @return reference tree
	 */
	public static ReferenceTree buildReferencedFilesTree(ISourceModule file,
			IProgressMonitor monitor) {
		HashSet<ISourceModule> processedFiles = new HashSet<ISourceModule>();
		processedFiles.add(file);

		Node root = new Node(file);
		try {
			internalBuildReferencedFilesTree(root, processedFiles, monitor);
		} catch (CoreException e) {
			Logger.logException(e);
		}

		return new ReferenceTree(root);
	}

	private static void internalBuildReferencedFilesTree(final Node root,
			Set<ISourceModule> processedFiles, IProgressMonitor monitor)
			throws CoreException {
		ISourceModule sourceModule = root.getFile();

		final List<String> includes = new LinkedList<String>();

		ModuleDeclaration moduleDeclaration = SourceParserUtil
				.getModuleDeclaration(sourceModule);

		ASTVisitor visitor = new ASTVisitor() {
			public boolean visit(Expression expr) throws ModelException {
				if (expr instanceof Include) {
					Expression fileExpr = ((Include) expr).getExpr();
					if (fileExpr instanceof Scalar) {
						String fileName = ASTUtils
								.stripQuotes(((Scalar) fileExpr).getValue());
						includes.add(fileName);
					}
				}
				return true;
			}
		};

		try {
			moduleDeclaration.traverse(visitor);
		} catch (Exception e) {
			Logger.logException(e);
		}

		for (String filePath : includes) {
			ISourceModule testFile = findSourceModule(sourceModule, filePath);
			if (testFile != null && !processedFiles.contains(testFile)) {
				processedFiles.add(testFile);
				root.addChild(new Node(testFile));
			}
		}

		Collection<Node> children = root.getChildren();
		if (children != null) {
			for (Node child : children) {
				internalBuildReferencedFilesTree(child, processedFiles, monitor);
			}
		}
	}

	public static ISourceModule findSourceModule(ISourceModule from, String path) {
		ISourceModule sourceModule = null;

		IProject currentProject = from.getScriptProject().getProject();
		String currentScriptDir = from.getParent().getPath().toString();
		String currentWorkingDir = currentScriptDir; // currentProject.getFullPath().toString();
		Result<?, ?> result = PHPSearchEngine.find(path, currentWorkingDir,
				currentScriptDir, currentProject);

		if (result instanceof ResourceResult) {
			// workspace file
			ResourceResult resResult = (ResourceResult) result;
			IResource resource = resResult.getFile();
			sourceModule = (ISourceModule) DLTKCore.create(resource);
		} else if (result instanceof IncludedFileResult) {
			IncludedFileResult incResult = (IncludedFileResult) result;
			IProjectFragment[] projectFragments = incResult
					.getProjectFragments();
			if (projectFragments != null) {
				String folderPath = ""; //$NON-NLS-1$
				String moduleName = path;
				int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
				if (i != -1) {
					folderPath = path.substring(0, i);
					moduleName = path.substring(i + 1);
				}
				for (IProjectFragment projectFragment : projectFragments) {
					IScriptFolder scriptFolder = projectFragment
							.getScriptFolder(folderPath);
					if (scriptFolder != null) {
						sourceModule = scriptFolder.getSourceModule(moduleName);
						if (sourceModule != null) {
							break;
						}
					}
				}
			}
		} else {
			// XXX: add support for external files
		}

		return sourceModule;
	}
}
