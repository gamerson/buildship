/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 *     Simon Scholz <simon.scholz@vogella.com> - Bug 473348
 */

package org.eclipse.buildship.core.workspace;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.gradle.jarjar.com.google.common.collect.Sets;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.ProgressListener;

import com.google.common.collect.ImmutableList;

import com.gradleware.tooling.toolingmodel.OmniEclipseWorkspace;
import com.gradleware.tooling.toolingmodel.repository.CompositeModelRepository;
import com.gradleware.tooling.toolingmodel.repository.FetchStrategy;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;
import com.gradleware.tooling.toolingmodel.repository.TransientRequestAttributes;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.configuration.GradleProjectNature;
import org.eclipse.buildship.core.configuration.ProjectConfiguration;
import org.eclipse.buildship.core.console.ProcessStreams;
import org.eclipse.buildship.core.util.progress.DelegatingProgressListener;
import org.eclipse.buildship.core.util.progress.ToolingApiWorkspaceJob;
import org.eclipse.buildship.core.workspace.internal.DefaultGradleBuildInWorkspace;

/**
 * Synchronizes a Java workspace project with its Gradle counterpart. In contrast to
 * {@link RefreshGradleProjectsJob}, this works on individual Eclipse projects instead of Gradle builds.
 */
public final class SynchronizeJavaWorkspaceProjectJob extends ToolingApiWorkspaceJob {

    private final IJavaProject project;
    private final FetchStrategy fetchStrategy;

    public SynchronizeJavaWorkspaceProjectJob(IJavaProject project) {
        this(project, FetchStrategy.LOAD_IF_NOT_CACHED);
    }

    public SynchronizeJavaWorkspaceProjectJob(IJavaProject project, FetchStrategy fetchStrategy) {
        super(String.format("Synchronize Java workspace project %s", project.getProject().getName()), false);
        this.project = project;
        this.fetchStrategy = fetchStrategy;
    }

    @Override
    protected void runToolingApiJobInWorkspace(IProgressMonitor monitor) throws Exception {
        monitor.beginTask(String.format("Synchronizing Java workspace project %s", this.project.getProject().getName()), 100);

        // all Java operations use the workspace root as a scheduling rule
        // see org.eclipse.jdt.internal.core.JavaModelOperation#getSchedulingRule()
        // if this rule ends during the import then other projects jobs see an
        // inconsistent workspace state, consequently we keep the rule for the whole import
        IJobManager manager = Job.getJobManager();
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        manager.beginRule(workspaceRoot, monitor);
        try {
            synchronizeWorkspaceProject(this.project, monitor, getToken());
        } finally {
            manager.endRule(workspaceRoot);
        }

        // monitor is closed by caller in super class
    }

    private void synchronizeWorkspaceProject(IJavaProject javaProject, IProgressMonitor monitor, CancellationToken token) throws CoreException {
        FixedRequestAttributes rootRequestAttributes = null;
        GradleBuildInWorkspace gradleBuild = null;

        IProject project = javaProject.getProject();
        if (GradleProjectNature.INSTANCE.isPresentOn(project)) {
            // find the Gradle project corresponding to the workspace project and update it accordingly
            ProjectConfiguration configuration = CorePlugin.projectConfigurationManager().readProjectConfiguration(project);
            rootRequestAttributes = configuration.getRequestAttributes();
            OmniEclipseWorkspace gradleWorkspace = fetchEclipseGradleBuild(rootRequestAttributes, monitor, token);
            gradleBuild = DefaultGradleBuildInWorkspace.from(gradleWorkspace, rootRequestAttributes);
        }

        CorePlugin.workspaceGradleOperations().synchronizeWorkspaceProject(project, gradleBuild, monitor);
    }

    private OmniEclipseWorkspace fetchEclipseGradleBuild(FixedRequestAttributes fixedRequestAttributes, IProgressMonitor monitor, CancellationToken token) {
        ProcessStreams streams = CorePlugin.processStreamsProvider().getBackgroundJobProcessStreams();
        List<ProgressListener> progressListeners = ImmutableList.<ProgressListener>of(new DelegatingProgressListener(monitor));
        TransientRequestAttributes transientAttributes = new TransientRequestAttributes(false, streams.getOutput(), streams.getError(), null, progressListeners,
                ImmutableList.<org.gradle.tooling.events.ProgressListener>of(), token);
        Set<FixedRequestAttributes> allRequestAttributes = Sets.newLinkedHashSet();
        List<IProject> allProjects = Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects());
        allRequestAttributes.addAll(getUniqueRootProjects(allProjects));
        allRequestAttributes.add(fixedRequestAttributes);
        CompositeModelRepository repository = CorePlugin.modelRepositoryProvider().getCompositeModelRepository(allRequestAttributes.toArray(new FixedRequestAttributes[0]));
        return repository.fetchEclipseWorkspace(transientAttributes, this.fetchStrategy);
    }

}
