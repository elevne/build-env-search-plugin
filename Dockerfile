FROM jenkins/jenkins:2.263.1-lts
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
RUN jenkins-plugin-cli --plugins structs:1.23
COPY target/build-env-search.hpi /usr/share/jenkins/ref/plugins/build-env-search.hpi
