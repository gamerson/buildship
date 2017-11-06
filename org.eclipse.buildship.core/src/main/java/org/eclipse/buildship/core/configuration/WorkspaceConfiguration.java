/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.buildship.core.configuration;

import java.io.File;

import org.gradle.api.Nullable;

import com.google.common.base.Objects;

import com.gradleware.tooling.toolingclient.GradleDistribution;

/**
 * Encapsulates settings that are the same for all Gradle projects in the workspace.
 *
 * @author Stefan Oehme
 *
 */
public final class WorkspaceConfiguration {

    private final GradleDistribution gradleDistribution;
    private final File gradleUserHome;
    private final boolean gradleIsOffline;
    private final boolean buildScansEnabled;
    private final boolean autoSyncEnabled;

    public WorkspaceConfiguration(GradleDistribution gradleDistribution, File gradleUserHome, boolean gradleIsOffline, boolean buildScansEnabled, boolean autoSyncEnabled) {
        this.gradleDistribution = gradleDistribution;
        this.gradleUserHome = gradleUserHome;
        this.gradleIsOffline = gradleIsOffline;
        this.buildScansEnabled = buildScansEnabled;
        this.autoSyncEnabled = autoSyncEnabled;
    }

    public GradleDistribution getGradleDistribution() {
        return this.gradleDistribution;
    }

    @Nullable
    public File getGradleUserHome() {
        return this.gradleUserHome;
    }

    public boolean isOffline() {
        return this.gradleIsOffline;
    }

    public boolean isBuildScansEnabled() {
        return this.buildScansEnabled;
    }

    public boolean isAutoSyncEnabled() {
        return this.autoSyncEnabled;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof WorkspaceConfiguration) {
            WorkspaceConfiguration other = (WorkspaceConfiguration) obj;
            return Objects.equal(this.gradleDistribution, other.gradleDistribution)
                    && Objects.equal(this.gradleUserHome, other.gradleUserHome)
                    && Objects.equal(this.gradleIsOffline, other.gradleIsOffline)
                    && Objects.equal(this.buildScansEnabled, other.buildScansEnabled)
                    && Objects.equal(this.autoSyncEnabled, other.autoSyncEnabled);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.gradleDistribution, this.gradleUserHome, this.gradleIsOffline, this.buildScansEnabled, this.autoSyncEnabled);
    }
}