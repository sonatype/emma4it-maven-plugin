package org.sonatype.maven.plugin.emma4it;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;

import com.vladium.emma.IAppConstants;
import com.vladium.emma.instr.InstrProcessor;
import com.vladium.util.XProperties;

/**
 * @author marvin
 * @goal copy
 * @phase process-test-classes
 */
public class CopyRepositoryMojo
    extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     * @required
     */
    private MavenProject project;

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
    @SuppressWarnings( "unchecked" )
    private List remoteRepositories;

    /**
     * @component
     */
    private MavenProjectBuilder mavenProjectBuilder;

    /**
     * @component
     */
    private ArtifactMetadataSource artifactMetadataSource;

    /**
     * @parameter
     */
    private ArtifactItem[] artifactItems;

    /**
     * @parameter default-value="${project.build.directory}/fake-repo"
     */
    private File output;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        for ( ArtifactItem artifactItem : artifactItems )
        {
            Artifact artifact;
            try
            {
                artifact = getArtifact( artifactItem );
            }
            catch ( AbstractArtifactResolutionException e )
            {
                throw new MojoExecutionException( "Unable to resolve artifact", e );
            }

            Set<Artifact> transitiveArtifacts;
            if ( artifactItem.getResolveTransitively() )
            {
                Artifact pomArtifact =
                    artifactFactory.createArtifact( artifactItem.getGroupId(), artifactItem.getArtifactId(),
                                                    artifactItem.getVersion(), artifactItem.getClassifier(), "pom" );

                try
                {
                    MavenProject pomProject =
                        mavenProjectBuilder.buildFromRepository( pomArtifact, remoteRepositories, localRepository );
                    Set dependencies = pomProject.createArtifacts( artifactFactory, null, null );
                    ArtifactResolutionResult arr =
                        resolver.resolveTransitively( dependencies, pomArtifact, localRepository, remoteRepositories,
                                                      artifactMetadataSource, null );
                    transitiveArtifacts = arr.getArtifacts();
                }
                catch ( ProjectBuildingException e )
                {
                    throw new MojoExecutionException( "Unable to resolve artifact project", e );
                }
                catch ( InvalidDependencyVersionException e )
                {
                    throw new MojoExecutionException( "Invalid dependency version", e );
                }
                catch ( AbstractArtifactResolutionException e )
                {
                    throw new MojoExecutionException( "Unable to resolve artifact", e );
                }

            }
            else
            {
                transitiveArtifacts = new HashSet<Artifact>();
                transitiveArtifacts.add( artifact );
            }

            for ( Artifact transitiveArtifact : transitiveArtifacts )
            {
                copy( transitiveArtifact );
            }

            if ( artifactItem.getInstrument() )
            {
                File file = copy( artifact );
                instrument( file );
            }
        }
    }

    private void instrument( File file )
    {
        InstrProcessor.OutMode outMode = InstrProcessor.OutMode.nameToMode( "overwrite" );

        File emmaFolder = new File( project.getBuild().getDirectory(), "emma" );
        if ( !emmaFolder.exists() )
        {
            emmaFolder.mkdirs();
        }

        InstrProcessor processor = InstrProcessor.create();
        processor.setAppName( IAppConstants.APP_NAME );

        processor.setInstrPath( new String[] { file.getAbsolutePath() }, true );
        processor.setInclExclFilter( null );
        processor.setOutMode( outMode );
        processor.setInstrOutDir( null );
        processor.setMetaOutFile( new File( emmaFolder, "coverage.em" ).getAbsolutePath() );
        processor.setMetaOutMerge( Boolean.TRUE );
        XProperties properties = new XProperties();
        processor.setPropertyOverrides( properties );

        processor.run();
    }

    private File copy( Artifact artifact )
        throws MojoExecutionException
    {
        File localRepo = new File(localRepository.getBasedir());
        File source = artifact.getFile();
        File sourceFolder = source.getParentFile();
        String relativePath = sourceFolder.getAbsolutePath().substring( localRepo.getAbsolutePath().length() );
        File targetDir = new File( output, relativePath );
        File targetFile = new File( targetDir, source.getName() );
        try
        {
            FileUtils.copyDirectory( sourceFolder, targetDir );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Unable to copy artifact", e );
        }
        return targetFile;
    }

    private Artifact getArtifact( ArtifactItem artifactItem )
        throws AbstractArtifactResolutionException
    {
        Artifact artifact =
            artifactFactory.createArtifactWithClassifier( artifactItem.getGroupId(), artifactItem.getArtifactId(),
                                                          artifactItem.getVersion(), artifactItem.getType(),
                                                          artifactItem.getClassifier() );
        resolver.resolve( artifact, remoteRepositories, localRepository );
        return artifact;
    }

}
