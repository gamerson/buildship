/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.buildship.core.workspace;

import java.util.Set;

import com.gradleware.tooling.toolingmodel.OmniEclipseWorkspace;
import com.gradleware.tooling.toolingmodel.repository.FetchStrategy;

import org.eclipse.core.resources.IProject;

/**
 * Refreshes the {@link OmniEclipseWorkspace} when a project is deleted.
 *
 * Refresh on project addition is not necessary, as that is already handled by the
 * {@link ImportGradleProjectJob}.
 *
 * @author Stefan Oehme
 *
 */
public class GradleWorkspaceRefreshingProjectRemovalListener extends WorkspaceProjectChangeListener {

    @Override
    protected void notifyAboutProjectAdditions(Set<IProject> addedProjects) {
    }

    @Override
    protected void notifyAboutProjectRemovals(Set<IProject> deletedProjects) {
        new RefreshGradleProjectsJob(FetchStrategy.LOAD_IF_NOT_CACHED, NewProjectHandler.DONT_IMPORT).schedule();
    }

}
