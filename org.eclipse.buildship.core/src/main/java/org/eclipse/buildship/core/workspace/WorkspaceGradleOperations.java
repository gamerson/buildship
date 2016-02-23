/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.core.workspace;

import com.gradleware.tooling.toolingmodel.OmniEclipseProject;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;

/**
 * Provides operations related to querying and modifying the Gradle specific parts of
 * Eclipse elements that exist in a workspace.
 */
public interface WorkspaceGradleOperations {

    /**
     * Synchronizes the given Gradle build with the Eclipse workspace. The algorithm is as follows:
     * <p/>
     * <ol>
     * <li>Uncouple all open workspace projects for which there is no corresponding Gradle project in the Gradle build anymore
     * <ul>
     * <li>As outlined in {@link #uncoupleWorkspaceProjectFromGradle(IProject, IProgressMonitor)}</li>
     * </ul>
     * </li>
     * <li>Synchronize all Gradle projects of the Gradle build with the Eclipse workspace project counterparts:
     * <ul>
     * <li>As outlined in {@link #synchronizeGradleProjectWithWorkspaceProject(OmniEclipseProject, GradleBuildInWorkspace, NewProjectHandler, IProgressMonitor)}</li>
     * </ul>
     * </li>
     * </ol>
     *
     * @param gradleBuild           the Gradle build to synchronize
     * @param newProjectHandler     what to do with projects that are not imported yet
     * @param monitor               the monitor to report the progress on
     */
    void synchronizeGradleBuildWithWorkspace(GradleBuildInWorkspace gradleBuild, NewProjectHandler newProjectHandler, IProgressMonitor monitor);

    /**
     * Synchronizes the given Gradle project with its Eclipse workspace project counterpart. The algorithm is as follows:
     * <p/>
     * <ol>
     * <li>
     * If there is a project in the workspace at the location of the Gradle project, the synchronization is as follows:
     * <ol>
     * <li>If the workspace project is closed, the project is left unchanged</li>
     * <li>If the workspace project is open:
     * <ul>
     * <li>the Gradle nature is set</li>
     * <li>the project name is updated</li>
     * <li>the Gradle settings file is written</li>
     * <li>the Gradle resource filter is set</li>
     * <li>the linked resources are set</li>
     * <li>the project natures and build commands are set</li>
     * <li>if the Gradle project is a Java project
     * <ul>
     * <li>the Java nature is added </li>
     * <li>the source compatibility settings are updated</li>
     * <li>the set of source folders is updated</li>
     * <li>the Gradle classpath container is updated</li>
     * </ul>
     * </li>
     * </ul>
     * </li>
     * </ol>
     * </li>
     * <li>
     * If there is an Eclipse project at the location of the Gradle project, i.e. there is a .project file in that folder, then
     * the {@link NewProjectHandler} decides whether to import it. If the project is imported
     * into the workspace, then it is synchronized as specified above. The {@link NewProjectHandler} decides whether to keep or overwrite the existing .project file.
     * </li>
     * <li>If the there is no project in the workspace, nor an Eclipse project at the location of the Gradle build, then the {@link NewProjectHandler} decides whether to
     * import it. If it is imported, it is synchronized as specified above.
     * </li>
     * </ol>
     *
     * @param project               the backing Gradle project
     * @param gradleBuild           the Gradle build to which the Gradle project belongs
     * @param newProjectHandler     what to do with projects that are not yet imported
     * @param monitor               the monitor to report the progress on
     */
    void synchronizeGradleProjectWithWorkspaceProject(OmniEclipseProject project, GradleBuildInWorkspace gradleBuild, NewProjectHandler newProjectHandler, IProgressMonitor monitor);

    /**
     * Uncouples the given Eclipse workspace project from Gradle. The algorithm is as follows:
     *
     * <ol>
     * <li>the Gradle resource filter is removed</li>
     * <li>the Gradle nature is removed</li>
     * <li>the Gradle settings file is removed</li>
     * </ol>
     *
     * @param workspaceProject        the project from which to remove all Gradle specific parts
     * @param monitor                 the monitor to report the progress on
     */
    void uncoupleWorkspaceProjectFromGradle(IProject workspaceProject, IProgressMonitor monitor);

    /**
     * Updates the Gradle classpath container elements in a target Java project with the entries
     * defined in an {@link OmniEclipseProject} instance.
     * <p/>
     * If the target Java project doesn't have the Gradle classpath container on its classpath, then
     * this method has no effect.
     *
     * @param javaProject the target project containing the classpath container to update
     * @param project the model supplying the entries to add to the classpath container
     * @param monitor the monitor to report the progress on
     */
    void synchronizeClasspathContainer(IJavaProject javaProject, OmniEclipseProject project, IProgressMonitor monitor);

}
