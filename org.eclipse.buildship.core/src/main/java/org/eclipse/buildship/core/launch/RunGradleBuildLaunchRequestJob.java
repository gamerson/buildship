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

package org.eclipse.buildship.core.launch;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.gradleware.tooling.toolingclient.LaunchableConfig;
import com.gradleware.tooling.toolingclient.SimpleRequest;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.console.ProcessDescription;
import org.eclipse.buildship.core.i18n.CoreMessages;
import org.eclipse.buildship.core.util.collections.CollectionsUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * Executes Gradle tasks based on a given {@code ILaunch} and {@code ILaunchConfiguration} instance.
 */
public final class RunGradleBuildLaunchRequestJob extends BaseLaunchRequestJob {

    private final ILaunch launch;
    private final GradleRunConfigurationAttributes configurationAttributes;

    public RunGradleBuildLaunchRequestJob(ILaunch launch) {
        super("Launching Gradle tasks", false);
        this.launch = Preconditions.checkNotNull(launch);
        this.configurationAttributes = GradleRunConfigurationAttributes.from(launch.getLaunchConfiguration());
    }

    @Override
    protected String getJobTaskName() {
        return String.format("Launch Gradle tasks %s", this.configurationAttributes.getTasks());
    }

    @Override
    protected GradleRunConfigurationAttributes getConfigurationAttributes() {
        return this.configurationAttributes;
    }

    @Override
    protected ProcessDescription createProcessDescription() {
        String processName = createProcessName(this.configurationAttributes.getTasks(), this.configurationAttributes.getWorkingDir(), this.launch.getLaunchConfiguration().getName());
        return new BuildLaunchProcessDescription(processName);
    }

    private String createProcessName(List<String> tasks, File workingDir, String launchConfigurationName) {
        return String.format("%s [Gradle Project] %s in %s (%s)", launchConfigurationName, Joiner.on(' ').join(tasks), workingDir.getAbsolutePath(), DateFormat
                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date()));
    }

    @Override
    protected SimpleRequest<Void> createRequest() {
        return CorePlugin.toolingClient().newBuildLaunchRequest(LaunchableConfig.forTasks(this.configurationAttributes.getTasks()));
    }

    @Override
    protected void writeExtraConfigInfo(OutputStreamWriter writer) throws IOException {
        String taskNames = Strings.emptyToNull(CollectionsUtils.joinWithSpace(this.configurationAttributes.getTasks()));
        taskNames = taskNames != null ? taskNames : CoreMessages.RunConfiguration_Value_RunDefaultTasks;
        writer.write(String.format("%s: %s%n", CoreMessages.RunConfiguration_Label_GradleTasks, taskNames));
    }

    /**
     * Implementation of {@code ProcessDescription}.
     */
    private final class BuildLaunchProcessDescription extends BaseProcessDescription {

        public BuildLaunchProcessDescription(String processName) {
            super(processName, RunGradleBuildLaunchRequestJob.this, RunGradleBuildLaunchRequestJob.this.configurationAttributes);
        }

        @Override
        public boolean isRerunnable() {
            ILaunchConfiguration[] launchConfigurations;
            try {
                launchConfigurations = DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurations();
            } catch (CoreException e) {
                return false;
            }

            ILaunchConfiguration targetLaunchConfiguration = RunGradleBuildLaunchRequestJob.this.launch.getLaunchConfiguration();
            for (ILaunchConfiguration launchConfiguration : launchConfigurations) {
                if (launchConfiguration.equals(targetLaunchConfiguration)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void rerun() {
            ILaunch launch = RunGradleBuildLaunchRequestJob.this.launch;
            CorePlugin.gradleLaunchConfigurationManager().launch(launch.getLaunchConfiguration(), launch.getLaunchMode());
        }

    }

}
