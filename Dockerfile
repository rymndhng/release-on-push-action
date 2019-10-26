FROM alpine:3.10

# RUN apk --no-cache add openssl git curl openssh-client bash
RUN apk --no-cache add curl bash jq

COPY lib /lib
COPY entrypoint.sh /entrypoint.sh
ENTRYPOINT [ "/entrypoint.sh" ]
