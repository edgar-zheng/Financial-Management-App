# Deployed portfolio API — 2026-11-06

## Discovered state

The repository already contained the Java 21 / Spring Boot 4.1.1 Maven backend,
Vite frontend, session/CSRF authentication, configurable credentialed CORS,
Actuator health, Procfile, and Aiven JKS truststore with VERIFY_IDENTITY.
There was no infrastructure-as-code convention or custom EB nginx configuration.
The AWS default profile authenticated as a non-root IAM user in us-west-2.
The prior interrupted run had created the two IAM roles and instance profile,
but no Beanstalk application/environment or CloudFront distribution existed.

## Resources

| Resource | Value |
| --- | --- |
| AWS region | `us-west-2` |
| EB application | `financial-management-backend` |
| EB environment | `financial-management-backend-prod` |
| EB environment ID | `e-3tbbrb5bep` |
| EB hostname | `financial-management-backend-prod.eba-bm4gtgcv.us-west-2.elasticbeanstalk.com` |
| EB platform | `64bit Amazon Linux 2023 v4.14.1 running Corretto 21` |
| Application version | `backend-20261106-094306` |
| CloudFormation stack | `portfolio-api-cloudfront` |
| Distribution ID | `E2FHWSR9ZGJBTH` |
| Distribution hostname | `d5xnufx0gsczd.cloudfront.net` |
| Public API | `https://d5xnufx0gsczd.cloudfront.net` |

Created the first EB application/environment, application version and S3 bundle,
with its managed ALB, one t3.small EC2 instance, Auto Scaling group, security groups
and monitoring resources. Reused `portfolio-eb-ec2-profile`,
`portfolio-eb-ec2-role` and `portfolio-eb-service-role` from the previous run.
Created one CloudFormation stack containing one CloudFront distribution.
No duplicate EB environment, ACM certificate, Route 53 zone, custom alias, or
Vercel resource was created. These AWS resources remain running and incur charges.

The deployment ZIP includes `app.jar`, `Procfile`, and
`certs/aiven-truststore.jks`. EB uses
`AIVEN_TRUSTSTORE_PATH=/var/app/current/certs/aiven-truststore.jks`.
Credentials were supplied via EB environment properties, not Git or the bundle.

## Validation results

- Full Maven `clean verify`: **65 tests passed**, zero failures/errors/skips.
  Integration tests used a disposable local MySQL 8.4 container; it was removed.
- Frontend: **6 unit tests passed** and production build passed. No lint script
  is configured. No frontend source changes were needed for this deployment.
- AWS CloudFormation template validation passed; stack is **CREATE_COMPLETE**.
- EB final status **Ready / Green / Ok**; ALB target **healthy**.
- EB logs verified Spring listening on port **5000**, Hikari startup, Flyway
  validation of all four migrations, schema already current, Hibernate/JPA
  initialization, and successful application startup with no startup errors.
- Instance ingress is port 80 from the ALB security group; Java port 5000 is not
  publicly exposed. ALB has an HTTP port 80 listener, intentionally.
- Direct EB `/actuator/health`: HTTP 200, `{"status":"UP"}`.
- CloudFront status **Deployed**, no aliases, default certificate, HTTP-only
  EB origin, managed CachingDisabled and AllViewerExceptHostHeader policies.
- HTTPS validates normally; HTTP redirects to HTTPS. GET and OPTIONS passed.
  Spring supplied exact-origin credentialed CORS and rejected an untrusted origin.
- POST registration/login/portfolio/transaction and PUT allocations passed.
  Login used an email query parameter and password form body, proving query/body
  forwarding without placing the password in the URL.
- Secure / HttpOnly / SameSite=None cookies reached the HTTP client and Chrome.
  Authenticated requests succeeded; logout invalidated the session. Anonymous
  requests could not receive a cached authenticated identity.
- Portfolio fetch, transaction history, derived holdings, live market valuation,
  analytics and allocation drift passed. Invalid allocation totals were rejected.
- Subsequent fresh-login reads retrieved persisted data. Direct read-only JDBC
  using VERIFY_IDENTITY independently verified TLS, portfolio ownership, saved
  transaction/allocation rows, and four successful Flyway migrations.
- Chrome from `http://localhost:5173` verified the real React login, portfolio
  fetch, holdings display, cookie attributes and logout using CloudFront.
- Repeated health requests returned `X-Cache: Miss from cloudfront`, no Age, and
  `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`.
- PATCH/DELETE business endpoints do not exist; CloudFront permits these methods
  but no new application routes were added. Authorization-header authentication
  is not used; sessions/CSRF were preserved. The forwarding policy includes it.
- Identifiable demo records remain in Aiven under **portfolio ID 2**.
  All test sessions were logged out; the temporary local Vite server was stopped.

## Vercel handoff

Set this build-time variable in Vercel, then build/redeploy React:

```properties
VITE_API_BASE_URL=https://d5xnufx0gsczd.cloudfront.net
```

EB currently expects:

```properties
FRONTEND_URL=http://localhost:5173
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=none
```

Once the actual Vercel production origin is known, update only the allowed origin
and revalidate browser login:

```sh
aws --profile default --region us-west-2 elasticbeanstalk update-environment \
  --environment-name financial-management-backend-prod \
  --option-settings Namespace=aws:elasticbeanstalk:application:environment,OptionName=FRONTEND_URL,Value=https://YOUR-ACTUAL-PRODUCTION-DOMAIN.vercel.app
```

Use an exact origin with no path or trailing slash. A Vercel preview domain is a
separate origin and is not automatically allowed. No Vercel deployment was done.
The root local `.env` was left unchanged. For local testing without editing it:

```sh
cd frontend
VITE_API_BASE_URL=https://d5xnufx0gsczd.cloudfront.net npm run dev
```

## Limits and operations

This is **browser HTTPS → CloudFront → HTTP → EB**, not end-to-end HTTPS.
The public EB hostname can still be reached directly. The separate Aiven link
uses TLS with VERIFY_IDENTITY. CloudFront/Spring CORS does not restrict non-browser
clients or replace authentication.

Sessions are held in one backend JVM; the ASG min/max are both 1. Restarts log
users out. Scaling requires a session strategy and was not introduced here.
Browsers that block third-party cookies may still prevent Vercel/CloudFront
cross-site sessions despite correct CORS and SameSite settings. Chrome passed in
the tested profile; this is not a guarantee for every browser privacy mode.

See [the deployment guide](../backend/deploy/ELASTIC_BEANSTALK.md) for packaging,
updates and CloudFormation commands. Review the Git changes before committing.
No commits or pushes were made by this task.
