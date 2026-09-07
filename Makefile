.PHONY: setup start stop infrastructure backend frontend test test-backend test-frontend clean

setup:
	cd frontend && npm ci --ignore-scripts

start:
	floci start

stop:
	floci stop

infrastructure: backend
	./scripts/terraform.sh init
	./scripts/terraform.sh apply -auto-approve -var='aws_endpoint_url=http://localhost.floci.io:4566'

backend:
	./scripts/package-lambda.sh

frontend:
	cd frontend && npm run build

test: test-backend test-frontend

test-backend:
	cd backend && mvn test

test-frontend:
	cd frontend && npm test

clean:
	cd backend && mvn clean
	rm -rf frontend/dist
