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
import java.util.Set;

import org.gradle.tooling.ProgressListener;

import com.google.common.collect.ImmutableList;

import com.gradleware.tooling.toolingmodel.OmniEclipseWorkspace;
import com.gradleware.tooling.toolingmodel.repository.CompositeModelRepository;
import com.gradleware.tooling.toolingmodel.repository.FetchStrategy;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;
import com.gradleware.tooling.toolingmodel.repository.TransientRequestAttributes;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.console.ProcessStreams;
import org.eclipse.buildship.core.util.progress.DelegatingProgressListener;
import org.eclipse.buildship.core.util.progress.ToolingApiWorkspaceJob;
import org.eclipse.buildship.core.workspace.internal.DefaultGradleBuildInWorkspace;

/**
 * Base class for jobs that synchronize a set of Gradle projects with their workspace counterparts.
 */
public abstract class SynchronizeGradleProjectsJob extends ToolingApiWorkspaceJob {
    private final FetchStrategy fetchStrategy;

    public SynchronizeGradleProjectsJob(String description, boolean notifyUserOfBuildFailures, FetchStrategy fetchStrategy) {
        super(description, notifyUserOfBuildFailures);
        this.fetchStrategy = fetchStrategy;
    }

    @Override
    protected final void runToolingApiJobInWorkspace(IProgressMonitor monitor) {
        monitor.beginTask("Synchronizing Gradle projects with workspace", 100);
        beforeSynchronization(new SubProgressMonitor(monitor, 10));
        IJobManager manager = Job.getJobManager();
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        manager.beginRule(workspaceRoot, monitor);
        try {
            Set<FixedRequestAttributes> requestAttributes = getBuildsToSynchronize();
            if (requestAttributes.isEmpty()) {
                return;
            }
            OmniEclipseWorkspace gradleWorkspace = forceReloadEclipseWorkspace(requestAttributes, new SubProgressMonitor(monitor, 40));
            for (FixedRequestAttributes attributes : requestAttributes) {
                GradleBuildInWorkspace gradleBuild = DefaultGradleBuildInWorkspace.from(gradleWorkspace, attributes);
                CorePlugin.workspaceGradleOperations().synchronizeGradleBuildWithWorkspace(
                    gradleBuild,
                    getNewProjectHandler(gradleBuild),
                    new SubProgressMonitor(monitor, 50)
                );
            }
        } finally {
            manager.endRule(workspaceRoot);
        }
    }

    /**
     * Invoked before the synchronization is started.
     */
    protected void beforeSynchronization(IProgressMonitor progressMonitor) {
    }

    /**
     * The request attributes of the root projects to synchronize.
     */
    protected abstract Set<FixedRequestAttributes> getBuildsToSynchronize();

    /**
     * Determines what to do with not yet imported projects in the given build.
     */
    protected NewProjectHandler getNewProjectHandler(GradleBuildInWorkspace gradleBuild) {
        return NewProjectHandler.IMPORT_AND_MERGE;
    }

    private OmniEclipseWorkspace forceReloadEclipseWorkspace(Set<FixedRequestAttributes> fixedRequestAttributes, IProgressMonitor monitor) {
        monitor.beginTask("Loading workspace model", IProgressMonitor.UNKNOWN);
        try {
            ProcessStreams streams = CorePlugin.processStreamsProvider().getBackgroundJobProcessStreams();
            List<ProgressListener> listeners = ImmutableList.<ProgressListener> of(new DelegatingProgressListener(monitor));
            TransientRequestAttributes transientAttributes = new TransientRequestAttributes(false, streams.getOutput(), streams.getError(), streams.getInput(), listeners,
                    ImmutableList.<org.gradle.tooling.events.ProgressListener> of(), getToken());
            CompositeModelRepository repository = CorePlugin.modelRepositoryProvider().getCompositeModelRepository(fixedRequestAttributes);
            return repository.fetchEclipseWorkspace(transientAttributes, this.fetchStrategy);
        } finally {
            monitor.done();
        }
    }
}
