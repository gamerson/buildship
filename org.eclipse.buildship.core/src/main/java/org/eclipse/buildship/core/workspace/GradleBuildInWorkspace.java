package org.eclipse.buildship.core.workspace;

import java.util.List;

import com.gradleware.tooling.toolingmodel.OmniEclipseProject;
import com.gradleware.tooling.toolingmodel.OmniEclipseWorkspace;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;

import org.eclipse.buildship.core.gradle.Specs;

/**
 * Represents a set of {@link OmniEclipseProject} within an {@link OmniEclipseWorkspace} which
 * belong to the same root project.
 */
public class GradleBuildInWorkspace {

    private List<OmniEclipseProject> eclipseProjects;
    private OmniEclipseWorkspace workspace;
    private FixedRequestAttributes requestAttributes;

    public GradleBuildInWorkspace(OmniEclipseWorkspace workspace, FixedRequestAttributes requestAttributes) {
        this.eclipseProjects = workspace.filter(Specs.eclipseProjectIsSubProjectOf(requestAttributes.getProjectDir()));
        this.workspace = workspace;
        this.requestAttributes = requestAttributes;
    }

    public List<OmniEclipseProject> getEclipseProjects() {
        return this.eclipseProjects;
    }

    public OmniEclipseWorkspace getWorkspace() {
        return this.workspace;
    }

    public FixedRequestAttributes getRequestAttributes() {
        return this.requestAttributes;
    }

}
