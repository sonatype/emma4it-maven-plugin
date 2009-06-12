package org.sonatype.maven.plugin.emma4it;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;

import com.vladium.emma.IAppConstants;
import com.vladium.emma.instr.InstrProcessor;
import com.vladium.util.XProperties;

/**
 * @goal instrument-project-artifact
 * @phase pre-integration-test
 */
public class InstrumentProjectArtifactMojo
    extends AbstractMojo
{
    /**
     * @component
     */
    private ArtifactFactory artifactFactory;

    /**
     * @component
     */
    private ArtifactResolver resolver;

    /**
     * @parameter expression="${localRepository}"
     */
    private ArtifactRepository localRepository;

    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     */
    private List<? extends ArtifactRepository> remoteRepositories;

    /**
     * @parameter expression="${project}"
     * @required
     */
    private MavenProject project;

    /**
     * The include filter for the classes to instrument/measure.
     *
     * @parameter
     * @since 1.2
     */
    private String[] includes;

    /**
     * The exclude filter for the classes to not instrument/measure.
     *
     * @parameter
     * @since 1.2
     */
    private String[] excludes;

    /**
     * @parameter default-value="overwrite"
     */
    private String outputMode;

    /**
     * Append emma jar on the instrumented jar
     *
     * @parameter default-value="false"
     */
    private boolean appendEmma;

    public void execute()
        throws MojoExecutionException
    {
        File projectFile = project.getArtifact().getFile();

        if ( projectFile == null || !projectFile.exists() )
        {
            throw new MojoExecutionException( "Unable to find project artifact file!" );
        }

        InstrProcessor.OutMode outMode = InstrProcessor.OutMode.nameToMode( outputMode );
        if ( outMode == null )
        {
            throw new MojoExecutionException( "invalid outputMode value: " + outputMode );
        }

        File emmaFolder = new File( project.getBuild().getDirectory(), "emma" );
        if ( !emmaFolder.exists() )
        {
            emmaFolder.mkdirs();
        }

        File instrumentedFile = new File( emmaFolder, projectFile.getName() );
        try
        {
            FileUtils.copyFile( projectFile, instrumentedFile );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

        InstrProcessor processor = InstrProcessor.create();
        processor.setAppName( IAppConstants.APP_NAME );

        processor.setInstrPath( new String[] { instrumentedFile.getAbsolutePath() }, true );
        processor.setInclExclFilter( getCoverageFilters() );
        processor.setOutMode( outMode );
        processor.setInstrOutDir( null );
        processor.setMetaOutFile( new File( emmaFolder, "coverage.em" ).getAbsolutePath() );
        processor.setMetaOutMerge( Boolean.TRUE );
        XProperties properties = new XProperties();
        processor.setPropertyOverrides( properties );

        processor.run();

        if ( appendEmma )
        {
            try
            {
                appendEmma( instrumentedFile );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }

        }

    }

    private void appendEmma( File instrumentedFile )
        throws MojoExecutionException, IOException
    {
        File original = new File( instrumentedFile.getParentFile(), "original.jar" );
        FileUtils.copyFile( instrumentedFile, original );
        instrumentedFile.delete();

        Artifact emma = artifactFactory.createArtifact( "emma", "emma", "2.0.5312", "compile", "jar" );
        try
        {
            resolver.resolve( emma, remoteRepositories, localRepository );
        }
        catch ( AbstractArtifactResolutionException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

        JarOutputStream finalJar = new JarOutputStream( new FileOutputStream( instrumentedFile ) );
        try
        {
            append( finalJar, original, false );
            append( finalJar, emma.getFile(), true );
        }
        finally
        {
            IOUtil.close( finalJar );
        }
    }

    private void append( JarOutputStream output, File jar, boolean excludeMetainf )
        throws IOException
    {
        JarFile ejar = new JarFile( jar );

        Enumeration<JarEntry> entries = ejar.entries();
        while ( entries.hasMoreElements() )
        {
            JarEntry jarEntry = entries.nextElement();
            if ( jarEntry.isDirectory() || ( excludeMetainf && jarEntry.getName().startsWith( "META-INF" ) ) )
            {
                continue;
            }

            output.putNextEntry( jarEntry );

            InputStream input = ejar.getInputStream( jarEntry );
            try
            {
                IOUtil.copy( input, output );
            }
            finally
            {
                IOUtil.close( input );
            }

            output.flush();
        }

    }

    private String[] getCoverageFilters()
    {
        Collection<String> filters = new LinkedHashSet<String>();
        if ( includes != null )
        {
            filters.addAll( Arrays.asList( includes ) );
        }
        if ( excludes != null )
        {
            for ( String exclude : excludes )
            {
                filters.add( '-' + exclude );
            }
        }
        return filters.isEmpty() ? null : filters.toArray( new String[filters.size()] );
    }

}
