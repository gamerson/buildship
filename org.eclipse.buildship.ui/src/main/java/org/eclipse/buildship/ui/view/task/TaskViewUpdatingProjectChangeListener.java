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

package org.eclipse.buildship.ui.view.task;

import java.util.Set;

import com.google.common.base.Preconditions;

import com.gradleware.tooling.toolingmodel.repository.FetchStrategy;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.ui.PlatformUI;

import org.eclipse.buildship.core.workspace.WorkspaceProjectChangeListener;

/**
 * Tracks the creation/deletion of projects in the workspace and updates the {@link TaskView}
 * accordingly.
 * <p>
 * Every time a project is added or removed from the workspace, the listener updates the content of
 * the task view.
 */
public final class TaskViewUpdatingProjectChangeListener extends WorkspaceProjectChangeListener implements IResourceChangeListener {

    private final TaskView taskView;

    public TaskViewUpdatingProjectChangeListener(TaskView taskView) {
        this.taskView = Preconditions.checkNotNull(taskView);
    }

    @Override
    protected void notifyAboutProjectAdditions(Set<IProject> projects) {
        refreshTasksView();
    }

    @Override
    protected void notifyAboutProjectRemovals(Set<IProject> projects) {
        refreshTasksView();
    }

    private void refreshTasksView() {
        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

            @Override
            public void run() {
                TaskViewUpdatingProjectChangeListener.this.taskView.reload(FetchStrategy.LOAD_IF_NOT_CACHED);
            }
        });
    }

}
