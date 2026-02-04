package io.jenkins.plugins.envsearch;

import hudson.Extension;
import hudson.model.RootAction;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.GET;

@Extension
public class BuildEnvSearchRootAction implements RootAction {

    private final EnvSearchService searchService = new EnvSearchService();

    @Override
    public String getIconFileName() {
        return "symbol-search";
    }

    @Override
    public String getDisplayName() {
        return "Build Env Search";
    }

    @Override
    public String getUrlName() {
        return "env-search";
    }

    @GET
    public void doSearch(
            StaplerRequest req,
            StaplerResponse rsp,
            @QueryParameter String envKey,
            @QueryParameter String envValue,
            @QueryParameter String maxBuilds
    ) throws Exception {
        Jenkins.get().checkPermission(Jenkins.READ);

        int max;
        try {
            max = (maxBuilds != null && !maxBuilds.isEmpty()) ? Integer.parseInt(maxBuilds) : 50;
        } catch (NumberFormatException e) {
            max = 50;
        }

        List<BuildSearchResult> results = searchService.search(envKey, envValue, max);

        JSONObject response = new JSONObject();
        response.put("searchKey", envKey);
        response.put("searchValue", envValue);
        response.put("totalFound", results.size());

        JSONArray arr = new JSONArray();
        for (BuildSearchResult result : results) {
            JSONObject obj = new JSONObject();
            obj.put("jobName", result.getJobName());
            obj.put("buildNumber", result.getBuildNumber());
            obj.put("buildUrl", result.getBuildUrl());
            obj.put("result", result.getResult());
            obj.put("timestamp", result.getTimestamp());
            obj.put("duration", result.getDuration());
            arr.add(obj);
        }
        response.put("results", arr);

        rsp.setContentType("application/json;charset=UTF-8");
        rsp.getWriter().write(response.toString());
    }
}
