# sh ./generate_env.sh <dev|uat|prod>

ENV=$1

if [ -z "$ENV" ]
then
  echo "No environment specified. Usage: sh ./generate_env.sh <dev|uat|prod>"
  exit 1
fi


subscription_dev="bbe47ad4-08b3-4925-94c5-1278e5819b86"
subscription_uat="26abc801-0d8f-4a6e-ac5f-8e81bcc09112"
subscription_prod="b9fc9419-6097-45fe-9f74-ba0641c91912"

resource_group_dev="pagopa-d-weu-bizevents-rg"
resource_group_uat="pagopa-u-weu-bizevents-rg"
resource_group_prod="pagopa-p-weu-bizevents-rg"

account_dev="pagopa-d-weu-bizevents-ds-cosmos-account"
account_uat="pagopa-u-weu-bizevents-ds-cosmos-account"
account_prod="pagopa-p-weu-bizevents-ds-cosmos-account"


if [ "$ENV" = "dev" ]; then
  endpoint=$(az cosmosdb show --subscription $subscription_dev --resource-group $resource_group_dev --name $account_dev --query documentEndpoint --output tsv)
  key=$(az cosmosdb keys list --subscription $subscription_dev --resource-group $resource_group_dev --name $account_dev --query primaryMasterKey --output tsv)
fi
if [ "$ENV" = "uat" ]; then
  endpoint=$(az cosmosdb show --subscription $subscription_uat --resource-group $resource_group_uat --name $account_uat --query documentEndpoint --output tsv)
  key=$(az cosmosdb keys list --subscription $subscription_uat --resource-group $resource_group_uat --name $account_uat --query primaryMasterKey --output tsv)
fi
if [ "$ENV" = "prod" ]; then
  endpoint=$(az cosmosdb show --subscription $subscription_prod --resource-group $resource_group_prod --name $account_prod --query documentEndpoint --output tsv)
  key=$(az cosmosdb keys list --subscription $subscription_prod --resource-group $resource_group_prod --name $account_prod --query primaryMasterKey --output tsv)
fi


echo ENDPOINT="${endpoint}" > .env
echo KEY="${key}" >> .env

echo 'SUCCESS: .env file generated'
