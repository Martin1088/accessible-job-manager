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
//                    azureOpenAiApiKey='<AZURE_OPENAI_API_KEY>' \
//                    adzunaAppId='<ADZUNA_APP_ID>' \
//                    adzunaAppKey='<ADZUNA_APP_KEY>'
//
// adzunaAppId/adzunaAppKey turn on the advisor job search (Adzuna). Leave both
// unset to keep the feature switched off - see dev/.env.example.
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
// LEGAL DOCUMENTS
//
// This deployment publishes its OWN privacy policy and legal notice, from
// deploy/azure/legal/<slug>.<lang>.yaml. It has to: the documents bundled in the
// image name Garage S3 and Authentik, which this template does not deploy, and they
// say nothing about Azure OpenAI receiving the text of imported job postings.
//
//
// To change the text: edit the YAML and redeploy. loadTextContent inlines the files
// at compile time, so a missing file fails `az bicep build` rather than the
// deployment, and a malformed one fails the container's startup rather than being
// served - the application refuses to start on a directory that does not cover
// every slug, so it can never fall back to the upstream author's imprint.
//
// Editing a secret in the portal instead does NOT reach a running app: Container
// Apps does not restart revisions when a secret changes. Redeploying does, because
// it creates a new revision. If editing legal text without a redeploy ever becomes
// a requirement, switch the volume to an Azure Files share - the application re-reads
// the directory every 30 seconds and would then pick it up live.
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

@description('Adzuna application id for the advisor job search. Leave empty to keep the job search feature switched off')
param adzunaAppId string = ''

@description('Adzuna application key. Register your own at https://developer.adzuna.com - the API terms bind the organisation using the key')
@secure()
param adzunaAppKey string = ''

@description('Country board the advisor job search defaults to')
param adzunaCountry string = 'de'

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
          // Chromium fetches user-supplied posting URLs itself and follows its
          // own redirects, so the refusal has to live in the process that
          // connects. Consumption-plan Container Apps have no fixed outbound IP
          // and this template provisions no egress rules, which makes this the
          // only address-level control the Azure deployment actually has - see
          // "Gotenberg network isolation" in Readme.md.
          args: [
            'gotenberg'
            '--chromium-deny-private-ips'
            '--api-timeout=30s'
          ]
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
        {
          name: 'adzuna-app-key'
          value: adzunaAppKey
        }
        // This deployment's own legal documents, mounted as files below. They are
        // published on a public page, so holding them as secrets costs no
        // confidentiality - it only puts them in the app's secret list and in the
        // ARM deployment history. loadTextContent inlines them at compile time, so
        // deleting one of the twelve files fails `az bicep build` rather than the
        // deployment.
        //
        // Each carries #disable-next-line use-secure-value-for-secure-inputs: the ACA
        // schema types every secret value as secure, which is right for the five
        // above and wrong for these. Suppressed per line rather than repo-wide, so a
        // genuinely leaked secret still warns.
        {
          name: 'legal-datenschutz-de'
          #disable-next-line use-secure-value-for-secure-inputs
          value: loadTextContent('legal/datenschutz.de.yaml')
        }
        {
          name: 'legal-datenschutz-en'
          #disable-next-line use-secure-value-for-secure-inputs
          value: loadTextContent('legal/datenschutz.en.yaml')
        }
        {
          name: 'legal-datenschutz-nl'
          #disable-next-line use-secure-value-for-secure-inputs
          value: loadTextContent('legal/datenschutz.nl.yaml')
        }
        {
          name: 'legal-datenschutz-es'
          #disable-next-line use-secure-value-for-secure-inputs
          value: loadTextContent('legal/datenschutz.es.yaml')
        }
        {
          name: 'legal-impressum-de'
          #disable-next-line use-secure-value-for-secure-inputs
          value: loadTextContent('legal/impressum.de.yaml')
        }
        {
          name: 'legal-impressum-en'
          #disable-next-line use-secure-value-for-secure-inputs
          value: loadTextContent('legal/impressum.en.yaml')
        }
        {
          name: 'legal-impressum-nl'
          #disable-next-line use-secure-value-for-secure-inputs
          value: loadTextContent('legal/impressum.nl.yaml')
        }
        {
          name: 'legal-impressum-es'
          #disable-next-line use-secure-value-for-secure-inputs
          value: loadTextContent('legal/impressum.es.yaml')
        }
        {
          name: 'legal-demo-hinweis-de'
          #disable-next-line use-secure-value-for-secure-inputs
          value: loadTextContent('legal/demo-hinweis.de.yaml')
        }
        {
          name: 'legal-demo-hinweis-en'
          #disable-next-line use-secure-value-for-secure-inputs
          value: loadTextContent('legal/demo-hinweis.en.yaml')
        }
        {
          name: 'legal-demo-hinweis-nl'
          #disable-next-line use-secure-value-for-secure-inputs
          value: loadTextContent('legal/demo-hinweis.nl.yaml')
        }
        {
          name: 'legal-demo-hinweis-es'
          #disable-next-line use-secure-value-for-secure-inputs
          value: loadTextContent('legal/demo-hinweis.es.yaml')
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
            { name: 'COOKIE_SECURE', value: 'true' }
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
            { name: 'JOBSOURCE_ADZUNA_APP_ID', value: adzunaAppId }
            { name: 'JOBSOURCE_ADZUNA_APP_KEY', secretRef: 'adzuna-app-key' }
            { name: 'JOBSOURCE_ADZUNA_COUNTRY', value: adzunaCountry }
            { name: 'LEGAL_DOCUMENTS_DIR', value: '/etc/ajm/legal' }
          ]
          volumeMounts: [
            {
              volumeName: 'legal'
              mountPath: '/etc/ajm/legal'
            }
          ]
        }
      ]
      volumes: [
        {
          name: 'legal'
          storageType: 'Secret'
          // Every legal secret is listed explicitly, each with the file name the
          // application's loader requires (<slug>.<lang>.yaml). Mounting "all
          // secrets" instead - which is what you get by omitting this array - would
          // also write pg-password, oidc-client-secret, storage-connection,
          // azure-openai-api-key and adzuna-app-key into this directory as files.
          // LegalDocumentRegistry ignores any file that is not named <slug>.<lang>.yaml,
          // so nothing would break; but the database password does not belong on disk
          // beside the privacy policy. Keep this list explicit.
          secrets: [
            { secretRef: 'legal-datenschutz-de', path: 'datenschutz.de.yaml' }
            { secretRef: 'legal-datenschutz-en', path: 'datenschutz.en.yaml' }
            { secretRef: 'legal-datenschutz-nl', path: 'datenschutz.nl.yaml' }
            { secretRef: 'legal-datenschutz-es', path: 'datenschutz.es.yaml' }
            { secretRef: 'legal-impressum-de', path: 'impressum.de.yaml' }
            { secretRef: 'legal-impressum-en', path: 'impressum.en.yaml' }
            { secretRef: 'legal-impressum-nl', path: 'impressum.nl.yaml' }
            { secretRef: 'legal-impressum-es', path: 'impressum.es.yaml' }
            { secretRef: 'legal-demo-hinweis-de', path: 'demo-hinweis.de.yaml' }
            { secretRef: 'legal-demo-hinweis-en', path: 'demo-hinweis.en.yaml' }
            { secretRef: 'legal-demo-hinweis-nl', path: 'demo-hinweis.nl.yaml' }
            { secretRef: 'legal-demo-hinweis-es', path: 'demo-hinweis.es.yaml' }
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
