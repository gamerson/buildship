/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
