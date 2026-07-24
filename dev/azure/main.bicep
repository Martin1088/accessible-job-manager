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
//
// Postgres laeuft als Azure Database for PostgreSQL Flexible Server (managed).
// Das Erstellen des Servers dauert beim ersten Deployment ca. 10-15 Minuten.
// ---------------------------------------------------------------------------

@description('Region fuer alle Ressourcen')
param location string = resourceGroup().location

@description('Region fuer den PostgreSQL Flexible Server. Getrennt von "location", weil manche Subscriptions (z.B. Trial) PostgreSQL Flexible Server in bestimmten Regionen sperren (Fehler "LocationIsOfferRestricted") - Server bleibt trotzdem oeffentlich erreichbar, nur mit minimal hoeherer Latenz.')
param pgLocation string = 'westeurope'

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
// Storage: Account + Blob Container (Dokumente)
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

// ---------------------------------------------------------------------------
// Postgres (managed: Azure Database for PostgreSQL Flexible Server)
//
// Oeffentlicher Zugriff + "AllowAzureServices"-Firewallregel: Container Apps
// im Consumption-Plan haben keine feste ausgehende IP, daher kein VNet-Setup
// noetig. Fuer produktive Daten waere private Netzwerkanbindung vorzuziehen.
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
