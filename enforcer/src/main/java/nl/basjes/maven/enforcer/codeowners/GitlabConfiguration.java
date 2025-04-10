/*
 * CodeOwners Tools
 * Copyright (C) 2023-2025 Niels Basjes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.basjes.maven.enforcer.codeowners;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.regex.Pattern;

import static nl.basjes.maven.enforcer.codeowners.GitlabConfiguration.Level.ERROR;
import static nl.basjes.maven.enforcer.codeowners.GitlabConfiguration.Level.WARNING;

@Accessors(chain = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GitlabConfiguration {
    private ServerUrl   serverUrl   = new ServerUrl();
    private ProjectId   projectId   = new ProjectId();
    private AccessToken accessToken = new AccessToken();

    @Setter
    private boolean showAllApprovers = false;

    @Setter
    private boolean assumeUncheckableEmailExistsAndCanApprove = true;

    public enum FailLevel {
        NEVER,
        FATAL,
        ERROR,
        WARNING
    }

    public enum Level {
        INFO,
        WARNING,
        ERROR,
        FATAL
    }

    @Setter
    private FailLevel failLevel = FailLevel.ERROR;

    private ProblemLevels problemLevels = new ProblemLevels();
    @Getter @Setter
    public static class ProblemLevels {
        public Level roleNoUsers             = WARNING;
        public Level userUnknownEmail        = WARNING;
        public Level userDisabled            = WARNING;
        public Level approverDoesNotExist    = WARNING;
        public Level userTooLowPermissions   = WARNING;
        public Level groupTooLowPermissions  = WARNING;
        public Level userNotProjectMember    = ERROR;
        public Level groupNotProjectMember   = ERROR;
        public Level noValidApprovers        = ERROR;

        @Override
        public String toString() {
            return "Configured ProblemLevels: \n" +
                "- roleNoUsers            = " + roleNoUsers + "\n" +
                "- approverDoesNotExist   = " + approverDoesNotExist + "\n" +
                "- userUnknownEmail       = " + userUnknownEmail + "\n" +
                "- userDisabled           = " + userDisabled + "\n" +
                "- userTooLowPermissions  = " + userTooLowPermissions + "\n" +
                "- groupTooLowPermissions = " + groupTooLowPermissions + "\n" +
                "- userNotProjectMember   = " + userNotProjectMember + "\n" +
                "- groupNotProjectMember  = " + groupNotProjectMember + "\n" +
                "- noValidApprovers       = " + noValidApprovers + "\n";
        }
    }

    public GitlabConfiguration(ServerUrl serverUrl, ProjectId projectId, AccessToken accessToken) {
        this(serverUrl, projectId, accessToken, false, true, FailLevel.ERROR, new ProblemLevels());
    }

    public Boolean isValid() {
        return serverUrl.isValid() && projectId.isValid() && accessToken.isValid();
    }

    /**
     * Is this config assuming the default CI settings used for obtaining the serverUrl and projectId settings
     * AND is it missing all of them --> i.e. this is settings specific for running in CI and it is run now outside of CI.
     * @return If this is CI config running OUTSIDE of CI
     */
    public boolean isDefaultCIConfigRunningOutsideCI() {
        serverUrl.load();
        projectId.load();
        accessToken.load();
        return  serverUrl.isDefaultCIConfig() && !serverUrl.isValid() &&
                projectId.isDefaultCIConfig() && !projectId.isValid() &&
                !accessToken.isValid();
    }

    @Override
    public String toString() {
        return "GitlabConfiguration: {\n" +
            "  " + serverUrl + "\n" +
            "  " + projectId + "\n" +
            "  " + accessToken + "\n" +
            "  assumeUncheckableEmailExistsAndCanApprove = " + assumeUncheckableEmailExistsAndCanApprove + "\n" +
            "  " + problemLevels + "\n" +
            '}';
    }


    @Getter
    public static abstract class EnvironmentValueLoader{
        public abstract void load();

        private String source = null;
        private String value = null;
        private boolean loaded = false;
        private boolean valid = false;

        public boolean isValid() {
            load();
            return valid;
        }

        private static final Pattern NON_SPACE_STRING = Pattern.compile("^[a-zA-Z0-9:/\\\\+.%_-]+$");

        protected boolean checkValidity(String value) {
            return value != null && NON_SPACE_STRING.matcher(value).matches();
        }

        public String getValue() {
            load();
            if (!valid) {
                return null;
            }
            return value;
        }

        public String toString(String name) {
            load();
            return name + "='" + value + "' found via " + source + (isValid() ? " is valid." : " is NOT valid.");
        }

        /**
         * Load the value
         *
         * @param directValue                    The directly configured value
         * @param propertyId                     A readable form of the property where that is configured.
         * @param environmentVariableName        The name of the configured environment variable
         * @param defaultEnvironmentVariableName A builtin default environment variable name
         */
        protected void load(
            String directValue,
            String propertyId,
            String environmentVariableName,
            String defaultEnvironmentVariableName) {
            if (loaded) {
                return;
            }

            // Explicitly specified
            if (directValue != null && !directValue.isEmpty()) {
                value = directValue.trim();
                source = propertyId;
                loaded = true;
                valid = checkValidity(value);
                return;
            }
            // Get from environment
            String usedEnvVariableName = defaultEnvironmentVariableName;
            if (environmentVariableName != null && !environmentVariableName.isEmpty()) {
                usedEnvVariableName = environmentVariableName;
            }
            if (usedEnvVariableName == null || usedEnvVariableName.isEmpty()) {
                value = null;
                source = "invalid environment variable \""+usedEnvVariableName+"\"";
                loaded = true;
                valid = false;
                return;
            }

            value = System.getenv(usedEnvVariableName);
            if (value != null) {
                value = value.trim();
            }
            source = "environment variable " + usedEnvVariableName;
            loaded = true;
            valid = checkValidity(value);
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerUrl extends EnvironmentValueLoader {
        private static final String CI_SERVER_VARIABLE = "CI_SERVER_URL";
        private String url = null;
        private String environmentVariableName = null;

        private static final Pattern BASEURL_REGEX = Pattern.compile("^https?://[a-zA-Z0-9](?:(?:[a-zA-Z0-9-]*|(?<!-)\\.(?![-.]))*[a-zA-Z0-9]+)?(?::[0-9]+)?");

        public void load() {
            load(url, "gitlab.serverUrl.url", environmentVariableName, CI_SERVER_VARIABLE);
        }

        @Override
        protected boolean checkValidity(String value) {
            if (!super.checkValidity(value)) {
                return false;
            }
            return BASEURL_REGEX.matcher(value).matches();
        }

        public boolean isDefaultCIConfig() {
            return (CI_SERVER_VARIABLE.equals(environmentVariableName) || environmentVariableName == null) && url == null;
        }

        @Override
        public String toString() {
            return toString("ServerUrl");
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectId extends EnvironmentValueLoader {
        private static final String CI_PROJECT_VARIABLE = "CI_PROJECT_ID";
        private String id = null;
        private String environmentVariableName = null;

        public void load() {
            load(id, "gitlab.projectId.id", environmentVariableName, CI_PROJECT_VARIABLE);
        }

        public boolean isDefaultCIConfig() {
            return (CI_PROJECT_VARIABLE.equals(environmentVariableName) || environmentVariableName == null) && id == null;
        }

        @Override
        public String toString() {
            return toString("ProjectId");
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessToken extends EnvironmentValueLoader {
        private String environmentVariableName = null;

        public void load() {
            load(null, "Not allowed", environmentVariableName, null);
        }

        @Override
        public String toString() {
            // We are NOT printing the entire token.
            // The Gitlab tokens I have seen are usually "gl pat-" followed by about 20 random characters.

            if (!isValid()) {
                return "AccessToken found via " + getSource() + " is NOT valid.";
            }
            String token = getValue();
            String cleanedToken = "***";
            if (token.length() > 10) {
                cleanedToken =
                    token.substring(0, 6) + "*****" + token.substring(token.length() - 2);
            }
            return "AccessToken= '" + cleanedToken + "' found via " + getSource() + " is valid.";
        }
    }

}
