package org.sonatype.maven.plugin.emma4it;

public class ArtifactItem
{

    private String groupId;

    private String artifactId;

    private String version;

    private String classifier;

    private boolean instrument = true;

    private boolean resolveTransitively = true;

    private String type;

    public String getArtifactId()
    {
        return artifactId;
    }

    public String getClassifier()
    {
        return classifier;
    }

    public String getGroupId()
    {
        return groupId;
    }

    public boolean getInstrument()
    {
        return instrument;
    }

    public boolean getResolveTransitively()
    {
        return resolveTransitively;
    }

    public String getType()
    {
        return type;
    }

    public String getVersion()
    {
        return version;
    }

    public void setArtifactId( String artifactId )
    {
        this.artifactId = artifactId;
    }

    public void setClassifier( String classifier )
    {
        this.classifier = classifier;
    }

    public void setGroupId( String groupId )
    {
        this.groupId = groupId;
    }

    public void setInstrument( boolean instrument )
    {
        this.instrument = instrument;
    }

    public void setResolveTransitively( boolean resolveTransitively )
    {
        this.resolveTransitively = resolveTransitively;
    }

    public void setType( String type )
    {
        this.type = type;
    }

    public void setVersion( String version )
    {
        this.version = version;
    }
}
