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

package org.eclipse.buildship.core.test.fixtures

import org.gradle.tooling.GradleConnector

import com.gradleware.tooling.toolingclient.GradleDistribution
import com.gradleware.tooling.toolingmodel.OmniEclipseGradleBuild
import com.gradleware.tooling.toolingmodel.OmniEclipseProject
import com.gradleware.tooling.toolingmodel.OmniEclipseWorkspace;
import com.gradleware.tooling.toolingmodel.repository.CompositeModelRepository
import com.gradleware.tooling.toolingmodel.repository.FetchStrategy
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes
import com.gradleware.tooling.toolingmodel.repository.SimpleModelRepository
import com.gradleware.tooling.toolingmodel.repository.TransientRequestAttributes

import org.eclipse.buildship.core.CorePlugin
import org.eclipse.buildship.core.workspace.GradleBuildInWorkspace
import org.eclipse.buildship.core.workspace.internal.DefaultGradleBuildInWorkspace;;


/**
 * Helper class to load Gradle models.
 */
abstract class GradleModel {

    private GradleBuildInWorkspace build

    GradleModel(GradleBuildInWorkspace model) {
        this.build = model
    }

    /**
     * The request attributes used to load the Gradle model.
     *
     * @return the request attributes
     */
    public FixedRequestAttributes getAttributes() {
        build.requestAttributes
    }

    /**
     * The OmniEclipseGradleBuild model of the loaded project.
     *
     * @return the OmniEclipseGradleBuild model
     */
    public GradleBuildInWorkspace getBuild() {
        build
    }

    /**
     * The OmniEclipseProject model of the loaded project.
     *
     * @return the OmniEclipseProject model
     */
    public OmniEclipseProject eclipseProject(String name) {
        build.eclipseProjects.find { it.name == name }
    }

    /**
     * Loads the Gradle project from the target folder.
     *
     * @param rootProjectFolder the folder from where the Gradle model should be loaded
     */
    static GradleModel fromProject(File rootProjectFolder) {
        FixedRequestAttributes attributes = new FixedRequestAttributes(rootProjectFolder, null, GradleDistribution.fromBuild(), null, [], [])
        CompositeModelRepository modelRepository = CorePlugin.modelRepositoryProvider().getCompositeModelRepository(attributes)
        OmniEclipseWorkspace workspace = modelRepository.fetchEclipseWorkspace(new TransientRequestAttributes(false, System.out, System.err, System.in, [], [], GradleConnector.newCancellationTokenSource().token()), FetchStrategy.FORCE_RELOAD)
        new GradleModel(DefaultGradleBuildInWorkspace.from(workspace, attributes)) {}
    }
}
