/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.core.workspace;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gradleware.tooling.toolingmodel.repository.FetchStrategy;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

/**
 * Synchronizes each Gradle root project with the Eclipse workspace.
 */
public class RefreshGradleProjectsJob extends SynchronizeGradleProjectsJob {

    private final NewProjectHandler newProjectHandler;

    public RefreshGradleProjectsJob() {
        this(FetchStrategy.FORCE_RELOAD, NewProjectHandler.IMPORT_AND_MERGE);
    }

    public RefreshGradleProjectsJob(FetchStrategy fetchStrategy, NewProjectHandler newProjectHandler) {
        super("Synchronize workspace projects with Gradle counterparts", true, fetchStrategy);
        this.newProjectHandler = newProjectHandler;
    }

    @Override
    protected Set<FixedRequestAttributes> getBuildsToSynchronize() {
        List<IProject> allProjects = Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects());
        return getUniqueRootProjects(allProjects);
    }

    @Override
    protected NewProjectHandler getNewProjectHandler(GradleBuildInWorkspace gradleBuild) {
        return this.newProjectHandler;
    }
}
