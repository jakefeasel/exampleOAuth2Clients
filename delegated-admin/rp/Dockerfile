FROM forgerock-docker-public.bintray.io/forgerock/openig:6.0.0

ENV OPENAM_INSTANCE http://login.sample.svc.cluster.local

ADD config /var/openig/config
ADD scripts /var/openig/scripts

USER root

RUN chown -R forgerock /var/openig

USER forgerock
