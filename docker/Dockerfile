FROM rockylinux:9

WORKDIR /opt/portal-resource-server

ARG VERSION

COPY prs-${VERSION}.tgz /tmp/

RUN set -x \
  && yum install -y java-1.8.0-openjdk-headless openssh-clients \
  && tar tzf /tmp/prs-${VERSION}.tgz | head -10 \
  && tar xzf /tmp/prs-${VERSION}.tgz \
     --strip-components=2 \
     --wildcards \
     "*/lib" \
     "*/bin" \
  && rm /tmp/prs-${VERSION}.tgz