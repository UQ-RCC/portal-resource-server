FROM rockylinux:9

WORKDIR /opt/portal-resource-server

ARG VERSION

COPY prs-${VERSION}.tgz /tmp/

RUN set -x \
  && yum install -y java-1.8.0-openjdk-headless openssh-clients \
  && tar xzf /tmp/prs-${VERSION}.tgz opt/portal-resource-server-${VERSION}/lib opt/portal-resource-server-${VERSION}/bin --strip-components=2 \
  && rm /tmp/prs-${VERSION}.tgz