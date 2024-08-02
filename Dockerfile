FROM ubuntu:20.04
LABEL maintainer "Clovis Applied Engineering Solutions <clovis-solutions@outlook.com>"

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update && \    
    apt-get install -y \
        git \
        wget \
        build-essential \
        pkg-config \
        default-jre \ 
        maven

WORKDIR /opt

# WORKDIR /opt/openpnp

# RUN bash openpnp.sh

WORKDIR /app

# CMD OpenPnP