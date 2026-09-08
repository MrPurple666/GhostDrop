.PHONY: setup start stop infrastructure serve dev backend frontend test test-backend test-frontend e2e clean

setup:
	cd frontend && npm ci --ignore-scripts

start:
	floci start

stop:
	floci stop

infrastructure: backend
	./scripts/terraform.sh init
	./scripts/terraform.sh apply -auto-approve -var='aws_endpoint_url=http://host.docker.internal:4566' -var='public_endpoint_url=http://localhost.floci.io:4566'

backend:
	./scripts/package-lambda.sh

frontend:
	cd frontend && npm run build

serve: frontend
	node scripts/dev-server.mjs

dev:
	./scripts/dev.sh

test: test-backend test-frontend

test-backend:
	cd backend && mvn test

test-frontend:
	cd frontend && npm test

e2e:
	./scripts/e2e.sh

clean:
	cd backend && mvn clean
	rm -rf frontend/dist
