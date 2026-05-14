# Build Environment Search Plugin

Jenkins 의 모든 잡(job) 을 가로질러 **환경변수 key/value 쌍** 으로 과거 빌드를 검색하는 플러그인.
"GERRIT_CHANGE_NUMBER=702276 으로 빌드된 게 어느 잡 몇 번이지?" 같은 질문을 잡 히스토리 일일이 뒤지지 않고 한 번에 찾는 용도.

- **검색 대상**: Freestyle / Pipeline / Multibranch / Folder 안의 모든 빌드
- **env 소스 2 가지**:
  - `ParametersAction` — 빌드 파라미터
  - `EnvironmentContributingAction` — Gerrit Trigger / GitHub plugin 등이 빌드 시점에 주입한 env
- **진입 경로**: 헤더 드롭다운 메뉴, 직접 URL (`/env-search/`), REST API
- **운영 정렬**: cerberus-jenkins (`jenkins/jenkins:2.562-jdk21`, prefix `/new-jenkins`) 환경에서 동작 확인

---

## 1. 요구 사항

| 항목 | 버전 | 비고 |
|------|------|------|
| Jenkins 런타임 | **2.555.x LTS 이상**, weekly 2.562 권장 | weekly 라인 follow 정책 — cerberus 와 동일 |
| Java 런타임 | **JDK 21** | class file major=65 → JDK 17 에서는 `UnsupportedClassVersionError` |
| Java 빌드 | **JDK 21** | maven-hpi-plugin 3.18+ 가 빌드 시점에 JDK 21 강제 |
| Maven | **3.9.6+** | parent POM 6.x 요구 |
| 권한 | `Jenkins.READ` | 검색 사용 시 |

POM 좌표:
- Parent: `org.jenkins-ci.plugins:plugin:6.2153.vcf31911d10c4`
- BOM: `io.jenkins.tools.bom:bom-2.555.x:6421.v4a_efb_4b_3a_61d`
- 명시 의존성: `org.jenkins-ci.plugins:structs` (BOM 이 공급, 현재 `362.va_b_695ef4fdf9`)

---

## 2. 빌드 방법

### 2.1 환경 준비

이 레포 작업자(swfarmer) 머신 기준 — 동일 위치에 사전 배치된 도구 사용:

```bash
export JAVA_HOME=/home/swfarmer/.local/java/jdk-21.0.11+10
export PATH="$JAVA_HOME/bin:/home/swfarmer/.local/maven/apache-maven-3.9.6/bin:$PATH"

java -version    # → openjdk 21.0.11 LTS Temurin
mvn -version     # → Apache Maven 3.9.6
```

다른 머신에서 빌드 시 Temurin JDK 21 + Maven 3.9.6 동등 버전을 사전 설치 후 위와 동일하게 `JAVA_HOME` / `PATH` 설정.

### 2.2 일반 빌드

```bash
mvn clean verify           # 단위/통합 테스트 포함 (~50s)
mvn -Dmaven.test.skip=true clean package   # 테스트 스킵 빌드 (~10s)
```

산출물:
- `target/build-env-search.hpi` — 배포용 플러그인 패키지 (Plugin Manager UI 업로드용)
- `target/build-env-search.jar` — 컴파일된 jar (.hpi 안에도 포함)

산출물 매니페스트(주요 항목):
```
Jenkins-Version: 2.562
Java-Version:    21
Plugin-Dependencies: structs:362.va_b_695ef4fdf9
Url:             http://mod.lge.com/hub/webos-devops/jenkins/plugins/env-search-plugin
```

### 2.3 로컬 dev Jenkins 실행 (`mvn hpi:run`)

코드 수정하면서 즉시 확인하고 싶을 때:

```bash
mvn hpi:run -Dport=8082 -Dhost=0.0.0.0
# 접속: http://localhost:8082/jenkins/
```

`work/` 디렉토리에 dev Jenkins 의 `JENKINS_HOME` 이 생성되어 잡/플러그인이 영속됩니다. 초기화하려면 `work/` 삭제 후 재기동.

### 2.4 Docker (운영 mirror 환경)

cerberus-jenkins 와 정렬된 환경 (`jenkins/jenkins:2.562-jdk21` + `prefix=/new-jenkins` + CSRF off):

```bash
mvn -Dmaven.test.skip=true clean package        # .hpi 먼저 빌드
docker compose down -v                          # (선택) 이전 상태 정리
docker compose up -d --build                    # 빌드 + 실행
docker compose logs -f jenkins                  # 부팅 로그 모니터
# 접속: http://localhost:8888/new-jenkins/
```

상세 절차는 [`docs/DOCKER-TEST.md`](docs/DOCKER-TEST.md) (`.gitignore` 대상이라 로컬 작업 폴더에만 존재).

### 2.5 CI 빌드 (GitLab)

`.gitlab-ci.yml` 이 tag push 시 `.hpi` 산출. 러너에 JDK 21 / Maven 3.9.6 사전 배치 필요.

```yaml
script:
  - export JAVA_HOME=/home/swfarmer/.local/java/jdk-21.0.11+10
  - export PATH="$JAVA_HOME/bin:/home/swfarmer/.local/maven/apache-maven-3.9.6/bin:$PATH"
  - mvn clean package -DskipTests -B
```

---

## 3. 설치 방법

### 3.1 운영 Jenkins (cerberus 등) 에 설치

1. `mvn -Dmaven.test.skip=true clean package` 로 `target/build-env-search.hpi` 빌드.
2. Jenkins 웹 UI → **Manage Jenkins → Plugins → Advanced settings → Deploy Plugin** 에 `.hpi` 업로드.
3. "Restart Jenkins when installation is complete" 체크 후 재시작.
4. 사이드바 / 헤더 드롭다운에 "Build Env Search" 항목 노출 확인.

### 3.2 Docker 이미지에 사전 배치

[`Dockerfile`](Dockerfile) 이 `target/build-env-search.hpi` 를 `/usr/share/jenkins/ref/plugins/` 로 복사 → 첫 부팅 시 Jenkins 가 자동으로 `/var/jenkins_home/plugins/` 로 이동시킴.

---

## 4. 사용 방법

### 4.1 웹 UI

진입: 헤더 드롭다운 메뉴 "Build Env Search" 또는 직접 URL `/env-search/`.

검색 폼:
| 필드 | 설명 | 기본값 |
|------|------|--------|
| Environment Variable Key | 찾고자 하는 env 이름 (예: `GERRIT_CHANGE_NUMBER`) | (필수) |
| Value | 찾고자 하는 값 (예: `702276`) | (필수) |
| Max Builds per Job | 잡당 최근 N개 빌드까지만 스캔 | 50 |
| Max Total Results | 전체 결과 개수 제한 | 500 |

결과 표 컬럼: Job Name / Build # / Status / Started / Duration.
Build # 클릭 시 해당 빌드 상세 페이지로 이동.

### 4.2 REST API

```
GET /env-search/search?envKey=<KEY>&envValue=<VALUE>&maxBuilds=<N>&maxResults=<N>
```

응답 (JSON):
```json
{
  "searchKey":   "GERRIT_CHANGE_NUMBER",
  "searchValue": "100105",
  "totalFound":  1,
  "results": [
    {
      "jobName":     "gerrit-pipeline-02",
      "buildNumber": 5,
      "buildUrl":    "job/gerrit-pipeline-02/5/",
      "result":      "SUCCESS",
      "timestamp":   1747200000000,
      "duration":    45000
    }
  ]
}
```

쿼리 파라미터:
| 이름 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| `envKey` | ✅ | — | env 이름 |
| `envValue` | ✅ | — | env 값 (정확 일치) |
| `maxBuilds` | ❌ | 50 | 잡당 최근 N개 빌드까지만 스캔 |
| `maxResults` | ❌ | 500 | 전체 결과 상한 |

호출 예시:
```bash
curl -sf "http://10.159.48.66:8888/new-jenkins/env-search/search?envKey=BRANCH&envValue=main" \
  | python3 -m json.tool
```

> CSRF 활성 환경에서는 GET 이라 crumb 불필요. POST 호출이 추가될 경우 `Jenkins-Crumb` 헤더 필요.

---

## 5. 주요 코드 소개

### 5.1 디렉토리 구조

```
src/
├── main/
│   ├── java/io/jenkins/plugins/envsearch/
│   │   ├── BuildEnvSearchRootAction.java   # RootAction + REST 엔드포인트 (HTTP 진입점)
│   │   ├── EnvSearchService.java           # 병렬 검색 핵심 로직
│   │   └── BuildSearchResult.java          # 검색 결과 DTO
│   ├── resources/
│   │   ├── index.jelly                                                # Plugin Manager 설명
│   │   └── io/jenkins/plugins/envsearch/BuildEnvSearchRootAction/
│   │       └── index.jelly                                            # 검색 폼 + 결과 표 UI
│   └── webapp/js/
│       └── env-search.js                   # 검색 UI JS (CSP 대응으로 inline에서 분리)
└── test/java/io/jenkins/plugins/envsearch/
    └── BuildEnvSearchRootActionTest.java   # JenkinsRule 기반 통합 테스트
```

### 5.2 `BuildEnvSearchRootAction` — HTTP 진입점

`hudson.model.RootAction` 구현 → Jenkins 루트 URL `/env-search/` 으로 라우팅.

```java
@Extension
public class BuildEnvSearchRootAction implements RootAction {

    private final EnvSearchService searchService = new EnvSearchService();

    @Override public String getIconFileName() { return "symbol-search"; }
    @Override public String getDisplayName()  { return "Build Env Search"; }
    @Override public String getUrlName()      { return "env-search"; }

    @GET
    public void doSearch(
            StaplerRequest2 req,
            StaplerResponse2 rsp,
            @QueryParameter String envKey,
            @QueryParameter String envValue,
            @QueryParameter String maxBuilds,
            @QueryParameter String maxResults) throws Exception {

        Jenkins.get().checkPermission(Jenkins.READ);
        // ... envKey/envValue/limits 파싱 후 searchService.search(...) 호출 →
        // JSON 직렬화하여 응답
    }
}
```

핵심 포인트:
- `@Extension` 만으로 Jenkins 가 자동 등록.
- `StaplerRequest2` / `StaplerResponse2` (Jenkins 2.475+ Jakarta Servlet 5).
- `@GET` 으로 verb 제한 → POST/PUT 차단.
- 응답은 `net.sf.json.JSONObject` (Jenkins 코어 내장).

### 5.3 `EnvSearchService` — 병렬 검색 로직

모든 잡을 `ExecutorService` (스레드 풀 최대 8) 로 동시 스캔. 30초 글로벌 타임아웃.

검색 흐름:
```
search(envKey, envValue, maxBuildsPerJob, maxResults)
  ↓
Jenkins.get().getAllItems(Job.class)             # 전체 잡 수집
  ↓
각 잡당 Callable 생성
  ↓
executor.invokeAll(tasks, 30s)                    # 병렬 실행 + 타임아웃
  ↓
각 잡 task:
  for run in job.getBuilds() [최근 maxBuildsPerJob 개 까지]:
    matched = findEnvValueInline(run, envKey)
    if matched == envValue: 결과에 추가
  ↓
findEnvValueInline(run, envKey):
  1. run.getAction(ParametersAction.class).getParameter(envKey)  → 값 있으면 반환
  2. run.getActions(EnvironmentContributingAction.class) 순회:
       eca.buildEnvironment(run, envVars); envVars.get(envKey)   → 값 있으면 반환
  3. 둘 다 없으면 null
  ↓
결과 모두 합산 → timestamp 내림차순 정렬 → maxResults 절단
```

핵심 코드:
```java
ParametersAction paramsAction = run.getAction(ParametersAction.class);
if (paramsAction != null) {
    ParameterValue param = paramsAction.getParameter(envKey);
    if (param != null) return param.getValue().toString();
}
for (EnvironmentContributingAction action : run.getActions(EnvironmentContributingAction.class)) {
    EnvVars envVars = new EnvVars();
    action.buildEnvironment(run, envVars);
    String value = envVars.get(envKey);
    if (value != null) return value;
}
```

> 실측: 30 빌드 검색이 ~5ms (병렬 효과). 1만 빌드 규모에서도 잡당 50개 cap + 8 스레드면 수초 이내.

### 5.4 `BuildSearchResult` — 결과 DTO

`hudson.model.Run` 으로부터 직렬화 가능한 필드만 추출:

```java
public BuildSearchResult(Run<?,?> run) {
    this.jobName     = run.getParent().getFullName();   // Folder/Multibranch 포함 full path
    this.buildNumber = run.getNumber();
    this.buildUrl    = run.getUrl();                    // "job/foo/42/" 상대 경로
    this.result      = run.isBuilding() ? "BUILDING"
                     : run.getResult() != null ? run.getResult().toString()
                     : "UNKNOWN";
    this.timestamp   = run.getStartTimeInMillis();
    this.duration    = run.getDuration();
}
```

### 5.5 검색 UI (`index.jelly` + `env-search.js`)

`src/main/resources/io/jenkins/plugins/envsearch/BuildEnvSearchRootAction/index.jelly`:
- Jenkins 표준 `<l:layout>` + `<l:side-panel>` + `<l:main-panel>` 구조.
- 입력 폼 (Key/Value/Max 옵션) + 결과 영역 (`<table>`).
- 인라인 `<script>` 는 **Jenkins 2.555+ CSP (`script-src 'self'`) 가 차단** 하므로 외부 `js/env-search.js` 로 분리.

`src/main/webapp/js/env-search.js`:
- `searchBtn` click 핸들러 → `fetch('/env-search/search?...')` → JSON 받아 결과 표 렌더.
- Enter 키로도 검색 트리거.
- Status 별 컬러링 (SUCCESS=green, FAILURE=red 등).

---

## 6. 라이선스

**LG Electronics Internal** — 사내 및 승인된 협력 업체 한정. 외부 배포 / 공개 금지.
상세 조건은 [LICENSE](LICENSE) 참조.

## 7. 참고 자료

- 운영 Jenkins 레퍼런스: [`webos-infraops/iac/manager-node/jenkins-setup`](http://mod.lge.com/hub/webos-infraops/iac/manager-node/jenkins-setup.git)
- 소스 (GitLab): <http://mod.lge.com/hub/webos-devops/jenkins/plugins/env-search-plugin>
- Jenkins Plugin Development: <https://www.jenkins.io/doc/developer/>
- Stapler API: <https://www.jenkins.io/doc/developer/handling-requests/>
