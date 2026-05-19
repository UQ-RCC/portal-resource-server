FROM rockylinux:9

WORKDIR /opt/portal-resource-server

ARG VERSION

COPY prs-${VERSION}.tgz /tmp/

RUN set -x \
  && yum install -y java-1.8.0-openjdk-headless openssh-clients \
  && echo "=== Archive contents ===" \
  && tar tzf /tmp/prs-${VERSION}.tgz | head -5 \
  && tar xzf /tmp/prs-${VERSION}.tgz \
     --strip-components=2 \
     --wildcards \
     "*/lib" \
     "*/bin" \
  && chmod +x bin/portal-resource-server \
  && rm /tmp/prs-${VERSION}.tgz

CMD ["bin/portal-resource-server"]