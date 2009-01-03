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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import com.vladium.emma.IAppConstants;
import com.vladium.emma.instr.InstrProcessor;
import com.vladium.util.XProperties;

/**
 * @goal instrument
 * @phase process-test-classes
 */
public class ArtifactInstrumenterMojo
    extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     * @required
     */
    private MavenProject project;

    /** @component */
    private ArtifactFactory artifactFactory;

    /** @component */
    private ArtifactResolver resolver;

    /** @parameter expression="${localRepository}" */
    private ArtifactRepository localRepository;

    /** @parameter expression="${project.remoteArtifactRepositories}" */
    private List<? extends ArtifactRepository> remoteRepositories;

    /**
     * @parameter
     */
    private ArtifactItem[] artifactItems;

    /**
     * @parameter
     */
    private File[] jarFiles;

    /**
     * The collection of JAR files to instrument.
     * 
     * @parameter
     * @since 1.2
     */
    private FileSet[] jarSets;

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

    public void execute()
        throws MojoExecutionException
    {
        String[] instrPath = collectInstrumentationPath();

        if ( instrPath.length <= 0 )
        {
            getLog().error( "Nothing found to instrument!" );
            return;
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

        InstrProcessor processor = InstrProcessor.create();
        processor.setAppName( IAppConstants.APP_NAME );

        processor.setInstrPath( instrPath, true );
        processor.setInclExclFilter( getCoverageFilters() );
        processor.setOutMode( outMode );
        processor.setInstrOutDir( null );
        processor.setMetaOutFile( new File( emmaFolder, "coverage.em" ).getAbsolutePath() );
        processor.setMetaOutMerge( Boolean.TRUE );
        XProperties properties = new XProperties();
        processor.setPropertyOverrides( properties );

        processor.run();
    }

    private String[] collectInstrumentationPath()
        throws MojoExecutionException
    {
        getLog().debug( "Collecting JAR files" );

        List<String> instrPath = new ArrayList<String>();

        if ( artifactItems != null && artifactItems.length != 0 )
        {
            List<Artifact> artifacts = resolveArtifacts();
            instrPath.addAll( Arrays.asList( getPaths( artifacts ) ) );
        }

        if ( jarFiles != null )
        {
            for ( File jar : jarFiles )
            {
                String path = jar.getAbsolutePath();
                if ( jar.exists() )
                {
                    instrPath.add( path );
                }
                else
                {
                    getLog().warn( "JAR " + path + " not found!" );
                }
            }
        }

        if ( jarSets != null && jarSets.length > 0 )
        {
            for ( FileSet fileSet : jarSets )
            {
                if ( fileSet.getDirectory() == null )
                {
                    throw new MojoExecutionException( "Missing base directory for JAR set " + fileSet );
                }
                else if ( !fileSet.getDirectory().isDirectory() )
                {
                    getLog().warn( "Ignored non-existing JAR set directory " + fileSet.getDirectory() );
                }
                else
                {
                    instrPath.addAll( fileSet.scan( true, false ) );
                }
            }
        }

        if ( getLog().isDebugEnabled() )
        {
            for ( String path : instrPath )
            {
                getLog().debug( "  " + path );
            }
        }

        return instrPath.toArray( new String[instrPath.size()] );
    }

    private String[] getPaths( List<Artifact> artifacts )
    {
        List<String> paths = new ArrayList<String>();
        for ( Artifact artifact : artifacts )
        {
            paths.add( artifact.getFile().getAbsolutePath() );
        }
        return paths.toArray( new String[0] );
    }

    private List<Artifact> resolveArtifacts()
        throws MojoExecutionException
    {
        List<Artifact> artifacts = new ArrayList<Artifact>();
        for ( ArtifactItem artifactItem : artifactItems )
        {
            Artifact artifact =
                artifactFactory.createArtifactWithClassifier( artifactItem.getGroupId(), artifactItem.getArtifactId(),
                                                              artifactItem.getVersion(), artifactItem.getType(),
                                                              artifactItem.getClassifier() );
            try
            {
                resolver.resolve( artifact, remoteRepositories, localRepository );
            }
            catch ( AbstractArtifactResolutionException e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }
            artifacts.add( artifact );
        }
        return artifacts;
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
