# --- quarkus ---
quarkus.banner.enabled=false
quarkus.hibernate-orm.database.generation=validate
quarkus.hibernate-orm.metrics.enabled=true
quarkus.hibernate-orm.jdbc.timezone=UTC

# --- datasource ---
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.max-size=30
quarkus.datasource.jdbc.min-size=10
quarkus.datasource.metrics.enabled=true

# --- dev defaults ---
%dev.quarkus.datasource.jdbc.url=${DB_URL:jdbc:postgresql://postgresdb:5432/{{dbName}}?sslmode=disable}
%dev.quarkus.datasource.devservices.port=5432
%dev.quarkus.datasource.username=onecx-dev
%dev.quarkus.datasource.password=onecx-dev

# --- production defaults ---
%prod.quarkus.oidc-client.client-id=${quarkus.application.name}
%prod.quarkus.datasource.jdbc.url=${DB_URL:jdbc:postgresql://postgresdb:5432/{{dbName}}?sslmode=disable}
%prod.quarkus.datasource.username=${DB_USER:{{name}}}
%prod.quarkus.datasource.password=${DB_PWD:{{name}}}

# --- liquibase ---
quarkus.liquibase.migrate-at-start=true
quarkus.liquibase.validate-on-migrate=true

# --- auth ---
quarkus.http.auth.permission.health.paths=/q/*
quarkus.http.auth.permission.health.policy=permit
quarkus.http.auth.permission.default.paths=/*
quarkus.http.auth.permission.default.policy=authenticated

# --- multitenancy / rest context ---
quarkus.hibernate-orm.multitenant=DISCRIMINATOR
tkit.rs.context.tenant-id.enabled=true
tkit.rs.context.token.header-param=apm-principal-token

# --- auth disabled for dev ---
%dev.quarkus.http.auth.permission.default.policy=permit
%dev.quarkus.http.auth.proactive=false
%dev.onecx.permissions.allow-all=true
%dev.quarkus.otel.sdk.disabled=true
%dev.quarkus.oidc-client.discovery-enabled=false
%dev.tkit.security.auth.enabled=false
%dev.tkit.rs.context.tenant-id.mock.enabled=false
%dev.tkit.rs.context.tenant-id.enabled=false

# --- test settings ---
%test.tkit.rs.context.token.header-param=apm-principal-token
%test.tkit.rs.context.tenant-id.mock.claim-org-id=orgId
%test.quarkus.otel.sdk.disabled=true
%test.onecx.tenant.service.client.url=http://localhost:8089