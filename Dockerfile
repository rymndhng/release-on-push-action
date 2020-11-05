# See https://hub.docker.com/r/borkdude/babashka
FROM borkdude/babashka:0.2.3

WORKDIR /var/src/release-on-push-action

COPY src src

ENV BABASHKA_CLASSPATH /var/src/release-on-push-action/src

ENTRYPOINT [ "bb", "--main", "release-on-push-action.core" ]
