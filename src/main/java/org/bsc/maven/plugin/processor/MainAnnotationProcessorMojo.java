/*
 *   Copyright (C) 2009 2010 2011 Bartolomeo Sorrentino <bartolomeo.sorrentino@gmail.com>
 * 
 *   This file is part of maven-annotation-plugin.
 *
 *    maven-annotation-plugin is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    maven-annotation-plugin is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with maven-annotation-plugin.  If not, see <http://www.gnu.org/licenses/>. 
 */
package org.bsc.maven.plugin.processor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;

/**
 * 
 * @author bsorrentino
 *
 * @goal process
 * @requiresDependencyResolution compile
 * @phase generate-sources
 */
//@MojoGoal("process")
//@MojoRequiresDependencyResolution(value = "compile")
//@MojoPhase("generate-sources")
public class MainAnnotationProcessorMojo extends AbstractAnnotationProcessorMojo
{
    /** 
     * project classpath 
     * 
     * @parameter expression = "${project.compileClasspathElements}"
     * @required
     * @readonly
     */
    @SuppressWarnings("rawtypes")
    //@MojoParameter(expression = "${project.compileClasspathElements}", required = true, readonly = true)
    private List classpathElements;

    /** 
     * project sourceDirectory 
     * 
     * @parameter expression = "${project.build.sourceDirectory}"
     * @required
     */
    //@MojoParameter(expression = "${project.build.sourceDirectory}", required = true)
    private File sourceDirectory;

    /**
     * @parameter expression = "${project.build.directory}/generated-sources/apt"
     * @required
     */
    //@MojoParameter(expression = "${project.build.directory}/generated-sources/apt", required = true)
    private File defaultOutputDirectory;

    /**
     * Set the destination directory for class files (same behaviour of -d option)
     * 
     * @parameter expression="${project.build.outputDirectory}"
     */
    //@MojoParameter(required = false, expression="${project.build.outputDirectory}", description = "Set the destination directory for class files (same behaviour of -d option)")
    private File outputClassDirectory;

    @Override
    public List<File> getSourceDirectories()
    {
        List<File> dirs = new ArrayList<File>();
        dirs.add(sourceDirectory);


        for(String s : project.getCompileSourceRoots())
            dirs.add(new File(s));

        return dirs;
    }

    @Override
    protected File getOutputClassDirectory()
    {
        return outputClassDirectory;
    }

    protected void addCompileSourceRoot(MavenProject project, String dir)
    {
        project.addCompileSourceRoot(dir);
    }

    @Override
    public File getDefaultOutputDirectory()
    {
        return defaultOutputDirectory;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected java.util.Set<String> getClasspathElements( java.util.Set<String> result)
    {
        List<Resource> resources = project.getResources();

        if( resources!=null ) {
            for( Resource r : resources ) {
                result.add(r.getDirectory());
            }
        }

        result.addAll( classpathElements );
        
        return result;
     }


}
