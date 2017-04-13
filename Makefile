.PHONY: docker-stage

docker-stage:
	sbt docker:stage

docker-publishLocal: docker-stage
	sbt docker:publishLocal


# docker tag e34106838dbe asia.gcr.io/assignment-164106/fraud-status-http-server
# docker tag e66be95bbc2d asia.gcr.io/assignment-164106/fraud-status-server
# docker tag aead8fff19b9 asia.gcr.io/assignment-164106/fraud-score-server

gcloud-push:
	@gcloud docker -- push asia.gcr.io/assignment-164106/fraud-status-http-server
	@gcloud docker -- push asia.gcr.io/assignment-164106/fraud-status-server
	@gcloud docker -- push asia.gcr.io/assignment-164106/fraud-score-server