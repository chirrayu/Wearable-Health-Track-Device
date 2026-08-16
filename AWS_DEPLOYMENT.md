# Deploy to AWS Elastic Beanstalk

This setup deploys the Python backend to AWS Elastic Beanstalk. The Android
app and ESP32 firmware are not uploaded to AWS; they connect to the public
HTTPS address that Elastic Beanstalk creates.

## 1. Create a PostgreSQL database

In the AWS Console, create an **Amazon RDS PostgreSQL** instance in the same
AWS Region you will use for Elastic Beanstalk (for example, `ap-south-1`).

- Keep the database private; do not expose port 5432 to the internet.
- After creating the Elastic Beanstalk environment, allow inbound TCP 5432 on
  the RDS security group only from the Elastic Beanstalk EC2 instance security
  group.
- Record the database endpoint, database name, username, and password.

Your backend database variable must be in this exact form:

```text
DATABASE_URL=postgresql+psycopg2://USERNAME:PASSWORD@RDS-ENDPOINT:5432/DATABASE_NAME
```

URL-encode special characters in the username or password (for example, `@`
becomes `%40`).

## 2. Create the deployable ZIP

Run this in PowerShell from the repository root. The ZIP must contain the
backend files directly at its root, including `main.py`, `requirements.txt`,
and `Procfile`.

```powershell
Push-Location software\backend
Compress-Archive -Path * -DestinationPath ..\..\triage-ai-backend.zip -Force
Pop-Location
```

## 3. Create Elastic Beanstalk environment

1. Open the [Elastic Beanstalk console](https://console.aws.amazon.com/elasticbeanstalk/).
2. Create an application named `triage-ai` and choose **Web server environment**.
3. Choose the **Python 3.11 on Amazon Linux 2023** platform.
4. Upload `triage-ai-backend.zip` as the application code.
5. Use a single-instance environment for a demonstration or configure a load
   balancer and at least two instances for operational use.

The included `Procfile` starts FastAPI on port 8000, which Elastic Beanstalk's
proxy routes to the public HTTPS endpoint.

## 4. Set environment properties

In Elastic Beanstalk: **Configuration → Updates, monitoring, and logging →
Environment properties**, add:

```text
ENVIRONMENT=production
DATABASE_URL=postgresql+psycopg2://USERNAME:PASSWORD@RDS-ENDPOINT:5432/DATABASE_NAME
SECRET_KEY=<a long random value>
ADMIN_USERNAME=healthadmin
ADMIN_PASSWORD=<a strong password>
AWS_REGION=ap-south-1
S3_BUCKET_NAME=triage-ai-photos
```

Add `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` only if you are not using
an EC2 instance role. Prefer an instance role with access limited to the photo
bucket. Add Firebase credentials only if push notifications are needed.

If recovering the login password, set `RESET_ADMIN_PASSWORD=true` for one
deployment, then remove it immediately after logging in.

## 5. Connect the app and suit

After the environment health becomes green, open its public URL and verify
that `/` returns the service health JSON. Then replace the URL in
`software/frontend/app/src/main/java/com/example/healthmonitor/NetworkConfig.kt`
with the Elastic Beanstalk HTTPS URL, build a new APK, and provision the suit
from the app over BLE.
