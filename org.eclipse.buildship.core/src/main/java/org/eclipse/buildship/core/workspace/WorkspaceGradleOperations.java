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

import java.util.List;

import com.gradleware.tooling.toolingmodel.OmniEclipseProject;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;

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
     * <li>As outlined in {@link #synchronizeGradleProjectWithWorkspaceProject(OmniEclipseProject, GradleBuildInWorkspace, List, ExistingDescriptorHandler, IProgressMonitor)}</li>
     * </ul>
     * </li>
     * </ol>
     *
     * @param gradleBuild           the Gradle build to synchronize
     * @param workingSets           the working set to assign the imported projects to
     * @param existingDescriptorHandler whether to keep or delete existing project descriptors
     * @param monitor               the monitor to report the progress on
     */
    void synchronizeGradleBuildWithWorkspace(GradleBuildInWorkspace gradleBuild, List<String> workingSets, ExistingDescriptorHandler existingDescriptorHandler, IProgressMonitor monitor);

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
     * <li>the Gradle settings file is written</li>
     * <li>the Gradle resource filter is set</li>
     * <li>the linked resources are set</li>
     * <li>the workspace project is further updated in case of a Java Gradle project and the workspace project having the Java nature set
     * <ul>
     * <li>update the source compatibility settings</li>
     * <li>update the set of source folders</li>
     * <li>update the Gradle classpath container</li>
     * </ul>
     * </li>
     * <li>the workspace project is converted to a Java project in case of a Java Gradle project and the workspace project not having the Java nature set yet
     * <ul>
     * <li>a Gradle classpath container is added (this triggers a synchronize through the classpath container, handled by #1.2)</li>
     * </ul>
     * </li>
     * <li>the project natures and build commands are set</li>
     * </ul>
     * </li>
     * </ol>
     * </li>
     * <li>
     * If there is an Eclipse project at the location of the Gradle project, i.e. there is a .project file in that folder, then the {@link ExistingDescriptorHandler}
     * decides whether to keep or delete that existing file. If it is deleted, the synchronization happens as if there was none. If it is kept, the synchronization is as follows:
     * <ul>
     * <li>the Eclipse project is added to the workspace</li>
     * <li>the Gradle nature is set</li>
     * <li>the Gradle settings file is written</li>
     * <li>the workspace project is further configured in case of a Java Gradle project and the workspace project having the Java nature set
     * <ul>
     * <li>the source compatibility settings are set</li>
     * </ul>
     * <li>the project natures and build commands are set</li>
     * </ul>
     * </li>
     * <li>If the there is no project in the workspace nor an Eclipse project at the location of the Gradle build, the synchronization is as follows:
     * <ul>
     * <li>an Eclipse project is created and added to the workspace</li>
     * <li>the Gradle nature is set</li>
     * <li>the Gradle settings file is written</li>
     * <li>the Gradle resource filter is set</li>
     * <li>the linked resources are set</li>
     * <li>a Java project is created in case of a Java Gradle project
     * <ul>
     * <li>a Gradle classpath container is added (this triggers a synchronize through the classpath container, handled by #1.2)</li>
     * </ul>
     * </li>
     * <li>the project natures and build commands are set</li>
     * </ul>
     * </li>
     * </ol>
     *
     * @param project               the backing Gradle project
     * @param gradleBuild           the Gradle build to which the Gradle project belongs
     * @param workingSets           the working set to assign the imported projects to
     * @param existingDescriptorHandler whether to delete or keep existing .project files
     * @param monitor               the monitor to report the progress on
     */
    void synchronizeGradleProjectWithWorkspaceProject(OmniEclipseProject project, GradleBuildInWorkspace gradleBuild, List<String> workingSets, ExistingDescriptorHandler existingDescriptorHandler, IProgressMonitor monitor);

    /**
     * Synchronizes the given Eclipse workspace project with its Gradle counterpart, if that counterpart exists. The algorithm is as follows:
     * <p/>
     * <ol>
     * <li>If the workspace project contains the Gradle nature and there is a matching Gradle project at the location of the workspace project
     * <ul>
     * <li>As outlined in {@link #synchronizeGradleProjectWithWorkspaceProject(OmniEclipseProject, GradleBuildInWorkspace, List, IProgressMonitor)}</li>
     * </ul></li>
     * <li>In all other cases
     * <ul>
     * <li>As outlined in {@link #uncoupleWorkspaceProjectFromGradle(IProject, IProgressMonitor)}</li>
     * <li>and clear the Gradle classpath container</li>
     * </ul></li>
     * </ol>
     *
     * @param workspaceProject      the project to synchronize
     * @param gradleBuild           the Gradle build to which the Gradle project is expected to belong, can be null
     * @param rootRequestAttributes the preferences used to query the Gradle build, can be null
     * @param monitor               the monitor to report the progress on
     */
    void synchronizeWorkspaceProject(IProject workspaceProject, GradleBuildInWorkspace gradleBuild, IProgressMonitor monitor);

    /**
     * Uncouples the given Eclipse workspace project from Gradle. The algorithm is as follows:
     *
     * <ol>
     * <li>the Gradle nature is removed</li>
     * <li>the Gradle settings file is removed</li>
     * <li>the Gradle resource filter is removed</li>
     * </ol>
     *
     * @param workspaceProject        the project from which to remove all Gradle specific parts
     * @param monitor                 the monitor to report the progress on
     */
    void uncoupleWorkspaceProjectFromGradle(IProject workspaceProject, IProgressMonitor monitor);

}
