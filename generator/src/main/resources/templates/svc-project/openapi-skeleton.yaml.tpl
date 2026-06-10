openapi: 3.0.3
info:
  title: {{name}} API
  version: 1.0.0

servers:
  - url: http://{{name}}:8080/api

paths: {}

components:
  securitySchemes:
    oauth2:
      type: oauth2
      flows:
        clientCredentials:
          tokenUrl: https://oauth.simple.api/token
          scopes:
            "{{scopePrefix}}:all": Grants access to all operations
            "{{scopePrefix}}:read": Grants read access
            "{{scopePrefix}}:write": Grants write access
            "{{scopePrefix}}:delete": Grants access to delete operations

  schemas:
    ProblemDetailParam:
      type: object
      properties:
        key:
          type: string
        value:
          type: string

    ProblemDetailInvalidParam:
      type: object
      properties:
        name:
          type: string
        message:
          type: string

    ProblemDetailResponse:
      type: object
      properties:
        errorCode:
          type: string
        detail:
          type: string
        params:
          type: array
          items:
            $ref: '#/components/schemas/ProblemDetailParam'
        invalidParams:
          type: array
          items:
            $ref: '#/components/schemas/ProblemDetailInvalidParam'