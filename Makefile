#!/bin/bash

.PHONY: docker-publishLocal

GIT_COMMIT_HASH=$(shell git log --pretty=format:'%h' -n 1)

default:
	@echo ${GIT_COMMIT_HASH}

docker-publishLocal:
	sbt docker:publishLocal

#	FRAUD_SCORE_IMAGE_ID = docker images fraud-score-server -q
#	docker tag $FRAUD_SCORE_IMAGE_ID

docker-tags:
	@docker tag $(shell docker images fraud-status-http-server -q) asia.gcr.io/assignment-164106/fraud-status-http-server:${GIT_COMMIT_HASH}
	@docker tag $(shell docker images fraud-status-server -q)      asia.gcr.io/assignment-164106/fraud-status-server:${GIT_COMMIT_HASH}
	@docker tag $(shell docker images fraud-score-server -q)       asia.gcr.io/assignment-164106/fraud-score-server:${GIT_COMMIT_HASH}
	@docker tag $(shell docker images fraud-id-manager -q)         asia.gcr.io/assignment-164106/fraud-id-manager:${GIT_COMMIT_HASH}
	@docker images | grep ${GIT_COMMIT_HASH}

gcloud-push:
	@gcloud docker -- push asia.gcr.io/assignment-164106/fraud-status-http-server:${GIT_COMMIT_HASH}
	@gcloud docker -- push asia.gcr.io/assignment-164106/fraud-status-server:${GIT_COMMIT_HASH}
	@gcloud docker -- push asia.gcr.io/assignment-164106/fraud-score-server:${GIT_COMMIT_HASH}
	@gcloud docker -- push asia.gcr.io/assignment-164106/fraud-id-manager:${GIT_COMMIT_HASH}

yaml-apply-discovery:
	@kubectl apply -f kubernetes/discovery-service.yaml

yaml-apply-external:
	@kubectl apply -f kubernetes/external-service.yaml

yaml-apply-http:
	@cat kubernetes/fraud-status-http-server.yaml | sed "s/GIT_COMMIT_HASH/${GIT_COMMIT_HASH}/g" | kubectl apply -f -

yaml-apply-status:
	@cat kubernetes/fraud-status-server.yaml      | sed "s/GIT_COMMIT_HASH/${GIT_COMMIT_HASH}/g" | kubectl apply -f -

yaml-apply-score:
	@cat kubernetes/fraud-score-server.yaml       | sed "s/GIT_COMMIT_HASH/${GIT_COMMIT_HASH}/g" | kubectl apply -f -
	@cat kubernetes/fraud-score-server.yaml       | sed "s/GIT_COMMIT_HASH/${GIT_COMMIT_HASH}/g" | cat

yaml-apply-id:
	@cat kubernetes/fraud-id-manager-server.yaml  | sed "s/GIT_COMMIT_HASH/${GIT_COMMIT_HASH}/g" | kubectl apply -f -
	@cat kubernetes/fraud-id-manager-server.yaml  | sed "s/GIT_COMMIT_HASH/${GIT_COMMIT_HASH}/g" | cat


gcloud-create:
	@gcloud container clusters create --cluster-version=1.6.1 assignment-cluser-1 --machine-type=f1-micro