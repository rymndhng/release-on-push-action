FROM alpine:3.9

# RUN apk --no-cache add openssl git curl openssh-client bash
RUN apk --no-cache add curl

COPY lib /lib
COPY entrypoint.sh /entrypoint.sh
ENTRYPOINT [ "/entrypoint.sh" ]
