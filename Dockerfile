# See https://hub.docker.com/r/borkdude/babashka
FROM borkdude/babashka:0.0.78

WORKDIR /var/src/release-on-push-action

COPY src src
COPY entrypoint.sh entrypoint.sh

ENTRYPOINT [ "./entrypoint.sh" ]
