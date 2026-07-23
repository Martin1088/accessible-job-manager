// ---------------------------------------------------------------------------
// accessible-job-manager - Demo-Deployment auf Azure Container Apps
//
// Deployment:
//   az group create -n ajm-demo -l germanywestcentral
//   az deployment group create -g ajm-demo -f main.bicep \
//       --parameters pgPassword='<STRONG_PW>' \
//                    oidcClientId='<CLIENT_ID>' \
//                    oidcClientSecret='<CLIENT_SECRET>' \
//                    oidcIssuerUri='https://.../application/o/<slug>/' \
//                    oidcAuthUri='https://.../application/o/authorize/' \
//                    oidcRedirectUri='https://<expected-app-fqdn>/login/oauth2/code/authentik'
//
// oidcRedirectUri haengt vom FQDN der App ab, der erst nach dem ersten Deployment
// feststeht (siehe Output oidcRedirectUri). Vorgehen: einmal mit einem Platzhalter
// deployen, den echten Wert aus dem Output in Authentik als Redirect-URI eintragen,
// danach erneut mit --parameters oidcRedirectUri='<echter Wert>' deployen.
// ---------------------------------------------------------------------------

@description('Region fuer alle Ressourcen')
param location string = resourceGroup().location

@description('Praefix fuer Ressourcennamen')
param prefix string = 'ajm'

@description('Postgres-Passwort')
@secure()
param pgPassword string

@description('OIDC Client ID')
param oidcClientId string

@description('OIDC Client Secret')
@secure()
param oidcClientSecret string

@description('OIDC Redirect URI (login/oauth2/code/authentik unter der App-FQDN)')
param oidcRedirectUri string

@description('OIDC Issuer URI (Authentik oder Entra ID)')
param oidcIssuerUri string

@description('OIDC Authorization URI (Authorize-Endpoint)')
param oidcAuthUri string

@description('Container-Image der Anwendung')
param appImage string = 'ghcr.io/martin1088/accessible-job-manager:latest'

@description('Minimale Replicas der App. 0 = scale-to-zero (guenstiger, aber Cold Start)')
param appMinReplicas int = 0

// ---------------------------------------------------------------------------
// Storage: Account + File Share (Postgres-Daten) + Blob Container (Dokumente)
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

resource fileService 'Microsoft.Storage/storageAccounts/fileServices@2023-05-01' = {
  parent: storage
  name: 'default'
}

resource pgShare 'Microsoft.Storage/storageAccounts/fileServices/shares@2023-05-01' = {
  parent: fileService
  name: 'pgdata'
  properties: {
    shareQuota: 8
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
// Log Analytics (von Container Apps Environment benoetigt)
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

// File Share im Environment registrieren
resource envStorage 'Microsoft.App/managedEnvironments/storages@2024-03-01' = {
  parent: env
  name: 'pgdata'
  properties: {
    azureFile: {
      accountName: storage.name
      accountKey: storage.listKeys().keys[0].value
      shareName: pgShare.name
      accessMode: 'ReadWrite'
    }
  }
}

// ---------------------------------------------------------------------------
// Postgres (Container, internes TCP-Ingress)
// ---------------------------------------------------------------------------

resource postgres 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${prefix}-postgres'
  location: location
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      ingress: {
        external: false
        targetPort: 5432
        transport: 'tcp'
      }
      secrets: [
        {
          name: 'pg-password'
          value: pgPassword
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'postgres'
          image: 'postgres:16'
          resources: {
            cpu: json('0.5')
            memory: '1.0Gi'
          }
          env: [
            { name: 'POSTGRES_DB', value: 'manager' }
            { name: 'POSTGRES_USER', value: 'manager' }
            { name: 'POSTGRES_PASSWORD', secretRef: 'pg-password' }
            // Unterverzeichnis noetig: Azure Files legt Metadaten im Mount-Root ab
            { name: 'PGDATA', value: '/var/lib/postgresql/data/pgdata' }
          ]
          volumeMounts: [
            {
              volumeName: 'pgdata'
              mountPath: '/var/lib/postgresql/data'
            }
          ]
        }
      ]
      volumes: [
        {
          name: 'pgdata'
          storageName: envStorage.name
          storageType: 'AzureFile'
        }
      ]
      scale: {
        // Zwingend 1/1: zwei Instanzen auf demselben Volume korrumpieren die DB
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Gotenberg (PDF-Konvertierung, intern)
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
// Anwendung (Spring Boot + Angular im JAR)
// ---------------------------------------------------------------------------

resource app 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${prefix}-app'
  location: location
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
              value: 'jdbc:postgresql://${postgres.name}:5432/manager'
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
