IMAGE_NAME=twelve-dollar-challenge:latest
CONTAINER_NAME=twelve-dollar-server

.PHONY: docker-build
docker-build:
	docker build -t $(IMAGE_NAME) .

.PHONY: docker-run
docker-run:
	docker run --rm \
		--name $(CONTAINER_NAME) \
		--cpus="0.33" \
		--cpuset-cpus="0" \
		--memory="1800m" \
		--memory-swap="1800m" \
		--ulimit nofile=65535:65535 \
		-p 3000:80 \
		$(IMAGE_NAME)

.PHONY: docker-stop
docker-stop:
	docker stop $(CONTAINER_NAME) || true

.PHONY: docker-start
docker-start: docker-build docker-run
