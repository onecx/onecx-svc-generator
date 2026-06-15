app:
  name: svc
  image:
    repository: "onecx/{{name}}"
  db:
    enabled: true
  operator:
    keycloak:
      client:
        enabled: true
        spec:
          kcConfig:
            defaultClientScopes: [ {{scopePrefix}}:read ]
    microservice:
      spec:
        description: "OneCX backend service"
        name: "{{name}}"