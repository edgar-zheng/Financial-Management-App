# Portfolio backend deployment: Beanstalk and CloudFront

## Architecture

Browser (local Vite, later Vercel) → HTTPS → default `*.cloudfront.net` domain
→ **HTTP** → Beanstalk ALB/nginx → Spring Boot port 5000
→ TLS with `VERIFY_IDENTITY` → Aiven MySQL.

CloudFront provides browser-facing TLS. This is **not end-to-end HTTPS**: traffic
between CloudFront and EB is HTTP. The EB hostname remains directly reachable;
this demo does not implement origin-access restriction. Do not describe the
CloudFront-to-origin link as encrypted. No custom domain, alias, ACM certificate,
or Route 53 hosted zone is required or created.

The backend is Maven / Spring Boot 4.1.1 / Java 21 on the Java SE Corretto 21
Amazon Linux 2023 platform. Existing Docker files remain for local development;
Beanstalk runs the executable JAR directly. No custom nginx replacement is used.

## Configuration

Supply these through Beanstalk environment properties, never Git or the ZIP:

| Variable | Value / purpose |
| --- | --- |
| `PORT` | `5000` |
| `SPRING_PROFILES_ACTIVE` | `prod` (also selected by Procfile) |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://<HOST>:<PORT>/<DATABASE>?sslMode=VERIFY_IDENTITY` |
| `SPRING_DATASOURCE_USERNAME` | Existing Aiven user |
| `SPRING_DATASOURCE_PASSWORD` | Existing Aiven password |
| `AIVEN_TRUSTSTORE_PATH` | `/var/app/current/certs/aiven-truststore.jks` |
| `AIVEN_TRUSTSTORE_PASSWORD` | Existing truststore password |
| `MARKET_API_KEY` | Existing `market.api.key` value; needed for valuation/analytics |
| `FRONTEND_URL` | Initially `http://localhost:5173`; one exact origin without trailing slash |
| `SESSION_COOKIE_SECURE` | `true` |
| `SESSION_COOKIE_SAME_SITE` | `none` for the cross-site frontend/API arrangement |

There is no JWT secret: authentication uses Spring sessions and CSRF tokens.
Cookies remain HttpOnly. Keep one backend instance (ASG min=max=1); sessions are
stored in one JVM, lost on restart, and not suitable for multiple replicas without
an appropriate shared-session strategy. Browser third-party-cookie blocking can
still prevent cross-site sessions even with correct CORS/SameSite attributes.

The existing Aiven CA and JKS truststore are preserved. The public-CA truststore
contains trusted certificate entries only, not private keys. Its password belongs
in runtime environment properties. `VERIFY_IDENTITY` must not be downgraded.
Flyway owns V1–V4; Hibernate uses `validate`. Never enable schema auto-update,
Flyway clean, or automatic baselining as a startup workaround.

## Build and package

Run from the repository root with Java 21. Point test environment variables at a
**disposable** MySQL database, with local config imports disabled. If that local
test database has no TLS, supply the test-only Connector/J override below; this
must never be used for Aiven or the deployment:

```sh
SPRING_CONFIG_IMPORT= ./mvnw -f backend/pom.xml clean verify \
  -Dspring.datasource.hikari.data-source-properties.sslMode=DISABLED
```

The database URL/user/password and placeholder truststore properties must also be
provided for this test process. Use a new disposable schema, not the shared Aiven
database. The deployment verification used a temporary MySQL 8.4 container and
removed it after all 65 tests passed.

```sh
python3 backend/deploy/package_bundle.py
unzip -l backend/target/elastic-beanstalk.zip
```

The ZIP contains `app.jar`, `Procfile`, `certs/aiven-truststore.jks`, plus any future
backend `.platform`/`.ebextensions` files if present. No local `.env` is included;
`application-env.properties` is excluded from the JAR. Procfile disables local
configuration imports and defaults to port 5000. Local Spring development still
supports `.env` imports and port 8080.

## Existing environment and updates

Inspect before creating resources; do not create duplicate environments:

```sh
export AWS_PROFILE=default AWS_REGION=us-west-2 AWS_PAGER=''
aws sts get-caller-identity
aws elasticbeanstalk describe-environments \
  --query 'Environments[].{Name:EnvironmentName,Status:Status,Health:Health,CNAME:CNAME}'
```

Use application `financial-management-backend` and environment
`financial-management-backend-prod`. The EC2 instance profile is
`portfolio-eb-ec2-profile`; the service role is `portfolio-eb-service-role`.
Use the existing load-balanced one-instance environment. Default process health
checks should use HTTP port 80, `/actuator/health`, and success code 200; nginx
forwards to Spring on 5000. Actuator returns aggregate status only, including the
database check, and does not require authentication.

For an update after building and packaging:

```sh
EB_VERSION="backend-$(date +%Y%m%d-%H%M%S)"
EB_BUCKET=$(aws elasticbeanstalk create-storage-location --query S3Bucket --output text)
aws s3 cp backend/target/elastic-beanstalk.zip "s3://$EB_BUCKET/$EB_VERSION.zip"
aws elasticbeanstalk create-application-version \
  --application-name financial-management-backend --version-label "$EB_VERSION" \
  --source-bundle "S3Bucket=$EB_BUCKET,S3Key=$EB_VERSION.zip"
aws elasticbeanstalk update-environment \
  --environment-name financial-management-backend-prod --version-label "$EB_VERSION"
```

Wait for Ready/Green before deploying CloudFront. Inspect EB Events, Logs, nginx
logs, `/var/log/web.stdout.log`, and `/var/log/eb-engine.log` if startup fails.
Check secret mapping, truststore path/password, JDBC database name, Aiven allowlist,
DNS and outbound connectivity. Instances require egress to Aiven's actual service
port, HTTPS for Massive/AWS, and DNS. The ALB reaches instance nginx on port 80;
do not expose Java port 5000 publicly. Public-instance egress IPs can change; if
Aiven uses an IP allowlist, maintain it or adopt stable egress later.

## Reproducible CloudFront deployment

`infra/cloudfront.yaml` creates only one CloudFront distribution. It uses:

- The existing EB hostname as a parameterized custom HTTP-only origin, port 80.
- Default CloudFront hostname/certificate and HTTP-to-HTTPS viewer redirects.
- All seven HTTP methods; managed `CachingDisabled` cache policy (all TTLs zero).
- Managed `AllViewerExceptHostHeader`: forwards cookies, queries and viewer
  headers including Origin, CSRF, preflight and Authorization; origin Host is EB.
- No CloudFront CORS response policy: Spring is the sole CORS authority.
- Zero error-cache TTLs for configured errors and PriceClass_100.

```sh
EB_HOST=$(aws elasticbeanstalk describe-environments \
  --environment-names financial-management-backend-prod \
  --query 'Environments[0].CNAME' --output text)
curl --fail "http://$EB_HOST/actuator/health"
aws cloudformation validate-template --template-body file://infra/cloudfront.yaml
aws cloudformation deploy --stack-name portfolio-api-cloudfront \
  --template-file infra/cloudfront.yaml \
  --parameter-overrides ElasticBeanstalkHostname="$EB_HOST" \
  --no-fail-on-empty-changeset
aws cloudformation describe-stacks --stack-name portfolio-api-cloudfront \
  --query 'Stacks[0].Outputs' --output table
```

Wait until the distribution status is `Deployed`. Use its HTTPS URL for all login
and application tests; direct EB HTTP is used only for health checks. Validate GET,
OPTIONS, registration/login with CSRF, cookies (Secure/HttpOnly/SameSite=None),
portfolio creation, transactions, holdings, analytics, target allocation, logout,
and subsequent rejection of the old session. POST bodies and login query
parameters exercise origin forwarding. PATCH/DELETE are not application routes;
CloudFront allows them but this task does not invent new business endpoints.

## Vercel handoff

No Vercel deployment is performed here. Set this in Vercel's build environment,
then rebuild the frontend:

```text
VITE_API_BASE_URL=https://<distribution>.cloudfront.net
```

The centralized frontend API helper already uses that base URL for CSRF and API
requests and `credentials: 'include'`. Vite reads repository-root `.env` locally;
`VITE_` values are public and must never contain secrets. To test without editing
`.env`, run from `frontend/` with a process-level override:

```sh
VITE_API_BASE_URL=https://<distribution>.cloudfront.net npm run dev
```

Initially EB allows `http://localhost:5173`. Once the real Vercel production origin
is known, set `FRONTEND_URL=https://<actual-vercel-production-domain>` in EB and
retest credentialed CORS and browser login. Do not use a wildcard with credentials.
Do not add duplicate CORS headers in CloudFront.

## References

- [CloudFront managed cache policies](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-cache-policies.html)
- [CloudFront managed origin request policies](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-origin-request-policies.html)
- [Default CloudFront certificate](https://docs.aws.amazon.com/AWSCloudFormation/latest/TemplateReference/aws-properties-cloudfront-distribution-viewercertificate.html)
- [Beanstalk nginx/PORT](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/java-se-nginx.html)
