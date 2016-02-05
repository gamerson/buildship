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

import org.gradle.api.specs.Spec;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

import com.gradleware.tooling.toolingmodel.OmniEclipseProject;
import com.gradleware.tooling.toolingmodel.OmniGradleProject;
import com.gradleware.tooling.toolingmodel.OmniJavaSourceSettings;
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
import org.eclipse.buildship.core.workspace.GradleBuildInWorkspace;
import org.eclipse.buildship.core.workspace.GradleClasspathContainer;
import org.eclipse.buildship.core.workspace.NewProjectHandler;
import org.eclipse.buildship.core.workspace.WorkspaceGradleOperations;

/**
 * Default implementation of the {@link WorkspaceGradleOperations} interface.
 */
public final class DefaultWorkspaceGradleOperations implements WorkspaceGradleOperations {

    @Override
    public void synchronizeGradleBuildWithWorkspace(GradleBuildInWorkspace gradleBuild, NewProjectHandler newProjectHandler, IProgressMonitor monitor) {
        // collect Gradle projects and Eclipse workspace projects to sync
        List<OmniEclipseProject> allGradleProjects = gradleBuild.getEclipseProjects();
        List<IProject> decoupledWorkspaceProjects = collectOpenWorkspaceProjectsRemovedFromGradleBuild(allGradleProjects, gradleBuild.getRequestAttributes());

        monitor.beginTask("Synchronize Gradle build with workspace", decoupledWorkspaceProjects.size() + allGradleProjects.size());
        try {
            // uncouple the open workspace projects that do not have a corresponding Gradle project anymore
            for (IProject project : decoupledWorkspaceProjects) {
                uncoupleWorkspaceProjectFromGradle(project, new SubProgressMonitor(monitor, 1));
            }
            // synchronize the Gradle projects with their corresponding workspace projects
            for (OmniEclipseProject gradleProject : allGradleProjects) {
                synchronizeGradleProjectWithWorkspaceProject(gradleProject, gradleBuild, newProjectHandler, new SubProgressMonitor(monitor, 1));
            }
        } finally {
            monitor.done();
        }
    }

    private List<IProject> collectOpenWorkspaceProjectsRemovedFromGradleBuild(List<OmniEclipseProject> gradleProjects, final FixedRequestAttributes rootRequestAttributes) {
        // in the workspace, find all projects with a Gradle nature that belong to the same Gradle build (based on the root project directory) but
        // which do not match the location of one of the Gradle projects of that build
        final Set<File> gradleProjectDirectories = FluentIterable.from(gradleProjects).transform(new Function<OmniEclipseProject, File>() {

            @Override
            public File apply(OmniEclipseProject gradleProject) {
                return gradleProject.getProjectDirectory();
            }
        }).toSet();

        ImmutableList<IProject> allWorkspaceProjects = CorePlugin.workspaceOperations().getAllProjects();
        return FluentIterable.from(allWorkspaceProjects).filter(Predicates.accessibleGradleProject()).filter(new Predicate<IProject>() {

            @Override
            public boolean apply(IProject project) {
                ProjectConfiguration projectConfiguration = CorePlugin.projectConfigurationManager().readProjectConfiguration(project);
                return projectConfiguration.getRequestAttributes().getProjectDir().equals(rootRequestAttributes.getProjectDir()) &&
                        (project.getLocation() == null || !gradleProjectDirectories.contains(project.getLocation().toFile()));
            }
        }).toList();
    }

    @Override
    public void synchronizeGradleProjectWithWorkspaceProject(OmniEclipseProject project, GradleBuildInWorkspace gradleBuild, NewProjectHandler newProjectHandler, IProgressMonitor monitor) {
        monitor.beginTask(String.format("Synchronize Gradle project %s with workspace project", project.getName()), 1);
        try {
            // check if a project already exists in the workspace at the location of the Gradle project to import
            Optional<IProject> workspaceProject = CorePlugin.workspaceOperations().findProjectByLocation(project.getProjectDirectory());
            if (workspaceProject.isPresent()) {
                synchronizeWorkspaceProject(project, gradleBuild, workspaceProject.get(), gradleBuild.getRequestAttributes(), new SubProgressMonitor(monitor, 1));
            } else {
                if (newProjectHandler.shouldImport(project)) {
                    synchronizeNonWorkspaceProject(gradleBuild, project, newProjectHandler, new SubProgressMonitor(monitor, 1));
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

    private void synchronizeWorkspaceProject(OmniEclipseProject project, GradleBuildInWorkspace gradleBuild, IProject workspaceProject, FixedRequestAttributes rootRequestAttributes, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Synchronize Gradle project %s that is already in the workspace", project.getName()), 1);
        try {
            // check if the workspace project is open or not
            if (workspaceProject.isAccessible()) {
                synchronizeOpenWorkspaceProject(gradleBuild, project, workspaceProject, rootRequestAttributes, new SubProgressMonitor(monitor, 1));
            } else {
                synchronizeClosedWorkspaceProject();
                monitor.worked(1);
            }
        } finally {
            monitor.done();
        }
    }

    private void synchronizeOpenWorkspaceProject(GradleBuildInWorkspace gradleBuild, OmniEclipseProject project, IProject workspaceProject, FixedRequestAttributes rootRequestAttributes, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Synchronize Gradle project %s that is open in the workspace", project.getName()), 8);
        try {
            if (!project.getName().equals(workspaceProject.getName())) {
                workspaceProject = renameProject(workspaceProject, gradleBuild, project, new SubProgressMonitor(monitor, 1));
            }
            // add Gradle nature, if needed
            CorePlugin.workspaceOperations().addNature(workspaceProject, GradleProjectNature.ID, new SubProgressMonitor(monitor, 1));

            // persist the Gradle-specific configuration in the Eclipse project's .settings folder, if the configuration is available
            if (rootRequestAttributes != null) {
                ProjectConfiguration configuration = ProjectConfiguration.from(rootRequestAttributes, project);
                CorePlugin.projectConfigurationManager().saveProjectConfiguration(configuration, workspaceProject);
            }

            // update filters
            List<File> filteredSubFolders = getFilteredSubFolders(project);
            ResourceFilter.attachFilters(workspaceProject, filteredSubFolders, new SubProgressMonitor(monitor, 1));

            // update linked resources
            LinkedResourcesUpdater.update(workspaceProject, project.getLinkedResources(), new SubProgressMonitor(monitor, 1));

            if (isJavaProject(project)) {
                if (hasJavaNature(workspaceProject)) {
                    // if the workspace project is already a Java project, then update the source
                    // folders and the project/external dependencies
                    IJavaProject javaProject = JavaCore.create(workspaceProject);
                    JavaSourceSettingsUpdater.update(javaProject, project.getJavaSourceSettings(), new SubProgressMonitor(monitor, 1));
                    SourceFolderUpdater.update(javaProject, project.getSourceDirectories(), new SubProgressMonitor(monitor, 1));
                    ClasspathContainerUpdater.update(javaProject, project, new SubProgressMonitor(monitor, 1));
                } else {
                    // if the workspace project is not a Java project, then convert it to one and
                    // add the Gradle classpath container to its classpath, which will trigger a re-synchronization (the if-branch above)
                    IPath jrePath = JavaRuntime.getDefaultJREContainerEntry().getPath();
                    IClasspathEntry classpathContainer = GradleClasspathContainer.newClasspathEntry();
                    monitor.worked(2);
                    CorePlugin.workspaceOperations().createJavaProject(workspaceProject, jrePath, classpathContainer, new SubProgressMonitor(monitor, 1));
                }
            } else {
                monitor.worked(3);
            }

            // set project natures and build commands
            ProjectNatureUpdater.update(workspaceProject, project.getProjectNatures(), new SubProgressMonitor(monitor, 1));
            BuildCommandUpdater.update(workspaceProject, project.getBuildCommands(), new SubProgressMonitor(monitor, 1));
        } finally {
            monitor.done();
        }
    }

    private IProject renameProject(IProject workspaceProject, GradleBuildInWorkspace gradleBuild, OmniEclipseProject project, IProgressMonitor monitor) {
        String newName = project.getName();
        Optional<IProject> possibleDuplicate = CorePlugin.workspaceOperations().findProjectByName(newName);
        if (possibleDuplicate.isPresent()) {
            IProject duplicate = possibleDuplicate.get();
            Optional<OmniEclipseProject> duplicateInComposite = gradleBuild.getWorkspace().tryFind(Specs.eclipseProjectMatchesProjectDirectory(duplicate.getLocation().toFile()));
            if (duplicateInComposite.isPresent() && !duplicateInComposite.get().getName().equals(duplicate.getName())) {
                //this project will be renamed later during the synchronize operation anyway, so we can safely give it a temporary name
                CorePlugin.workspaceOperations().renameProject(duplicate, "GrumpyGradlephantNoLikeDuplicate-" + duplicate.getName(), new SubProgressMonitor(monitor, 1));
            } else {
                throw new GradlePluginsRuntimeException("A project with the name " + newName + " already exists");
            }
        }
        return CorePlugin.workspaceOperations().renameProject(workspaceProject, newName, new SubProgressMonitor(monitor, 1));
    }

    private void synchronizeClosedWorkspaceProject() {
        // do not modify closed projects
    }

    private void synchronizeNonWorkspaceProject(GradleBuildInWorkspace gradleBuild, OmniEclipseProject project, NewProjectHandler newProjectHandler, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Synchronize Gradle project %s that is not yet in the workspace", project.getName()), 2);
        try {
            IProject workspaceProject;

            // check if an Eclipse project already exists at the location of the Gradle project to import
            Optional<IProjectDescription> projectDescription = CorePlugin.workspaceOperations().findProjectInFolder(project.getProjectDirectory(), new SubProgressMonitor(monitor, 1));
            if (projectDescription.isPresent()) {
                if (newProjectHandler.shouldOverwriteDescriptor(projectDescription.get(), project)) {
                    deleteProjectDescriptor(project);
                    workspaceProject = addNewEclipseProjectToWorkspace(project, gradleBuild.getRequestAttributes(), new SubProgressMonitor(monitor, 1));
                } else {
                    workspaceProject = addExistingEclipseProjectToWorkspace(gradleBuild, project, projectDescription.get(), new SubProgressMonitor(monitor, 1));
                }
            } else {
                workspaceProject = addNewEclipseProjectToWorkspace(project, gradleBuild.getRequestAttributes(), new SubProgressMonitor(monitor, 1));
            }

            newProjectHandler.afterImport(workspaceProject, project);
        } finally {
            monitor.done();
        }
    }

    private void deleteProjectDescriptor(OmniEclipseProject project) {
        new File(project.getProjectDirectory(), ".project").delete();
    }

    private IProject addExistingEclipseProjectToWorkspace(GradleBuildInWorkspace gradleBuild, OmniEclipseProject project, IProjectDescription projectDescription, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Add existing Eclipse project %s for Gradle project %s to the workspace", projectDescription.getName(), project.getName()), 4);
        try {
            // include the existing Eclipse project in the workspace
            List<String> gradleNature = ImmutableList.of(GradleProjectNature.ID);
            IProject workspaceProject = CorePlugin.workspaceOperations().includeProject(projectDescription, gradleNature, new SubProgressMonitor(monitor, 1));

            if (!project.getName().equals(workspaceProject.getName())) {
                workspaceProject = renameProject(workspaceProject, gradleBuild, project, new SubProgressMonitor(monitor, 1));
            }
            // persist the Gradle-specific configuration in the Eclipse project's .settings folder
            ProjectConfiguration projectConfiguration = ProjectConfiguration.from(gradleBuild.getRequestAttributes(), project);
            CorePlugin.projectConfigurationManager().saveProjectConfiguration(projectConfiguration, workspaceProject);

            // update the source language level in case of a Java project
            if (isJavaProject(project) && hasJavaNature(workspaceProject)) {
                IJavaProject javaProject = JavaCore.create(workspaceProject);
                JavaSourceSettingsUpdater.update(javaProject, project.getJavaSourceSettings(), new SubProgressMonitor(monitor, 1));
            } else {
                monitor.worked(1);
            }

            // set project natures and build commands
            ProjectNatureUpdater.update(workspaceProject, project.getProjectNatures(), new SubProgressMonitor(monitor, 1));
            BuildCommandUpdater.update(workspaceProject, project.getBuildCommands(), new SubProgressMonitor(monitor, 1));
            return workspaceProject;
        } finally {
            monitor.done();
        }
    }

    private IProject addNewEclipseProjectToWorkspace(OmniEclipseProject project, FixedRequestAttributes rootRequestAttributes, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Add new Eclipse project for Gradle project %s to the workspace", project.getName()), 8);
        try {
            // create a new Eclipse project in the workspace for the current Gradle project
            List<String> gradleNature = ImmutableList.of(GradleProjectNature.ID);
            IProject workspaceProject = CorePlugin.workspaceOperations().createProject(project.getName(), project.getProjectDirectory(), gradleNature, new SubProgressMonitor(monitor, 1));

            // persist the Gradle-specific configuration in the Eclipse project's .settings folder
            ProjectConfiguration projectConfiguration = ProjectConfiguration.from(rootRequestAttributes, project);
            CorePlugin.projectConfigurationManager().saveProjectConfiguration(projectConfiguration, workspaceProject);

            // attach filters
            List<File> filteredSubFolders = getFilteredSubFolders(project);
            ResourceFilter.attachFilters(workspaceProject, filteredSubFolders, new SubProgressMonitor(monitor, 1));

            // set linked resources
            LinkedResourcesUpdater.update(workspaceProject, project.getLinkedResources(), new SubProgressMonitor(monitor, 1));

            // if the current Gradle project is a Java project, make the Eclipse project a Java project and add a Gradle classpath container
            if (isJavaProject(project)) {
                IPath jrePath = JavaRuntime.getDefaultJREContainerEntry().getPath();
                IClasspathEntry classpathContainer = GradleClasspathContainer.newClasspathEntry();
                CorePlugin.workspaceOperations().createJavaProject(workspaceProject, jrePath, classpathContainer, new SubProgressMonitor(monitor, 1));
                IJavaProject javaProject = JavaCore.create(workspaceProject);
                JavaSourceSettingsUpdater.update(javaProject, project.getJavaSourceSettings(), new SubProgressMonitor(monitor, 1));
                SourceFolderUpdater.update(javaProject, project.getSourceDirectories(), new SubProgressMonitor(monitor, 1));
                
            } else {
                monitor.worked(1);
            }

            // set project natures and build commands
            ProjectNatureUpdater.update(workspaceProject, project.getProjectNatures(), new SubProgressMonitor(monitor, 1));
            BuildCommandUpdater.update(workspaceProject, project.getBuildCommands(), new SubProgressMonitor(monitor, 1));

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
        Maybe<OmniJavaSourceSettings> javaSourceSettings = project.getJavaSourceSettings();
        if (javaSourceSettings.isPresent()) {
            // for Gradle version >=2.10 the project is a Java project if and only if
            // the Java source settings is not null
            return javaSourceSettings.get() != null;
        } else {
            // for older Gradle versions the following approximation is used: if the project
            // has at least one source folder then it is a Java project
            return !project.getSourceDirectories().isEmpty();
        }
    }

    private boolean hasJavaNature(IProject project) {
        try {
            return project.hasNature(JavaCore.NATURE_ID);
        } catch (CoreException e) {
            return false;
        }
    }

    @Override
    public void synchronizeWorkspaceProject(final IProject workspaceProject, GradleBuildInWorkspace gradleBuild, IProgressMonitor monitor) {
        monitor.beginTask(String.format("Synchronize workspace project %s with Gradle build", workspaceProject.getName()), 10);
        try {
            if (GradleProjectNature.INSTANCE.isPresentOn(workspaceProject)) {
                // find the Gradle project matching the location of the workspace project
                Optional<OmniEclipseProject> gradleProject = gradleBuild.getWorkspace().tryFind(new Spec<OmniEclipseProject>() {
                    @Override
                    public boolean isSatisfiedBy(OmniEclipseProject gradleProject) {
                        return workspaceProject.getLocation() != null && workspaceProject.getLocation().toFile().equals(gradleProject.getProjectDirectory());
                    }
                });

                // if the matching Gradle project can be found, synchronize the workspace project with it
                if (gradleProject.isPresent()) {
                    synchronizeGradleProjectWithWorkspaceProject(gradleProject.get(), gradleBuild, NewProjectHandler.IMPORT_AND_MERGE, new SubProgressMonitor(monitor, 10));
                    return;
                }
            }

            // uncouple the workspace project from Gradle if there is no matching Gradle project
            uncoupleWorkspaceProjectFromGradle(workspaceProject, new SubProgressMonitor(monitor, 5));
            clearClasspathContainer(workspaceProject, new SubProgressMonitor(monitor, 5));
        } finally {
            monitor.done();
        }
    }

    private void clearClasspathContainer(IProject workspaceProject, IProgressMonitor monitor) {
        try {
            if (hasJavaNature(workspaceProject)) {
                IJavaProject javaProject = JavaCore.create(workspaceProject);
                ClasspathContainerUpdater.clear(javaProject, monitor);
            }
        } catch (JavaModelException e) {
            String message = String.format("Cannot clear classpath container from workspace project %s", workspaceProject.getName());
            CorePlugin.logger().error(message, e);
            throw new GradlePluginsRuntimeException(message, e);
        }
    }

    @Override
    public void uncoupleWorkspaceProjectFromGradle(IProject workspaceProject, IProgressMonitor monitor) {
        monitor.beginTask(String.format("Uncouple workspace project %s from Gradle", workspaceProject.getName()), 2);
        try {
            ResourceFilter.detachAllFilters(workspaceProject, new SubProgressMonitor(monitor, 1));
            CorePlugin.workspaceOperations().removeNature(workspaceProject, GradleProjectNature.ID, new SubProgressMonitor(monitor, 1));
            CorePlugin.projectConfigurationManager().deleteProjectConfiguration(workspaceProject);
        } finally {
            monitor.done();
        }
    }

}
