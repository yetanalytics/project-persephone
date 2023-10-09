FROM alpine:3.14

ADD target/bundle /persephone
ADD .java_modules /persephone/.java_modules

# replace the linux runtime via jlink
RUN apk update \
    && apk upgrade \
    && apk add ca-certificates \
    && update-ca-certificates \
    && apk add --no-cache openjdk11 \
    && mkdir -p /persephone/runtimes \
    && jlink --output /persephone/runtimes/linux/ --add-modules $(cat /persephone/.java_modules) \
    && apk del openjdk11 \
    && rm -rf /var/cache/apk/*

WORKDIR /persephone
EXPOSE 8080
EXPOSE 8081
CMD ["/persephone/bin/persephone.sh", "--help"]
