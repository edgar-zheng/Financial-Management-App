# Spring Boot on Elastic Beanstalk

This guide prepares the Java backend only. It does not create AWS resources, change
Aiven credentials or schemas, or deploy React. Run commands from the repository root
unless another directory is specified. All example values are placeholders.

## Inspected application

- Maven wrapper at the repository root; POM at `backend/pom.xml`.
- Spring Boot 4.1.1, Java 21, executable JAR via the existing Spring Boot Maven plugin.
- MySQL Connector/J 9.7.0 is resolved by Spring Boot dependency management; Flyway owns V1–V4 and
  Hibernate validates the schema. Do not enable `ddl-auto=update`, Flyway clean,
  or automatic baselining to work around errors.
- Authentication uses server-side sessions, BCrypt passwords, and CSRF. There is
  no JWT secret. Sessions are held in one JVM and are lost on restart.
- `MARKET_API_KEY` supplies the Spring property `market.api.key` for Massive.
  It is optional at startup but required for price-dependent features.
- Local port remains 8080 unless `PORT`/`SERVER_PORT` is supplied. The packaged
  Procfile selects `prod`, disables local configuration imports, and listens on
  `${PORT:-5000}` to match the Beanstalk nginx proxy.
- Existing Docker/Compose support remains available; this deployment uses the
  Java SE platform directly and uploads no Docker files.

## Environment properties

Set these in the environment's **Configuration → Updates, monitoring, and logging
→ Runtime environment variables** (console wording may vary).

| Name | Value / purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://<AIVEN_HOST>:<AIVEN_PORT>/<DATABASE_NAME>?sslMode=REQUIRED` |
| `SPRING_DATASOURCE_USERNAME` | Existing Aiven database username |
| `SPRING_DATASOURCE_PASSWORD` | Existing Aiven password; prefer a secret reference |
| `MARKET_API_KEY` | Massive API key; maps to `market.api.key` |
| `FRONTEND_URL` | One exact allowed origin, initially `http://localhost:5173`; no path, trailing slash, or wildcard |
| `PORT` | `5000`; must match the proxy's upstream port |
| `SESSION_COOKIE_SECURE` | `true` for deployment over HTTPS (default) |
| `SESSION_COOKIE_SAME_SITE` | `lax` (default) for same-origin proxying; see frontend discussion below |
| `SPRING_PROFILES_ACTIVE` | `prod`; also explicitly selected by Procfile |

Do not put secrets in the JAR, source bundle, shell command arguments, or Git.
The root `.env.example` contains only placeholders. Do not overwrite an existing
`.env` or source it as a shell script: it is read locally as a Java properties file.
Do not set duplicate properties under different names.

For Secrets Manager, create a secret containing each required value, then choose
**Secrets Manager** as the environment variable source and supply its ARN. An ARN
entered as an ordinary plain-text password is not resolved. The EC2 instance
profile needs `secretsmanager:GetSecretValue` on those specific ARNs and
`kms:Decrypt` when using a customer-managed KMS key. SSM SecureString references
are an alternative (`ssm:GetParameter` and applicable KMS permission). No AWS SDK
or code change is needed. Use a current platform version that supports secret
references; refresh/redeploy the environment after rotating a secret.

## Aiven and TLS

Copy the actual host, port, and **existing** database name from Aiven's connection
information. A host alone or a `mysql://` URI is not a JDBC URL. Keep the credentials
in separate properties. Query parameters follow `?`, then `&` for additional ones.
Use the Connector/J spelling `sslMode=REQUIRED`, not `sslmode=require`.

`REQUIRED` encrypts transport but does not authenticate the server certificate.
Prefer `sslMode=VERIFY_IDENTITY` when you have installed the Aiven project CA in a
Java truststore. Obtain the CA from Aiven, create a truststore with `keytool`
(enter its password at the prompt), and provision it at a known deployment
location; never reference a developer laptop path:

```sh
keytool -importcert -alias aiven-ca -file /path/to/aiven-ca.pem \
  -keystore /secure/path/aiven-truststore.p12 -storetype PKCS12
```

After securely provisioning the file, use:

```text
jdbc:mysql://<AIVEN_HOST>:<AIVEN_PORT>/<DATABASE_NAME>?sslMode=VERIFY_IDENTITY&trustCertificateKeyStoreUrl=file:/var/app/current/aiven-truststore.p12&trustCertificateKeyStoreType=PKCS12
```

Supply the truststore password separately through a secret-backed
`SPRING_APPLICATION_JSON` value (preserves the driver's camel-case property name):

```json
{"spring":{"datasource":{"hikari":{"data-source-properties":{"trustCertificateKeyStorePassword":"<TRUSTSTORE_PASSWORD>"}}}}}
```
The optional truststore must be deliberately added to the bundle/provisioned on
instances; the default two-file bundle does not contain one. Do not claim hostname
verification with the default REQUIRED example. No existing URL or credentials
are changed by this preparation.

Keep the existing Flyway migrations. The current Aiven schema must already match
V4 for this release. At normal startup Flyway validates and applies any pending
migration; review future migrations before deploying. If an existing schema has
no Flyway history, stop and reconcile it instead of enabling automatic baselining.

## Build and bundle

Java 21 is required locally. This database-independent build runs all existing
unit/controller tests plus the new deployment security tests:

```sh
./mvnw -f backend/pom.xml clean package -Dtest=DeploymentSecurityTests,MarketDataClientTests,MarketDataServiceTests,PortfolioAnalyticsServiceTests,AllocationDriftServiceTests,PortfolioAccessServiceTests,RegistrationServiceTests,TransactionServiceTests,AuthControllerTests,PortfolioAnalyticsControllerTests,PortfolioControllerTests,MarketDataEndpointTests
```

Run the full integration suite only with credentials pointing at a **disposable
test MySQL database**, never the shared Aiven database:

```sh
./mvnw -f backend/pom.xml clean verify
```

The JAR is `backend/target/portfolio-0.0.1-SNAPSHOT.jar`. Local
`application-env.properties` is excluded from the JAR even when present on disk;
local `spring-boot:run` still supports it. Build a ZIP with exactly the executable
JAR and Procfile at its root:

```sh
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
root = Path('backend')
with ZipFile(root / 'target/elastic-beanstalk.zip', 'w', ZIP_DEFLATED) as bundle:
    bundle.write(root / 'target/portfolio-0.0.1-SNAPSHOT.jar', 'app.jar')
    bundle.write(root / 'Procfile', 'Procfile')
print(root / 'target/elastic-beanstalk.zip')
PY
unzip -l backend/target/elastic-beanstalk.zip
```

Do not upload the repository ZIP or the `.jar.original` file. The bundle needs no
Maven installation or local configuration files at runtime. The Procfile is an
explicit `java -jar` launch command, not an executable shell script.

For local development against Aiven with the current `.env`:

```sh
./mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=prod -Dspring-boot.run.arguments=--server.servlet.session.cookie.secure=false
```

Omit `PORT` locally for the existing port 8080, or explicitly pass
`--server.port=8080`. The cookie override is for localhost HTTP only. With real
exported environment variables, the same JAR can run from any directory:

```sh
java -jar backend/target/portfolio-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod --spring.config.import=
```

## Create the AWS environment (manual; not executed)

1. Choose a region near the Aiven region. In Elastic Beanstalk create application
   `financial-management-backend`, then a **Web server environment** such as
   `financial-management-backend-prod`.
2. Select **Java → Corretto 21 on 64-bit Amazon Linux 2023**, current supported
   platform release. Do not choose Tomcat, Java 17, or Docker. Upload
   `backend/target/elastic-beanstalk.zip` as the application code.
3. Select/create the Beanstalk service role and EC2 instance profile. The service
   role handles environment health/management; the instance profile handles
   application artifact/log access and any secret retrieval. Use the console's
   standard web-tier roles plus narrowly scoped secret permissions. Do not put
   IAM access keys in application environment properties. The deploying identity
   needs permission to create the environment and pass these roles.
4. For authenticated public use, choose a load-balanced environment with an
   **Application Load Balancer** and initially one application instance
   (minimum=maximum=1). An ALB needs subnets in two availability zones. Sessions
   are not shared: adding replicas requires a session strategy; stickiness alone
   does not survive restarts. A single-instance environment is cheaper but lacks
   ALB TLS termination; it needs separate HTTPS setup before real logins.
5. Networking: allow ALB ingress on HTTPS 443 (and HTTP 80 only for redirects).
   Allow the instance's nginx port 80 from the ALB security group. Do not expose
   application port 5000 or a MySQL inbound port publicly. Private instance
   subnets need NAT egress; public subnets need an internet-gateway route and
   public instance addresses. Enable VPC DNS. Permit outbound TCP to Aiven's
   **actual service port**, outbound HTTPS for Massive and AWS services, and
   required DNS/return traffic through security groups/NACLs. If Aiven has an IP
   allowlist, allow the AWS egress IP(s); NAT with an Elastic IP provides stable
   egress. The Aiven database does not need to move into AWS or use RDS.
6. Set the environment properties above before launching. Leave RDS creation
   disabled. Confirm Aiven is running and the selected database exists.
7. Configure the ALB default process to use **HTTP port 80**, health-check path
   **`/actuator/health`**, success code **200**. nginx forwards to Java on 5000.
   The endpoint returns only aggregate status, checks database availability, and
   requires no login or CSRF token. Set a startup health grace period suitable
   for JVM initialization and Aiven latency (start with 120 seconds). Database
   downtime makes this health check unhealthy; it is not a process-only probe.
8. Add an ACM certificate in the same region for a domain you control, an ALB
   HTTPS listener on 443, and DNS pointing the domain to the environment/ALB.
   Redirect public HTTP to HTTPS without redirecting the internal health probe.
   An automatically assigned `elasticbeanstalk.com` hostname is not by itself an
   HTTPS certificate for your application.
9. Enable enhanced health and CloudWatch log streaming/retention. Launch the
   environment. This creates billable EC2/ALB and possibly NAT resources.

No forwarded-header trust is enabled globally. The app currently emits no
absolute redirect URLs, and Secure cookies are explicit. If you later need
forwarded scheme/client-IP handling, configure Tomcat's trusted proxies for the
actual deployment topology; do not blindly trust arbitrary client-supplied
`X-Forwarded-*` headers.

## Verify and troubleshoot

Find the environment URL on the Beanstalk environment overview. Use the custom
HTTPS hostname for authenticated requests. After deployment:

```sh
BACKEND_URL=https://<YOUR_BACKEND_DOMAIN>
curl --fail --show-error "$BACKEND_URL/actuator/health"
curl --fail --show-error "$BACKEND_URL/api/auth/csrf"
curl -i "$BACKEND_URL/api/auth/me"
curl -i -X OPTIONS "$BACKEND_URL/api/portfolios" \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type,x-csrf-token'
```

Expect health `{"status":"UP"}`, a CSRF token, 401 for anonymous `/me`, and an
allowed-origin/credentials response for the preflight. Do not publish cookies or
CSRF tokens. These checks do not prove the entire portfolio workflow.

Use **Logs → Request logs → Last 100 lines / Full logs**, environment Events and
Health, or CloudWatch. Java stdout goes to `/var/log/web.stdout.log`; deployment
failures appear in `/var/log/eb-engine.log`; proxy errors in nginx logs.

- `UnknownHostException`: wrong/stopped Aiven host or missing DNS/egress.
- Timeout: route/NAT, egress rules, Aiven allowlist or service availability.
- `Unknown database`: incorrect database path; do not guess or recreate data.
- `Access denied`: inspect secret mapping, username and database grants.
- TLS/certificate errors: correct `sslMode`, CA truststore and hostname.
- HTTP 502: JVM not started, wrong port, or bad JAR/Procfile. Verify PORT=5000.
- Unhealthy/401 at `/`: set health path to `/actuator/health` instead.
- Login loops: HTTPS, cookies, CSRF refresh, SameSite, and origin configuration.

After an approved environment exists, the optional AWS CLI update flow is below.
Supply an existing deployment bucket in the same region and a unique version
label. These are deployment commands for you to run, not commands run by this task:

```sh
AWS_REGION=<REGION>
EB_BUCKET=<EXISTING_DEPLOYMENT_BUCKET>
EB_VERSION=<UNIQUE_VERSION_LABEL>
aws s3 cp backend/target/elastic-beanstalk.zip "s3://$EB_BUCKET/$EB_VERSION.zip" --region "$AWS_REGION"
aws elasticbeanstalk create-application-version --application-name financial-management-backend \
  --version-label "$EB_VERSION" --source-bundle "S3Bucket=$EB_BUCKET,S3Key=$EB_VERSION.zip" --region "$AWS_REGION"
aws elasticbeanstalk update-environment --environment-name financial-management-backend-prod \
  --version-label "$EB_VERSION" --region "$AWS_REGION"
```

## Local React to the deployed API, without editing React source

The checked-in Vite configuration still proxies `/api` to localhost:8080, and
`apiFetch` uses `credentials: 'same-origin'`. CORS alone cannot retarget that code.
Use a same-origin development proxy to the HTTPS backend. For Secure cookies,
serve local Vite over HTTPS using a locally trusted certificate for localhost
(e.g. provisioned by your local certificate tooling). This one-off launch merges
an override into the existing Vite configuration without editing any frontend file:

```sh
cd frontend
BACKEND_URL=https://<YOUR_BACKEND_DOMAIN> \
LOCAL_TLS_KEY=/absolute/path/to/localhost-key.pem \
LOCAL_TLS_CERT=/absolute/path/to/localhost.pem \
node --input-type=module <<'JS'
import { createServer } from 'vite';
import { readFileSync } from 'node:fs';
const server = await createServer({ server: {
  https: { key: readFileSync(process.env.LOCAL_TLS_KEY), cert: readFileSync(process.env.LOCAL_TLS_CERT) },
  proxy: { '/api': { target: process.env.BACKEND_URL, changeOrigin: true, secure: true } }
} });
await server.listen();
server.printUrls();
JS
```

Set `FRONTEND_URL=https://localhost:5173` for this HTTPS test origin. Open
https://localhost:5173, register/login, create a portfolio, add a transaction,
retrieve holdings, and save targets. The proxy preserves the path, session cookie,
and CSRF headers; it validates upstream TLS. Keep `SESSION_COOKIE_SAME_SITE=lax`.

A future browser-to-backend cross-site integration instead requires frontend
`credentials: 'include'`, an API base URL, exact CORS origin, and usually
`SESSION_COOKIE_SAME_SITE=none` plus Secure cookies; third-party-cookie blocking
can still prevent it. Those frontend changes and Vercel deployment are out of
scope. A same-origin proxy avoids that dependency.

## Verification performed locally (2026-11-06)

- Java 21 build succeeded; 44 selected tests passed, zero failures/errors.
  This includes four new deployment security tests. The database-writing full
  integration suite was not rerun against the shared Aiven database.
- Executable JAR started from an isolated temporary directory with datasource
  values supplied through environment variables and local imports disabled.
- Aiven connection succeeded with `sslMode=REQUIRED` for this test process;
  Hibernate schema validation passed. The existing `.env` was not edited and
  certificate/hostname verification was not tested.
- Flyway was disabled only for this live verification, so no migrations or
  schema changes could run. The deployment still uses the existing Flyway defaults.
- Live `/actuator/health` returned HTTP 200 and only `{"status":"UP"}`;
  allowed/disallowed CORS preflights and Secure/HttpOnly cookie flags passed.
  Protected endpoints still required authentication. The temporary process was stopped.
- JAR manifest and ZIP contents verified. No `application-env.properties` in the
  JAR; known local secret values were absent from changed files and packaged contents.
- No AWS commands/resources were changed and no AWS deployment was performed.
  AWS IAM, networking, Aiven allowlists from AWS, ALB health checks, HTTPS, and
  the local HTTPS frontend proxy remain to be verified in the deployed environment.

## References

- [AWS Java SE platform](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/java-se-platform.html)
- [AWS Procfile](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/java-se-procfile.html)
- [AWS nginx and PORT](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/java-se-nginx.html)
- [AWS secrets as environment variables](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/AWSHowTo.secrets.env-vars.html)
- [AWS ALB health checks and HTTPS](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/environments-cfg-alb.html)
- [Connector/J TLS](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-using-ssl.html)
- [Connector/J truststores](https://dev.mysql.com/doc/connector-j/en/connector-j-server-authentication.html)
- [Spring Boot Actuator exposure](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
