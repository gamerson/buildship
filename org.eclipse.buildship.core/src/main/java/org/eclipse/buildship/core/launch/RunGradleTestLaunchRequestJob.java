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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import com.gradleware.tooling.toolingclient.SimpleRequest;
import com.gradleware.tooling.toolingclient.TestConfig;
import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.console.ProcessDescription;
import org.eclipse.buildship.core.i18n.CoreMessages;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;
import org.gradle.tooling.events.test.TestOperationDescriptor;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * Executes tests through Gradle based on a given list of {@code TestOperationDescriptor} instances and a given set of {@code GradleRunConfigurationAttributes}.
 */
public final class RunGradleTestLaunchRequestJob extends BaseLaunchRequestJob {

    private final ImmutableList<TestOperationDescriptor> testDescriptors;
    private final GradleRunConfigurationAttributes configurationAttributes;

    public RunGradleTestLaunchRequestJob(List<TestOperationDescriptor> testDescriptors, GradleRunConfigurationAttributes configurationAttributes) {
        super("Launching Gradle tests", false);
        this.testDescriptors = ImmutableList.copyOf(testDescriptors);
        this.configurationAttributes = Preconditions.checkNotNull(configurationAttributes);
    }

    @Override
    protected String getJobTaskName() {
        return "Launch Gradle tests";
    }

    @Override
    protected GradleRunConfigurationAttributes getConfigurationAttributes() {
        return this.configurationAttributes;
    }

    @Override
    protected ProcessDescription createProcessDescription() {
        String processName = createProcessName(this.configurationAttributes.getWorkingDir());
        return new TestLaunchProcessDescription(processName);
    }

    private String createProcessName(File workingDir) {
        return String.format("%s [Gradle Project] %s in %s (%s)", collectTestTaskNames(this.testDescriptors), Joiner.on(' ').join(collectSimpleDisplayNames(this.testDescriptors)),
                workingDir.getAbsolutePath(), DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date()));
    }

    private String collectTestTaskNames(List<TestOperationDescriptor> testDescriptors) {
        ImmutableList.Builder<String> testTaskNames = ImmutableList.builder();
        for (TestOperationDescriptor testDescriptor : testDescriptors) {
            Optional<TaskOperationDescriptor> taskDescriptor = findParentTestTask(testDescriptor);
            testTaskNames.add(taskDescriptor.isPresent() ? taskDescriptor.get().getTaskPath() : "Test");
        }
        return Joiner.on(' ').join(ImmutableSet.copyOf(testTaskNames.build()));
    }

    private Optional<TaskOperationDescriptor> findParentTestTask(OperationDescriptor testDescriptor) {
        OperationDescriptor parent = testDescriptor.getParent();
        if (parent instanceof TaskOperationDescriptor) {
            return Optional.of((TaskOperationDescriptor) parent);
        } else if (parent != null) {
            return findParentTestTask(parent);
        } else {
            return Optional.absent();
        }
    }

    @Override
    protected SimpleRequest<Void> createRequest() {
        return CorePlugin.toolingClient().newTestLaunchRequest(TestConfig.forTests(this.testDescriptors));
    }

    @Override
    protected void writeExtraConfigInfo(OutputStreamWriter writer) throws IOException {
        writer.write(String.format("%s: %s%n", CoreMessages.RunConfiguration_Label_Tests, Joiner.on(' ').join(collectQualifiedDisplayNames(this.testDescriptors))));
    }

    private static List<String> collectQualifiedDisplayNames(List<TestOperationDescriptor> testDescriptors) {
        return FluentIterable.from(testDescriptors).transform(new Function<TestOperationDescriptor, String>() {

            @Override
            public String apply(TestOperationDescriptor descriptor) {
                if (descriptor instanceof JvmTestOperationDescriptor) {
                    JvmTestOperationDescriptor jvmTestDescriptor = (JvmTestOperationDescriptor) descriptor;
                    String className = jvmTestDescriptor.getClassName();
                    String methodName = jvmTestDescriptor.getMethodName();
                    return methodName != null ? className + "#" + methodName : className;
                } else {
                    return descriptor.getDisplayName();
                }
            }
        }).toList();
    }

    private static List<String> collectSimpleDisplayNames(List<TestOperationDescriptor> testDescriptors) {
        return FluentIterable.from(testDescriptors).transform(new Function<TestOperationDescriptor, String>() {

            @Override
            public String apply(TestOperationDescriptor descriptor) {
                if (descriptor instanceof JvmTestOperationDescriptor) {
                    JvmTestOperationDescriptor jvmTestDescriptor = (JvmTestOperationDescriptor) descriptor;
                    String className = jvmTestDescriptor.getClassName();
                    String methodName = jvmTestDescriptor.getMethodName();
                    int index = className.lastIndexOf('.');
                    if (index >= 0 && className.length() > index + 1) {
                        className = className.substring(index + 1);
                    }
                    return methodName != null ? className + "#" + methodName : className;
                } else {
                    return descriptor.getDisplayName();
                }
            }
        }).toList();
    }

    /**
     * Implementation of {@code ProcessDescription}.
     */
    private final class TestLaunchProcessDescription extends BaseProcessDescription {

        public TestLaunchProcessDescription(String processName) {
            super(processName, RunGradleTestLaunchRequestJob.this, RunGradleTestLaunchRequestJob.this.configurationAttributes);
        }

        @Override
        public boolean isRerunnable() {
            return true;
        }

        @Override
        public void rerun() {
            RunGradleTestLaunchRequestJob job = new RunGradleTestLaunchRequestJob(
                    RunGradleTestLaunchRequestJob.this.testDescriptors,
                    RunGradleTestLaunchRequestJob.this.configurationAttributes
            );
            job.schedule();
        }

    }

}
