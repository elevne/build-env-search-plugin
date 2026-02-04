package io.jenkins.plugins.envsearch;

import hudson.model.Result;
import hudson.model.Run;

public class BuildSearchResult {

    private final String jobName;
    private final int buildNumber;
    private final String buildUrl;
    private final String result;
    private final long timestamp;
    private final long duration;

    public BuildSearchResult(Run<?, ?> run) {
        this.jobName = run.getParent().getFullName();
        this.buildNumber = run.getNumber();
        this.buildUrl = run.getUrl();
        Result buildResult = run.getResult();
        if (run.isBuilding()) {
            this.result = "BUILDING";
        } else if (buildResult != null) {
            this.result = buildResult.toString();
        } else {
            this.result = "UNKNOWN";
        }
        this.timestamp = run.getStartTimeInMillis();
        this.duration = run.getDuration();
    }

    public String getJobName() {
        return jobName;
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    public String getBuildUrl() {
        return buildUrl;
    }

    public String getResult() {
        return result;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getDuration() {
        return duration;
    }
}
