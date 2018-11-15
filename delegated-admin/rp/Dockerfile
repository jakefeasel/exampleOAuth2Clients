FROM forgerock-docker-public.bintray.io/forgerock/openig:6.5.0-RC1

ENV OPENAM_INSTANCE https://login.sample.svc.cluster.local

ADD config /var/openig/config
ADD scripts /var/openig/scripts

USER root

RUN chown -R forgerock /var/openig

USER forgerock
