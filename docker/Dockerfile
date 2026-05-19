FROM rockylinux:9

WORKDIR /opt/portal-resource-server

ARG VERSION

COPY prs-${VERSION}.tgz /tmp/

RUN set -eux \
    && yum install -y java-1.8.0-openjdk-headless openssh-clients \
    && tar xzf /tmp/prs-${VERSION}.tgz -C /tmp \
    && mv /tmp/portal-resource-server-${VERSION}/* /opt/portal-resource-server/ \
    && chmod +x /opt/portal-resource-server/bin/portal-resource-server \
    && echo "=== Final layout ===" \
    && find /opt/portal-resource-server -maxdepth 2 -type f \
    && rm -rf /tmp/portal-resource-server-${VERSION} \
    && rm /tmp/prs-${VERSION}.tgz

CMD ["/opt/portal-resource-server/bin/portal-resource-server"]