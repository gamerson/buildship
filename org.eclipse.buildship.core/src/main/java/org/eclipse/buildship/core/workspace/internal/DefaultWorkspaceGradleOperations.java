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

package org.eclipse.buildship.core.workspace.internal;

import java.io.File;
import java.util.List;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

import com.gradleware.tooling.toolingmodel.OmniEclipseProject;
import com.gradleware.tooling.toolingmodel.OmniEclipseWorkspace;
import com.gradleware.tooling.toolingmodel.OmniGradleProject;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;
import com.gradleware.tooling.toolingmodel.util.Maybe;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.configuration.GradleProjectNature;
import org.eclipse.buildship.core.configuration.ProjectConfiguration;
import org.eclipse.buildship.core.gradle.Specs;
import org.eclipse.buildship.core.util.predicate.Predicates;
import org.eclipse.buildship.core.workspace.GradleClasspathContainer;
import org.eclipse.buildship.core.workspace.NewProjectHandler;
import org.eclipse.buildship.core.workspace.WorkspaceGradleOperations;

/**
 * Default implementation of the {@link WorkspaceGradleOperations} interface.
 */
public final class DefaultWorkspaceGradleOperations implements WorkspaceGradleOperations {

    @Override
    public void synchronizeCompositeBuildWithWorkspace(OmniEclipseWorkspace compositeBuild, Set<FixedRequestAttributes> requestAttributes, NewProjectHandler newProjectHandler, IProgressMonitor monitor) {
        // collect Gradle projects and Eclipse workspace projects to sync
        List<OmniEclipseProject> allGradleProjects = compositeBuild.getOpenEclipseProjects();
        List<IProject> decoupledWorkspaceProjects = collectOpenWorkspaceProjectsRemovedFromcompositeBuild(allGradleProjects, requestAttributes);

        monitor.beginTask("Synchronize Gradle build with workspace", decoupledWorkspaceProjects.size() + allGradleProjects.size());
        try {
            // uncouple the open workspace projects that do not have a corresponding Gradle project anymore
            for (IProject project : decoupledWorkspaceProjects) {
                uncoupleWorkspaceProjectFromGradle(project, new SubProgressMonitor(monitor, 1));
            }
            // synchronize the Gradle projects with their corresponding workspace projects
            for (OmniEclipseProject gradleProject : allGradleProjects) {
                synchronizeGradleProjectWithWorkspaceProject(gradleProject, compositeBuild, requestAttributes, newProjectHandler, new SubProgressMonitor(monitor, 1));
            }
        } finally {
            monitor.done();
        }
    }

    private List<IProject> collectOpenWorkspaceProjectsRemovedFromcompositeBuild(List<OmniEclipseProject> gradleProjects, final Set<FixedRequestAttributes> rootRequestAttributes) {
        // in the workspace, find all projects with a Gradle nature that belong to the same Gradle build (based on the root project directory) but
        // which do not match the location of one of the Gradle projects of that build
        final Set<File> gradleProjectDirectories = FluentIterable.from(gradleProjects).transform(new Function<OmniEclipseProject, File>() {

            @Override
            public File apply(OmniEclipseProject gradleProject) {
                return gradleProject.getProjectDirectory();
            }
        }).toSet();

        final Set<File> rootProjectDirectories = FluentIterable.from(rootRequestAttributes).transform(new Function<FixedRequestAttributes, File>() {

            @Override
            public File apply(FixedRequestAttributes attributes) {
                return attributes.getProjectDir();
            }
        }).toSet();



        ImmutableList<IProject> allWorkspaceProjects = CorePlugin.workspaceOperations().getAllProjects();
        return FluentIterable.from(allWorkspaceProjects).filter(Predicates.accessibleGradleProject()).filter(new Predicate<IProject>() {

            @Override
            public boolean apply(IProject project) {
                ProjectConfiguration projectConfiguration = CorePlugin.projectConfigurationManager().readProjectConfiguration(project);
                return rootProjectDirectories.contains(projectConfiguration.getRequestAttributes().getProjectDir()) &&
                        (project.getLocation() == null || !gradleProjectDirectories.contains(project.getLocation().toFile()));
            }
        }).toList();
    }

    private void synchronizeGradleProjectWithWorkspaceProject(OmniEclipseProject project, OmniEclipseWorkspace compositeBuild, Set<FixedRequestAttributes> requestAttributes, NewProjectHandler newProjectHandler, IProgressMonitor monitor) {
        monitor.beginTask(String.format("Synchronize Gradle project %s with workspace project", project.getName()), 1);
        try {
            // check if a project already exists in the workspace at the location of the Gradle project to import
            Optional<IProject> workspaceProject = CorePlugin.workspaceOperations().findProjectByLocation(project.getProjectDirectory());
            if (workspaceProject.isPresent()) {
                synchronizeWorkspaceProject(project, compositeBuild, requestAttributes, workspaceProject.get(), new SubProgressMonitor(monitor, 1));
            } else {
                if (newProjectHandler.shouldImport(project)) {
                    synchronizeNonWorkspaceProject(project, compositeBuild, requestAttributes, newProjectHandler, new SubProgressMonitor(monitor, 1));
                }
            }
        } catch (CoreException e) {
            String message = String.format("Cannot synchronize Gradle project %s with workspace project.", project.getName());
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message, e);
        } finally {
            monitor.done();
        }
    }

    private void synchronizeWorkspaceProject(OmniEclipseProject project, OmniEclipseWorkspace compositeBuild, Set<FixedRequestAttributes> requestAttributes, IProject workspaceProject, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Synchronize Gradle project %s that is already in the workspace", project.getName()), 1);
        try {
            // check if the workspace project is open or not
            if (workspaceProject.isAccessible()) {
                synchronizeOpenWorkspaceProject(project, compositeBuild, requestAttributes, workspaceProject, new SubProgressMonitor(monitor, 1));
            } else {
                synchronizeClosedWorkspaceProject();
                monitor.worked(1);
            }
        } finally {
            monitor.done();
        }
    }

    private void synchronizeOpenWorkspaceProject(OmniEclipseProject project, OmniEclipseWorkspace compositeBuild, Set<FixedRequestAttributes> requestAttributes, IProject workspaceProject, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Synchronize Gradle project %s that is open in the workspace", project.getName()), 9);
        try {
            if (project.getName().equals(workspaceProject.getName())) {
                monitor.worked(1);
            } else {
                workspaceProject = renameProject(workspaceProject, project, compositeBuild, new SubProgressMonitor(monitor, 1));
            }
            // add Gradle nature, if needed
            CorePlugin.workspaceOperations().addNature(workspaceProject, GradleProjectNature.ID, new SubProgressMonitor(monitor, 1));

            /*
             * TODO it would be much nicer if the OmniEclipseProject knew which build (FixedRequestAttributes) it came from.
             * We are lugging around the request attributes through all the signatures just for this one usage.
             */
            Optional<FixedRequestAttributes> rootRequestAttributes = findRequestAttributes(project, requestAttributes);
            if (rootRequestAttributes.isPresent()) {
                ProjectConfiguration configuration = ProjectConfiguration.from(rootRequestAttributes.get(), project);
                CorePlugin.projectConfigurationManager().saveProjectConfiguration(configuration, workspaceProject);
            } else {
                throw new GradlePluginsRuntimeException(String.format("Project %s is not part of the given Gradle builds", project.getName()));
            }

            // update filters
            List<File> filteredSubFolders = getFilteredSubFolders(project);
            ResourceFilter.attachFilters(workspaceProject, filteredSubFolders, new SubProgressMonitor(monitor, 1));

            // update linked resources
            LinkedResourcesUpdater.update(workspaceProject, project.getLinkedResources(), new SubProgressMonitor(monitor, 1));

            if (isJavaProject(project)) {
                IJavaProject javaProject;
                if (hasJavaNature(workspaceProject)) {
                    javaProject = JavaCore.create(workspaceProject);
                    monitor.worked(1);
                } else {
                    IPath jrePath = JavaRuntime.getDefaultJREContainerEntry().getPath();
                    IClasspathEntry classpathContainer = GradleClasspathContainer.newClasspathEntry();
                    javaProject = CorePlugin.workspaceOperations().createJavaProject(workspaceProject, jrePath, classpathContainer, new SubProgressMonitor(monitor, 1));
                }
                JavaSourceSettingsUpdater.update(javaProject, project.getJavaSourceSettings().get(), new SubProgressMonitor(monitor, 1));
                SourceFolderUpdater.update(javaProject, project.getSourceDirectories(), new SubProgressMonitor(monitor, 1));
                synchronizeClasspathContainer(javaProject, project,  new SubProgressMonitor(monitor, 1));
            } else {
                monitor.worked(4);
            }

            // set project natures and build commands
            ProjectNatureUpdater.update(workspaceProject, project.getProjectNatures(), new SubProgressMonitor(monitor, 1));
            BuildCommandUpdater.update(workspaceProject, project.getBuildCommands(), new SubProgressMonitor(monitor, 1));
        } finally {
            monitor.done();
        }
    }

    private Optional<FixedRequestAttributes> findRequestAttributes(OmniEclipseProject project, Set<FixedRequestAttributes> requestAttributes) {
        for (FixedRequestAttributes rootRequestAttributes : requestAttributes) {
            if (Specs.eclipseProjectIsSubProjectOf(rootRequestAttributes.getProjectDir()).isSatisfiedBy(project)) {
                return Optional.of(rootRequestAttributes);
            }
        }
        return Optional.absent();
    }

    private IProject renameProject(IProject workspaceProject, OmniEclipseProject project, OmniEclipseWorkspace compositeBuild, IProgressMonitor monitor) {
        String newName = project.getName();
        ensureNameIsFree(newName, compositeBuild, monitor);
        return CorePlugin.workspaceOperations().renameProject(workspaceProject, newName, monitor);
    }

    private void ensureNameIsFree(String newName, OmniEclipseWorkspace compositeBuild, IProgressMonitor monitor) {
        Optional<IProject> possibleDuplicate = CorePlugin.workspaceOperations().findProjectByName(newName);
        if (possibleDuplicate.isPresent()) {
            IProject duplicate = possibleDuplicate.get();
            if (isScheduledForRenaming(duplicate, compositeBuild)) {
                renameTemporarily(duplicate, monitor);
            } else {
                throw new GradlePluginsRuntimeException("A project with the name " + newName + " already exists");
            }
        }
    }

    private boolean isScheduledForRenaming(IProject duplicate, OmniEclipseWorkspace compositeBuild) {
        Optional<OmniEclipseProject> duplicateInComposite = compositeBuild.tryFind(Specs.eclipseProjectMatchesProjectDirectory(duplicate.getLocation().toFile()));
        return duplicateInComposite.isPresent() && !duplicateInComposite.get().getName().equals(duplicate.getName());
    }

    private void renameTemporarily(IProject duplicate, IProgressMonitor monitor) {
        CorePlugin.workspaceOperations().renameProject(duplicate, "GrumpyGradlephantNoLikeDuplicate-" + duplicate.getName(), monitor);
    }

    private void synchronizeClosedWorkspaceProject() {
        // do not modify closed projects
    }

    private void synchronizeNonWorkspaceProject(OmniEclipseProject project, OmniEclipseWorkspace compositeBuild, Set<FixedRequestAttributes> requestAttributes, NewProjectHandler newProjectHandler, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Synchronize Gradle project %s that is not yet in the workspace", project.getName()), 2);
        try {
            IProject workspaceProject;

            // check if an Eclipse project already exists at the location of the Gradle project to import
            Optional<IProjectDescription> projectDescription = CorePlugin.workspaceOperations().findProjectInFolder(project.getProjectDirectory(), new SubProgressMonitor(monitor, 1));
            if (projectDescription.isPresent()) {
                if (newProjectHandler.shouldOverwriteDescriptor(projectDescription.get(), project)) {
                    CorePlugin.workspaceOperations().deleteProjectDescriptors(project.getProjectDirectory());
                    workspaceProject = addNewEclipseProjectToWorkspace(project, compositeBuild, requestAttributes, new SubProgressMonitor(monitor, 1));
                } else {
                    workspaceProject = addExistingEclipseProjectToWorkspace(project, compositeBuild, requestAttributes, projectDescription.get(), new SubProgressMonitor(monitor, 1));
                }
            } else {
                workspaceProject = addNewEclipseProjectToWorkspace(project, compositeBuild, requestAttributes, new SubProgressMonitor(monitor, 1));
            }
            newProjectHandler.afterImport(workspaceProject, project);
        } finally {
            monitor.done();
        }
    }

    private IProject addExistingEclipseProjectToWorkspace(OmniEclipseProject project, OmniEclipseWorkspace compositeBuild, Set<FixedRequestAttributes> requestAttributes, IProjectDescription projectDescription, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Add existing Eclipse project %s for Gradle project %s to the workspace", projectDescription.getName(), project.getName()), 2);
        try {
            IProject workspaceProject = CorePlugin.workspaceOperations().includeProject(projectDescription, ImmutableList.<String>of(), new SubProgressMonitor(monitor, 1));
            synchronizeOpenWorkspaceProject(project, compositeBuild, requestAttributes, workspaceProject, new SubProgressMonitor(monitor, 1));
            return workspaceProject;
        } finally {
            monitor.done();
        }
    }

    private IProject addNewEclipseProjectToWorkspace(OmniEclipseProject project, OmniEclipseWorkspace compositeBuild, Set<FixedRequestAttributes> requestAttributes, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Add new Eclipse project for Gradle project %s to the workspace", project.getName()), 2);
        try {
            IProject workspaceProject = CorePlugin.workspaceOperations().createProject(project.getName(), project.getProjectDirectory(), ImmutableList.<String>of(), new SubProgressMonitor(monitor, 1));
            synchronizeOpenWorkspaceProject(project, compositeBuild, requestAttributes, workspaceProject, new SubProgressMonitor(monitor, 1));
            return workspaceProject;
        } finally {
            monitor.done();
        }
    }

    private List<File> getFilteredSubFolders(OmniEclipseProject project) {
        return ImmutableList.<File>builder().
                addAll(collectChildProjectLocations(project)).
                add(getBuildDirectory(project)).
                add(getDotGradleDirectory(project)).build();
    }

    private List<File> collectChildProjectLocations(OmniEclipseProject project) {
        return FluentIterable.from(project.getChildren()).transform(new Function<OmniEclipseProject, File>() {

            @Override
            public File apply(OmniEclipseProject project) {
                return project.getProjectDirectory();
            }
        }).toList();
    }

    private File getBuildDirectory(OmniEclipseProject project) {
        OmniGradleProject gradleProject = project.getGradleProject();
        Maybe<File> buildScript = gradleProject.getBuildDirectory();
        if (buildScript.isPresent() && buildScript.get() != null) {
            return buildScript.get();
        } else {
            return new File(project.getProjectDirectory(), "build");
        }
    }

    private File getDotGradleDirectory(OmniEclipseProject project) {
        return new File(project.getProjectDirectory(), ".gradle");
    }

    private boolean isJavaProject(OmniEclipseProject project) {
        return project.getJavaSourceSettings().isPresent();
    }

    private boolean hasJavaNature(IProject project) {
        try {
            return project.hasNature(JavaCore.NATURE_ID);
        } catch (CoreException e) {
            return false;
        }
    }

    private void uncoupleWorkspaceProjectFromGradle(IProject workspaceProject, IProgressMonitor monitor) {
        monitor.beginTask(String.format("Uncouple workspace project %s from Gradle", workspaceProject.getName()), 2);
        try {
            ResourceFilter.detachAllFilters(workspaceProject, new SubProgressMonitor(monitor, 1));
            CorePlugin.workspaceOperations().removeNature(workspaceProject, GradleProjectNature.ID, new SubProgressMonitor(monitor, 1));
            CorePlugin.projectConfigurationManager().deleteProjectConfiguration(workspaceProject);
        } finally {
            monitor.done();
        }
    }

    @Override
    public void synchronizeClasspathContainer(IJavaProject workspaceProject, OmniEclipseProject project, IProgressMonitor monitor) {
        try {
            ClasspathContainerUpdater.update(workspaceProject, project, monitor);
        } catch (JavaModelException e) {
            String message = String.format("Cannot update classpath container on workspace project %s", workspaceProject.getProject().getName());
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message, e);
        }
    }

}
