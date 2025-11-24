**TODO:** The RabbitMQ part of this microservice

**Important Note:** You must give the CCA URL as an environment variable for this microservice to run:

docker run -d \
--name shopping-cart-service \
--network my-microservices \
-p 8081:8081 \
-e CREDIT_CARD_AUTHORIZER_URL=http://credit-card-authorizer:8082/credit-card-authorizer/authorize \
shopping-cart-service