FROM jenkins/jenkins:2.562-jdk21

# Setup Wizard 스킵 (CI/단발 검증용)
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
# cerberus-jenkins 와 동일 URL prefix
ENV JENKINS_OPTS="--prefix=/new-jenkins"

# cerberus-jenkins init.groovy.d 정렬 (CSRF crumb 비활성)
# executors.groovy(=0) 는 테스트 빌드 실행을 위해 의도적으로 제외
COPY docker/init.groovy.d/crumbIssuer.groovy /usr/share/jenkins/ref/init.groovy.d/

# BOM 2.555.x 공급 structs 사전 설치
RUN jenkins-plugin-cli --plugins structs:362.va_b_695ef4fdf9

# 빌드된 .hpi 사전 배치
COPY target/build-env-search.hpi /usr/share/jenkins/ref/plugins/build-env-search.hpi
