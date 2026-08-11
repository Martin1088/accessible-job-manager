// ---------------------------------------------------------------------------
// accessible-job-manager - demo deployment to Azure Container Apps
//
// Deployment:
//   az group create -n ajm-demo -l germanywestcentral
//   az deployment group create -g ajm-demo -f main.bicep \
//       --parameters pgPassword='<STRONG_PW>' \
//                    oidcClientId='<CLIENT_ID>' \
//                    oidcClientSecret='<CLIENT_SECRET>' \
//                    oidcIssuerUri='https://.../application/o/<slug>/' \
//                    oidcAuthUri='https://.../application/o/authorize/' \
//                    oidcRedirectUri='https://<expected-app-fqdn>/login/oauth2/code/authentik' \
//                    azureOpenAiEndpoint='https://ajm-openai.openai.azure.com/' \
//                    azureOpenAiApiKey='<AZURE_OPENAI_API_KEY>'
//
// azureOpenAiEndpoint/azureOpenAiApiKey wire up the job-posting LLM extractor
// (jobPostingLlmProvider defaults to "azure" since no Ollama container is
// deployed here). See dev/azure/deploy-gpt-4.1-mini.sh for provisioning the
// deployment itself and check-openai-capacity.sh for picking a region/model
// with available quota.
//
// oidcRedirectUri depends on the app's FQDN, which is only known after the
// first deployment (see output oidcRedirectUri). Workflow: deploy once with a
// placeholder, register the real value from the output as the redirect URI in
// Authentik, then deploy again with --parameters oidcRedirectUri='<real value>'.
//
// Postgres runs as Azure Database for PostgreSQL Flexible Server (managed).
// Provisioning the server takes about 10-15 minutes on the first deployment.
//
// groupUser/groupAdvisor/groupReviewer must match whatever the OIDC provider
// puts in the "groups" claim. For Entra ID with cloud-only security groups,
// that is the group's Object ID (a GUID), not its display name - pass the
// GUIDs explicitly, e.g. --parameters groupUser='<object-id>'.
// ---------------------------------------------------------------------------

@description('Region for all resources')
param location string = resourceGroup().location

@description('Region for the PostgreSQL Flexible Server. Kept separate from "location" because some subscriptions (e.g. trial) block PostgreSQL Flexible Server in certain regions (error "LocationIsOfferRestricted") - the server is still publicly reachable, just with slightly higher latency.')
param pgLocation string = 'westeurope'

@description('Prefix for resource names')
param prefix string = 'ajm'

@description('Postgres password')
@secure()
param pgPassword string

@description('OIDC Client ID')
param oidcClientId string

@description('OIDC Client Secret')
@secure()
param oidcClientSecret string

@description('OIDC Redirect URI (login/oauth2/code/authentik under the app FQDN)')
param oidcRedirectUri string

@description('OIDC Issuer URI (Authentik or Entra ID)')
param oidcIssuerUri string

@description('OIDC Authorization URI (authorize endpoint)')
param oidcAuthUri string

@description('Entra ID / Authentik group (name or object ID) required for the User role')
param groupUser string = 'User'

@description('Entra ID / Authentik group (name or object ID) required for the Advisor role')
param groupAdvisor string = 'Advisor'

@description('Entra ID / Authentik group (name or object ID) required for the Reviewer role')
param groupReviewer string = 'Reviewer'

@description('Container image of the application')
param appImage string = 'ghcr.io/martin1088/accessible-job-manager:latest'

@description('Minimum replicas of the app. 0 = scale-to-zero (cheaper, but cold start)')
param appMinReplicas int = 0

@description('Job posting LLM extractor provider. No Ollama container is deployed here, so this defaults to "azure"')
param jobPostingLlmProvider string = 'azure'

@description('Azure OpenAI resource endpoint, e.g. https://ajm-openai.openai.azure.com/. Leave empty if jobPostingLlmProvider is not "azure"')
param azureOpenAiEndpoint string = ''

@description('Azure OpenAI deployment name')
param azureOpenAiDeployment string = 'gpt-4.1-mini'

@description('Azure OpenAI API version')
param azureOpenAiApiVersion string = '2024-08-01-preview'

@description('Azure OpenAI API key')
@secure()
param azureOpenAiApiKey string = ''

// ---------------------------------------------------------------------------
// Storage: account + blob container (documents)
// ---------------------------------------------------------------------------

var storageName = '${prefix}${uniqueString(resourceGroup().id)}'

resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: storageName
  location: location
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    minimumTlsVersion: 'TLS1_2'
    allowBlobPublicAccess: false
    supportsHttpsTrafficOnly: true
  }
}

resource blobService 'Microsoft.Storage/storageAccounts/blobServices@2023-05-01' = {
  parent: storage
  name: 'default'
}

resource documentsContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-05-01' = {
  parent: blobService
  name: 'documents'
  properties: {
    publicAccess: 'None'
  }
}

// ---------------------------------------------------------------------------
// Log Analytics (required by the Container Apps environment)
// ---------------------------------------------------------------------------

resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: '${prefix}-logs'
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
  }
}

// ---------------------------------------------------------------------------
// Container Apps Environment
// ---------------------------------------------------------------------------

resource env 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${prefix}-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Postgres (managed: Azure Database for PostgreSQL Flexible Server)
//
// Public access + "AllowAzureServices" firewall rule: Container Apps on the
// Consumption plan have no fixed outbound IP, so no VNet setup is needed.
// For production data, private networking would be preferable.
// ---------------------------------------------------------------------------

resource pgFlex 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: '${prefix}-pg-${uniqueString(resourceGroup().id, pgLocation)}'
  location: pgLocation
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    version: '16'
    administratorLogin: 'manager'
    administratorLoginPassword: pgPassword
    storage: {
      storageSizeGB: 32
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
    network: {
      publicNetworkAccess: 'Enabled'
    }
  }
}

resource pgFlexFirewall 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: pgFlex
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource pgFlexDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: pgFlex
  name: 'manager'
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

// ---------------------------------------------------------------------------
// Gotenberg (PDF conversion, internal)
// ---------------------------------------------------------------------------

resource gotenberg 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${prefix}-gotenberg'
  location: location
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      ingress: {
        external: false
        targetPort: 3000
        transport: 'http'
      }
    }
    template: {
      containers: [
        {
          name: 'gotenberg'
          image: 'gotenberg/gotenberg:8'
          resources: {
            cpu: json('0.5')
            memory: '1.0Gi'
          }
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 1
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Application (Spring Boot + Angular in the JAR)
// ---------------------------------------------------------------------------

resource app 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${prefix}-app'
  location: location
  dependsOn: [
    pgFlexDb
  ]
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      ingress: {
        external: true
        targetPort: 8060
        transport: 'http'
        allowInsecure: false
      }
      secrets: [
        {
          name: 'pg-password'
          value: pgPassword
        }
        {
          name: 'storage-connection'
          value: 'DefaultEndpointsProtocol=https;AccountName=${storage.name};AccountKey=${storage.listKeys().keys[0].value};EndpointSuffix=${environment().suffixes.storage}'
        }
        {
          name: 'oidc-client-secret'
          value: oidcClientSecret
        }
        {
          name: 'azure-openai-api-key'
          value: azureOpenAiApiKey
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'app'
          image: appImage
          resources: {
            cpu: json('1.0')
            memory: '2.0Gi'
          }
          env: [
            {
              name: 'MANAGER_DB_URL'
              value: 'jdbc:postgresql://${pgFlex.properties.fullyQualifiedDomainName}:5432/manager?sslmode=require'
            }
            { name: 'MANAGER_DB_USER', value: 'manager' }
            { name: 'MANAGER_DB_PASSWORD', secretRef: 'pg-password' }
            { name: 'OIDC_CLIENT_ID', value: oidcClientId }
            { name: 'OIDC_CLIENT_SECRET', secretRef: 'oidc-client-secret' }
            { name: 'OIDC_REDIRECT_URI', value: oidcRedirectUri }
            { name: 'OIDC_ISSUER_URI', value: oidcIssuerUri }
            { name: 'OIDC_AUTH_URI', value: oidcAuthUri }
            { name: 'GOTENBERG_URL', value: 'http://${gotenberg.name}' }
            { name: 'STORAGE_PROVIDER', value: 'azure' }
            { name: 'AZURE_STORAGE_CONNECTION_STRING', secretRef: 'storage-connection' }
            { name: 'AZURE_STORAGE_CONTAINER', value: documentsContainer.name }
            { name: 'GROUP_USER', value: groupUser }
            { name: 'GROUP_ADVISOR', value: groupAdvisor }
            { name: 'GROUP_REVIEWER', value: groupReviewer }
            { name: 'JOB_POSTING_LLM_PROVIDER', value: jobPostingLlmProvider }
            { name: 'AZURE_OPENAI_ENDPOINT', value: azureOpenAiEndpoint }
            { name: 'AZURE_OPENAI_DEPLOYMENT', value: azureOpenAiDeployment }
            { name: 'AZURE_OPENAI_API_VERSION', value: azureOpenAiApiVersion }
            { name: 'AZURE_OPENAI_API_KEY', secretRef: 'azure-openai-api-key' }
          ]
        }
      ]
      scale: {
        minReplicas: appMinReplicas
        maxReplicas: 2
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Outputs
// ---------------------------------------------------------------------------

output appUrl string = 'https://${app.properties.configuration.ingress.fqdn}'
output storageAccountName string = storage.name
output oidcRedirectUri string = 'https://${app.properties.configuration.ingress.fqdn}/login/oauth2/code/authentik'
output pgFlexFqdn string = pgFlex.properties.fullyQualifiedDomainName
