# Build Environment Search Plugin

Jenkins plugin to search builds by environment variable key/value pairs and view their statuses.

## Features

- **Web UI**: Sidebar menu "Build Env Search" with search form and results table
- **REST API**: JSON endpoint for programmatic access
- **Full search**: Searches across all jobs (Freestyle, Pipeline, folders, multibranch)
- **Environment variable sources**: ParametersAction, EnvironmentContributingAction (Gerrit Trigger, etc.)

## Usage

### Web UI

1. Click **"Build Env Search"** in the Jenkins sidebar
2. Enter the environment variable key (e.g., `GERRIT_CHANGE_NUMBER`)
3. Enter the value to search for (e.g., `702276`)
4. Click **Search**

### REST API

```
GET /env-search/search?envKey=GERRIT_CHANGE_NUMBER&envValue=702276&maxBuilds=50
```

Response:

```json
{
  "searchKey": "GERRIT_CHANGE_NUMBER",
  "searchValue": "702276",
  "totalFound": 2,
  "results": [
    {
      "jobName": "my-job",
      "buildNumber": 42,
      "buildUrl": "job/my-job/42/",
      "result": "SUCCESS",
      "timestamp": 1706000000000,
      "duration": 45000
    }
  ]
}
```

#### Parameters

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| envKey | Yes | - | Environment variable name |
| envValue | Yes | - | Value to search for |
| maxBuilds | No | 50 | Max builds to search per job |

## Requirements

- Jenkins 2.462+
- Java 17+

## Build

```bash
mvn clean verify
```

## Local Testing

```bash
mvn hpi:run
```

Then open http://localhost:8080/jenkins/env-search/

## Installation

Download the `.hpi` file from the releases page and install via **Manage Jenkins > Plugins > Advanced > Deploy Plugin**.

## License

[MIT License](LICENSE)
