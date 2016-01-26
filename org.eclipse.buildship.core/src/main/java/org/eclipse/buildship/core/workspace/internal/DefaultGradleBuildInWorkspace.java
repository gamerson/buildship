package org.eclipse.buildship.core.workspace.internal;

import java.util.List;

import com.gradleware.tooling.toolingmodel.OmniEclipseProject;
import com.gradleware.tooling.toolingmodel.OmniEclipseWorkspace;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;

import org.eclipse.buildship.core.gradle.Specs;
import org.eclipse.buildship.core.workspace.GradleBuildInWorkspace;

/**
 * Represents a set of {@link OmniEclipseProject} within an {@link OmniEclipseWorkspace} which
 * belong to the same root project.
 */
public final class DefaultGradleBuildInWorkspace implements GradleBuildInWorkspace {

    private List<OmniEclipseProject> eclipseProjects;
    private OmniEclipseWorkspace workspace;
    private FixedRequestAttributes requestAttributes;

    private DefaultGradleBuildInWorkspace(OmniEclipseWorkspace workspace, FixedRequestAttributes requestAttributes) {
        this.eclipseProjects = workspace.filter(Specs.eclipseProjectIsSubProjectOf(requestAttributes.getProjectDir()));
        this.workspace = workspace;
        this.requestAttributes = requestAttributes;
    }

    /**
     * The projects in the workspace that were derived from the root project specified by
     * {@link #getRequestAttributes()}
     */
    @Override
    public List<OmniEclipseProject> getEclipseProjects() {
        return this.eclipseProjects;
    }

    /**
     * The workspace that this build is a part of.
     */
    @Override
    public OmniEclipseWorkspace getWorkspace() {
        return this.workspace;
    }

    /**
     * The request attributes that were used to fetch the Eclipse projects of this build.
     */
    @Override
    public FixedRequestAttributes getRequestAttributes() {
        return this.requestAttributes;
    }

    public static GradleBuildInWorkspace from(OmniEclipseWorkspace workspace, FixedRequestAttributes requestAttributes) {
        return new DefaultGradleBuildInWorkspace(workspace, requestAttributes);
    }

}
