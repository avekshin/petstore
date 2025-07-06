#!/bin/bash

# -------------------------
# Configuration Variables
# -------------------------
rg="petstore-containerapps-rg"
location="eastus"
env_name="petstore-env"
acr_name="perstorecontainerregistry"
ai_name="petstore-insights"
storage="petstorestorageov202507"
func_name="orderitemsreserverov202507"
identity="petstore-mi"
image="orderitemsreserver:latest"

# -------------------------
# Step 1: Create Storage Account
# -------------------------
echo "📦 Creating storage account: $storage..."
az storage account create \
  --name "$storage" \
  --resource-group "$rg" \
  --location "$location" \
  --sku Standard_LRS \
  --kind StorageV2

# -------------------------
# Step 2: Create Application Insights
# -------------------------
echo "📊 Creating Application Insights: $ai_name..."
az monitor app-insights component create \
  --app "$ai_name" \
  --location "$location" \
  --resource-group "$rg" \
  --application-type web

# -------------------------
# Step 3: Create Managed Identity
# -------------------------
echo "🔐 Creating managed identity: $identity..."
az identity create \
  --name "$identity" \
  --resource-group "$rg" \
  --location "$location"

uami_id=$(az identity show --name "$identity" --resource-group "$rg" --query id -o tsv)
principalId=$(az identity show --name "$identity" --resource-group "$rg" --query principalId -o tsv)

# -------------------------
# Step 4: Assign Roles to Managed Identity
# -------------------------
echo "🔑 Assigning AcrPull and Blob Data Owner roles..."
acrId=$(az acr show --name "$acr_name" --resource-group "$rg" --query id -o tsv)
az role assignment create --assignee-object-id "$principalId" --role AcrPull --scope "$acrId" || true

storageId=$(az storage account show --name "$storage" --resource-group "$rg" --query id -o tsv)
az role assignment create --assignee-object-id "$principalId" --role "Storage Blob Data Owner" --scope "$storageId" || true

# -------------------------
# Step 5: Create the Function App (without image)
# -------------------------
echo "🚀 Creating function app: $func_name in Container Apps environment..."
az functionapp create \
  --name "$func_name" \
  --resource-group "$rg" \
  --storage-account "$storage" \
  --environment "$env_name" \
  --functions-version 4 \
  --workload-profile-name "Consumption" \
  --assign-identity "$uami_id" \
  --runtime custom

# -------------------------
# Step 6: Configure Container Image
# -------------------------
echo "🛠️ Setting image from ACR using managed identity..."
az functionapp config container set \
  --name "$func_name" \
  --resource-group "$rg" \
  --image "$acr_name.azurecr.io/$image" \
  --registry-server "$acr_name.azurecr.io" \
  --enable-acr-identity true \
  --acr-identity "$uami_id"

# -------------------------
# Step 7: Replace AzureWebJobsStorage with identity-based config
# -------------------------
echo "🔄 Replacing AzureWebJobsStorage setting..."
clientId=$(az identity show --name "$identity" --resource-group "$rg" --query clientId -o tsv)

az functionapp config appsettings delete \
  --name "$func_name" \
  --resource-group "$rg" \
  --setting-names AzureWebJobsStorage

az functionapp config appsettings set \
  --name "$func_name" \
  --resource-group "$rg" \
  --settings \
    AzureWebJobsStorage__accountName="$storage" \
    AzureWebJobsStorage__credential="managedidentity" \
    AzureWebJobsStorage__clientId="$clientId"

echo "✅ Deployment complete: $func_name is running in Container Apps environment."