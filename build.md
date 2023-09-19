
mvn clean install

docker build -t open-fhir .

Use AWS SSO

aws ecr get-login-password --region eu-west-2 | docker login --username AWS --password-stdin 365027538941.dkr.ecr.eu-west-2.amazonaws.com

docker tag open-fhir:latest 365027538941.dkr.ecr.eu-west-2.amazonaws.com/open-fhir:latest
docker tag open-fhir:latest 365027538941.dkr.ecr.eu-west-2.amazonaws.com/open-fhir:6.8.6

docker push 365027538941.dkr.ecr.eu-west-2.amazonaws.com/open-fhir:latest

docker push 365027538941.dkr.ecr.eu-west-2.amazonaws.com/open-fhir:6.8.6
