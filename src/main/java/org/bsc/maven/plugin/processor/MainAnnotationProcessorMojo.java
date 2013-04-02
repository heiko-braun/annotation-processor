package org.bsc.maven.plugin.processor;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    public Set<File> getSourceDirectories()
    {
        List<String> sourceRoots = this.project.getCompileSourceRoots();
        Set result = new HashSet(sourceRoots.size() + 1);

        result.add(this.sourceDirectory);
        for (String s : sourceRoots) {
            result.add(new File(s));
        }

        return result;
    }

    protected File getOutputClassDirectory()
    {
        return this.outputClassDirectory;
    }

    protected void addCompileSourceRoot(MavenProject project, String dir)
    {
        project.addCompileSourceRoot(dir);
    }

    public File getDefaultOutputDirectory()
    {
        return this.defaultOutputDirectory;
    }

    protected Set<String> getClasspathElements(Set<String> result)
    {
        List<Resource> resources = this.project.getResources();

        if (resources != null) {
            for (Resource r : resources) {
                result.add(r.getDirectory());
            }
        }

        result.addAll(this.classpathElements);

        return result;
    }
}