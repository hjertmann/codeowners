package nl.basjes.maven.enforcer.codeowners;
 
import javax.inject.Inject;
import javax.inject.Named;
 
import java.util.List;
 
import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
 
/**
 * Custom Enforcer Rule - example
 */
@Named("allFilesHaveCodeOwners") // rule name - must start from lowercase character
public class AllFilesHaveCodeOwners extends AbstractEnforcerRule {
 
    /**
     * Simple param. This rule fails if the value is true.
     */
    private boolean shouldIfail = false;
 
    /**
     * Rule parameter as list of items.
     */
    private List<String> listParameters;
 
    // Inject needed Maven components
 
    @Inject
    private MavenProject project;
 
    @Inject
    private MavenSession session;
 
    @Inject
    private RuntimeInformation runtimeInformation;
 
    public void execute() throws EnforcerRuleException {
 
        getLog().info("Retrieved Target Folder: " + project.getBuild().getDirectory());
        getLog().info("Retrieved ArtifactId: " + project.getArtifactId());
        getLog().info("Retrieved Project: " + project);
        getLog().info("Retrieved Maven version: " + runtimeInformation.getMavenVersion());
        getLog().info("Retrieved Session: " + session);
        getLog().warnOrError("Parameter shouldIfail: " + shouldIfail);
        getLog().info(() -> "Parameter listParameters: " + listParameters);
 
        if (this.shouldIfail) {
            throw new EnforcerRuleException("Failing because my param said so.");
        }
    }
 
    /**
     * If your rule is cacheable, you must return a unique id when parameters or conditions
     * change that would cause the result to be different. Multiple cached results are stored
     * based on their id.
     * <p>
     * The easiest way to do this is to return a hash computed from the values of your parameters.
     * <p>
     * If your rule is not cacheable, then you don't need to override this method or return null
     */
    @Override
    public String getCacheId() {
        //no hash on boolean...only parameter so no hash is needed.
        return Boolean.toString(shouldIfail);
    }
 
    /**
     * A good practice is provided toString method for Enforcer Rule.
     * <p>
     * Output is used in verbose Maven logs, can help during investigate problems.
     *
     * @return rule description
     */
    @Override
    public String toString() {
        return String.format("MyCustomRule[shouldIfail=%b]", shouldIfail);
    }
}

