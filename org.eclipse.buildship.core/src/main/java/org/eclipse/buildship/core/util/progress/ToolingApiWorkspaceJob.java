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

package org.eclipse.buildship.core.util.progress;

import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.buildship.core.CorePlugin;

/**
 * Base class for cancellable workspace jobs that invoke the Gradle Tooling API.
 *
 * Note that if the job returns a normal error status then Eclipse shows the default error dialog,
 * which is too basic for our needs. On the other hand, the OK_STATUS is not feasible since invokers
 * of the job might need to determine if the job has finished successfully or not. To solve this
 * dilemma, we return an INFO/CANCEL status containing the thrown exception.
 */
public abstract class ToolingApiWorkspaceJob extends WorkspaceJob {

    private final CancellationTokenSource tokenSource;
    private final String workName;
    private final boolean notifyUserAboutBuildFailures;

    /**
     * Creates a new job with the specified name. The job name is a human-readable value that is
     * displayed to users. The name does not need to be unique, but it must not be {@code null}. A
     * token for Gradle build cancellation is created.
     *
     * @param name the name of the job
     */
    protected ToolingApiWorkspaceJob(String name) {
        this(name, true);

    }

    /**
     * Creates a new job with the specified name. The job name is a human-readable value that is
     * displayed to users. The name does not need to be unique, but it must not be {@code null}. A
     * token for Gradle build cancellation is created.
     *
     * @param name the name of the job
     * @param notifyUserAboutBuildFailures {@code true} if the user should be visually notified about build failures that happen while running the job
     */
    protected ToolingApiWorkspaceJob(String name, boolean notifyUserAboutBuildFailures) {
        super(name);
        this.tokenSource = GradleConnector.newCancellationTokenSource();
        this.workName = name;
        this.notifyUserAboutBuildFailures = notifyUserAboutBuildFailures;
    }

    protected CancellationToken getToken() {
        return this.tokenSource.token();
    }

    @Override
    public final IStatus runInWorkspace(final IProgressMonitor monitor) throws CoreException {
        ToolingApiInvoker invoker = new ToolingApiInvoker(this.workName, this.notifyUserAboutBuildFailures);
        return invoker.invoke(new ToolingApiCommand() {
            @Override
            public void run() throws Exception {
                runToolingApiJobInWorkspace(monitor);
            }
        }, monitor);
    }

    /**
     * Template method that executes the actual Tooling API-related work.
     * <p/>
     * If an exception is thrown in this method, the exception is reported via the
     * {@link org.eclipse.buildship.core.notification.UserNotification} service. The
     * notification content and its severity depend on the type of the thrown exception.
     * <p/>
     * If no exception is thrown in the template method, the job's return status is
     * {@link org.eclipse.core.runtime.Status#OK_STATUS}. If an exception occurs, a non-error, non-ok status
     * is returned. This disables the platform UI to show the built-in (and rather basic)
     * exception dialog.
     *
     * @param monitor the monitor to report the progress
     * @throws Exception thrown when an error happens during the execution
     */
    protected abstract void runToolingApiJobInWorkspace(IProgressMonitor monitor) throws Exception;

    @Override
    protected void canceling() {
        this.tokenSource.cancel();
    }

    @Override
    public boolean belongsTo(Object family) {
        return CorePlugin.GRADLE_JOB_FAMILY.equals(family);
    }

}
