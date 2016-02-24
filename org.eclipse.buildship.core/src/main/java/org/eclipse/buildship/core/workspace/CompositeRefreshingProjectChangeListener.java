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

import java.util.Set;

import org.eclipse.core.resources.IProject;

/**
 * Refreshes the corresponding composite when a project is deleted.
 *
 * Refresh on project addition is not necessary, as that is already handled by the
 * {@link ImportGradleProjectJob}.
 *
 * @author Stefan Oehme
 *
 */
public class CompositeRefreshingProjectChangeListener extends WorkspaceProjectChangeListener {

    @Override
    protected void notifyAboutProjectAdditions(Set<IProject> addedProjects) {
    }

    @Override
    protected void notifyAboutProjectRemovals(Set<IProject> deletedProjects) {
        SynchronizeCompositeJob.newRefreshWorkspaceJob().schedule();
    }

}
