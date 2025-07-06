#!/bin/bash

rg="petstore-containerapps-rg"
location="eastus"
env_name="petstore-env"
acr_name="perstorecontainerregistry"
ai_name="petstore-insights"

tag="latest"

#az acr login -n $acr_name --expose-token
#
#podman login $$acr_name.azurecr.io \
#  --username 00000000-0000-0000-0000-000000000000 \
#  --password eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IkFNTDU6UUNMWjpCRDQ1OkRSRDI6RkdESTo3Q1NGOkdVUEI6V1RWQTpVVUtJOjRKN1Y6Mjc0QTpQSkRVIn0.eyJqdGkiOiIwNTE4ZDM3Yy1hMWQ5LTRiMWUtYmQyMy1mYmEyN2U0YmI3ODQiLCJzdWIiOiJPbGVrc2lpX1Zla3NoeW5AZXBhbS5jb20iLCJuYmYiOjE3NDk4MTczMzQsImV4cCI6MTc0OTgyOTAzNCwiaWF0IjoxNzQ5ODE3MzM0LCJpc3MiOiJBenVyZSBDb250YWluZXIgUmVnaXN0cnkiLCJhdWQiOiJwZXJzdG9yZWNvbnRhaW5lcnJlZ2lzdHJ5LmF6dXJlY3IuaW8iLCJ2ZXJzaW9uIjoiMS4wIiwicmlkIjoiNmZmYTQzYjIwYmQ3NDRmY2JkOTFhYjI0ZWU3MWY5MGEiLCJncmFudF90eXBlIjoicmVmcmVzaF90b2tlbiIsImFwcGlkIjoiMDRiMDc3OTUtOGRkYi00NjFhLWJiZWUtMDJmOWUxYmY3YjQ2IiwidGVuYW50IjoiYjQxYjcyZDAtNGU5Zi00YzI2LThhNjktZjk0OWYzNjdjOTFkIiwicGVybWlzc2lvbnMiOnsiYWN0aW9ucyI6WyJyZWFkIiwid3JpdGUiLCJkZWxldGUiLCJtZXRhZGF0YS9yZWFkIiwibWV0YWRhdGEvd3JpdGUiLCJkZWxldGVkL3JlYWQiLCJkZWxldGVkL3Jlc3RvcmUvYWN0aW9uIl19LCJyb2xlcyI6W119.UP7KkNBgdShvmZeJkqSVAyk0Lp9Fv0WuyTa1KvhCbTxgaBaAo391ikj7qcYMsVmKT4Da92k_4QgSdCwyqvhjoguUBVlQkidFrGYaSYP23wR0cXiksC55DkyHnqMKQnjA47btvSSixItPpaMtRKh7XwrIeWSD5X9wHMR0XbZIvgkXoVCdq0Elqgvb-jIHz3vTPn404sH5U9bcjLw9UAinFRF2vfkuRpYppT9-SdGpBLy0N5ospHFs4Ww9B7GHIXvjeUd8twICd6LYfcTKcOaKTerxK31-s4Mg_OV1Y9BOl6DwFwOGOEfMCqdZImoYg3tNPvC-T7QnF8b4jdan_KT_JA


parse_named_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --*)
        key="${1/--/}"
        val="$2"
        eval "$key=\"\$val\""
        shift 2
        ;;
      *)
        echo "Unknown option: $1"
        return 1
        ;;
    esac
  done
}

create_resource_group() {
  parse_named_args "$@"
  if [[ -z "$rg" || -z "$location" ]]; then
    echo "Usage: create_resource_group --rg <resource_group> --location <location>"
    return 1
  fi
  az group create --name "$rg" --location "$location"
}

create_repository() {
  parse_named_args "$@"
  if [[ -z "$acr" || -z "$rg" || -z "$location" ]]; then
    echo "Usage: create_repository --acr <acr_name> --rg <resource_group> --location <location>"
    return 1
  fi
  az acr create --name "$acr" --resource-group "$rg" --location "$location" --sku Basic --admin-enabled true
}

podman_repository_login() {
  parse_named_args "$@"

  if [[ -z "$acr" ]]; then
    echo "Usage: podman_repository_login --acr <acr_name>"
    return 1
  fi

  echo "🔐 Getting access token for ACR: $acr..."
  local token_json
  token_json=$(az acr login -n "$acr" --expose-token --output json)

  local username="00000000-0000-0000-0000-000000000000"
  local password
  password=$(echo "$token_json" | jq -r '.accessToken')

  if [[ -z "$password" || "$password" == "null" ]]; then
    echo "❌ Failed to retrieve access token."
    return 1
  fi

  local registry="${acr}.azurecr.io"
  echo "📦 Logging in to $registry with Podman..."
  echo "$password" | podman login "$registry" --username "$username" --password-stdin
}

build_and_push_image() {
  parse_named_args "$@"
  if [[ -z "$acr" || -z "$image" || -z "$tag" || -z "$source" ]]; then
    echo "Usage: build_and_push_image --acr <acr_name> --image <name> --tag <tag> --source <path>"
    return 1
  fi

  full_image="$acr.azurecr.io/$image:$tag"
  pushd "$source" > /dev/null || { echo "❌ Failed to cd into $source"; return 1; }
#  podman build --platform linux/amd64,linux/arm64 -t "$full_image" .
  podman build --platform linux/amd64 -t "$full_image" .
  podman push "$full_image"
  popd > /dev/null
}

delete_petstore_resources() {
  local resource_group="$1"

  if [[ -z "$resource_group" ]]; then
    echo "Usage: delete_petstore_resources <resource_group>"
    return 1
  fi

  echo "⚠️  This will permanently delete resource group: '$resource_group' and all its resources."
  read -p "Are you sure? (y/n): " confirm
  if [[ "$confirm" != "y" ]]; then
    echo "❌ Deletion cancelled."
    return 0
  fi

  echo "🧨 Deleting resource group '$resource_group'..."
  az group delete --name "$resource_group" --yes --no-wait

  echo "✅ Deletion started. Use 'az group show --name \"$resource_group\"' to monitor status."
}

create_container_app_instance() {
  parse_named_args "$@"
  if [[ -z "$rg" || -z "$env" || -z "$region" || -z "$acr" || -z "$app" || -z "$image_path" || -z "$tag" ]]; then
    echo "Usage: create_container_app_instance --rg <resource_group> --env <env_name> --region <region> --acr <acr_name> --app <app_name> --image_path <path> --tag <tag> [--port <port>]"
    return 1
  fi
  port="${port:-8080}"

  registry="$acr.azurecr.io"
  image="$registry/$image_path:$tag"

  local username
  username=$(az acr credential show --name "$acr_name" --query username -o tsv)

  local password
  password=$(az acr credential show --name "$acr_name" --query 'passwords[0].value' -o tsv)

  if ! az containerapp env show --name "$env" --resource-group "$rg" &>/dev/null; then
    az containerapp env create --name "$env" --resource-group "$rg" --location "$region"
  fi

  az containerapp create \
    --name "$app" \
    --resource-group "$rg" \
    --environment "$env" \
    --image "$image" \
    --registry-server "$registry" \
    --registry-username "$username" \
    --registry-password "$password" \
    --target-port "$port" \
    --ingress external \
    --transport auto \
    --cpu 0.5 \
    --memory 1.0Gi
}

set_container_app_environment_variable() {
  parse_named_args "$@"

  if [[ -z "$rg" || -z "$app" || -z "$key" || -z "$value" ]]; then
    echo "Usage: set_containerapp_env_var --rg <resource_group> --app <container_app_name> --key <env_var_name> --value <env_var_value>"
    return 1
  fi

  echo "🔧 Setting environment variable '$key=$value' on app '$app'..."

  az containerapp update \
    --name "$app" \
    --resource-group "$rg" \
    --set-env-vars "$key=$value"
}

create_app_insights() {
  parse_named_args "$@"

  if [[ -z "$rg" || -z "$location" || -z "$name" ]]; then
    echo "Usage: create_app_insights --rg <resource_group> --location <region> --name <app_insights_name>"
    return 1
  fi

  echo "📊 Creating Application Insights: $name in $location..."

  az monitor app-insights component create \
    --app "$name" \
    --location "$location" \
    --resource-group "$rg" \
    --application-type web
}

get_app_insights_connection_string() {
  parse_named_args "$@"

  if [[ -z "$rg" || -z "$name" ]]; then
    echo "Usage: get_app_insights_connection_string --rg <resource_group> --name <app_insights_name>"
    return 1
  fi

  connection_string=$(az monitor app-insights component show \
    --app "$name" \
    --resource-group "$rg" \
    --query connectionString -o tsv)
}

set_containerapp_app_insights() {
  parse_named_args "$@"

  if [[ -z "$rg" || -z "$app" || -z "$connection_string" ]]; then
    echo "Usage: set_containerapp_app_insights --rg <resource_group> --app <container_app_name> --connection_string <conn_string>"
    return 1
  fi

  echo "🔧 Setting Application Insights for container app $app..."

  az containerapp update \
    --name "$app" \
    --resource-group "$rg" \
    --set-env-vars APPLICATIONINSIGHTS_CONNECTION_STRING="$connection_string"
}

setup_app_insights_for_all_petstore_apps() {
  apps=(petstoreapp petstoreorderservice petstorepetservice petstoreproductservice)

  # Step 1: Create Application Insights
  create_app_insights \
    --rg "$rg" \
    --location "$location" \
    --name "$ai_name"

  # Step 2: Get connection string
  get_app_insights_connection_string \
    --rg "$rg" \
    --name "$ai_name"

  # Step 3: Connect each app
  for app in "${apps[@]}"; do
    set_containerapp_app_insights \
      --rg "$rg" \
      --app "$app" \
      --connection_string "$connection_string"
  done
}

restart_all_petstore_apps() {
  local apps=(petstoreapp petstoreorderservice petstorepetservice petstoreproductservice)

  for app in "${apps[@]}"; do
    echo "🔍 Getting active revision for $app..."
    revision_name=$(az containerapp revision list \
      --name "$app" \
      --resource-group "$rg" \
      --query "[?active].name" -o tsv)

    if [[ -z "$revision_name" ]]; then
      echo "⚠️  No active revision found for $app. Skipping."
      continue
    fi

    echo "🔁 Restarting $app (revision: $revision_name)..."
    az containerapp revision restart \
      --name "$app" \
      --resource-group "$rg" \
      --revision "$revision_name"
  done

  echo "✅ All active PetStore app revisions restarted."
}