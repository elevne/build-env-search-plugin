package io.jenkins.plugins.envsearch;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BuildEnvSearchRootActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testSearchFindsMatchingBuild() throws Exception {
        // Create a freestyle project with a string parameter
        FreeStyleProject project = j.createFreeStyleProject("test-job");
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("GERRIT_CHANGE_NUMBER", "", "Gerrit change number")
        ));

        // Trigger a build with a specific parameter value
        FreeStyleBuild build = project.scheduleBuild2(0,
                new ParametersAction(new StringParameterValue("GERRIT_CHANGE_NUMBER", "702276"))
        ).get();
        j.waitForCompletion(build);

        // Search using the service
        EnvSearchService service = new EnvSearchService();
        var results = service.search("GERRIT_CHANGE_NUMBER", "702276", 50);

        assertEquals(1, results.size());
        assertEquals("test-job", results.get(0).getJobName());
        assertEquals(build.getNumber(), results.get(0).getBuildNumber());
        assertEquals("SUCCESS", results.get(0).getResult());
    }

    @Test
    public void testSearchDoesNotFindNonMatchingBuild() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("test-job-2");
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("GERRIT_CHANGE_NUMBER", "", "")
        ));

        FreeStyleBuild build = project.scheduleBuild2(0,
                new ParametersAction(new StringParameterValue("GERRIT_CHANGE_NUMBER", "999999"))
        ).get();
        j.waitForCompletion(build);

        EnvSearchService service = new EnvSearchService();
        var results = service.search("GERRIT_CHANGE_NUMBER", "702276", 50);

        assertEquals(0, results.size());
    }

    @Test
    public void testSearchWithEmptyInputReturnsEmpty() {
        EnvSearchService service = new EnvSearchService();

        assertEquals(0, service.search("", "value", 50).size());
        assertEquals(0, service.search("key", "", 50).size());
        assertEquals(0, service.search(null, "value", 50).size());
        assertEquals(0, service.search("key", null, 50).size());
    }

    @Test
    public void testRootActionIsRegistered() {
        BuildEnvSearchRootAction action = j.jenkins.getExtensionList(BuildEnvSearchRootAction.class).get(0);
        assertNotNull(action);
        assertEquals("env-search", action.getUrlName());
        assertEquals("Build Env Search", action.getDisplayName());
    }

    @Test
    public void testRestApiReturnsJson() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("api-test-job");
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("MY_VAR", "", "")
        ));

        FreeStyleBuild build = project.scheduleBuild2(0,
                new ParametersAction(new StringParameterValue("MY_VAR", "hello"))
        ).get();
        j.waitForCompletion(build);

        // Call REST API via JenkinsRule
        JenkinsRule.WebClient wc = j.createWebClient();
        var page = wc.goTo("env-search/search?envKey=MY_VAR&envValue=hello", "application/json");
        String json = page.getWebResponse().getContentAsString();
        JSONObject response = JSONObject.fromObject(json);

        assertEquals("MY_VAR", response.getString("searchKey"));
        assertEquals("hello", response.getString("searchValue"));
        assertEquals(1, response.getInt("totalFound"));
        assertEquals("api-test-job", response.getJSONArray("results").getJSONObject(0).getString("jobName"));
    }

    @Test
    public void testSearchMultipleJobsAndBuilds() throws Exception {
        // Job 1 with matching build
        FreeStyleProject job1 = j.createFreeStyleProject("job-alpha");
        job1.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("CHANGE_ID", "", "")
        ));
        FreeStyleBuild b1 = job1.scheduleBuild2(0,
                new ParametersAction(new StringParameterValue("CHANGE_ID", "100"))
        ).get();
        j.waitForCompletion(b1);

        // Job 2 with matching build
        FreeStyleProject job2 = j.createFreeStyleProject("job-beta");
        job2.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("CHANGE_ID", "", "")
        ));
        FreeStyleBuild b2 = job2.scheduleBuild2(0,
                new ParametersAction(new StringParameterValue("CHANGE_ID", "100"))
        ).get();
        j.waitForCompletion(b2);

        // Job 3 with non-matching build
        FreeStyleProject job3 = j.createFreeStyleProject("job-gamma");
        job3.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("CHANGE_ID", "", "")
        ));
        FreeStyleBuild b3 = job3.scheduleBuild2(0,
                new ParametersAction(new StringParameterValue("CHANGE_ID", "200"))
        ).get();
        j.waitForCompletion(b3);

        EnvSearchService service = new EnvSearchService();
        var results = service.search("CHANGE_ID", "100", 50);

        assertEquals(2, results.size());
    }
}
