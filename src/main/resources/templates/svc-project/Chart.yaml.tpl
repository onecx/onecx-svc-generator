apiVersion: v2
name: {{name}}
description: OneCX backend service Helm chart
type: application
version: 0.0.0
appVersion: "0.0.0"
dependencies:
  - name: helm-quarkus-app
    alias: app
    version: ^0
    repository: oci://ghcr.io/onecx/charts