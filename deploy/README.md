# DisputeX - AWS Demo Deployment Runbook

A temporary (~2 week) public demo on a single EC2 instance running the existing Docker
Compose stack behind Caddy (HTTPS), publishing dispute events to a real Amazon SQS FIFO
queue, with container logs streamed to CloudWatch. Region: af-south-1 (Cape Town).

Infrastructure is defined as code in `deploy/infra.yaml` (CloudFormation): one command
creates everything, one command deletes everything.

> You run every command yourself, logged into your own AWS account. Nothing here runs
> automatically. Set the billing guardrail (Step 1) BEFORE deploying, and run Teardown
> when the review is done.

## Scope note (state this to the reviewer)

The async pipeline is implemented up to and including PUBLISHING to the queue (the
producer). Each queued dispute is persisted and a message is sent to the SQS FIFO queue;
the returned SQS message id is stored on the dispute row for audit. There is deliberately
NO consumer: the back-office agent service that would read the queue and assign disputes
is OUT OF SCOPE. Messages therefore accumulate in the queue (visible in the SQS console),
which is itself proof the producer works. A production system would add a consumer plus a
dead-letter queue and redrive.

The deployed stack runs under the Spring `prod` profile (set in
`deploy/docker-compose.prod.yml`). For this throwaway reviewer demo the profile loads the
labelled demo accounts on purpose, so a reviewer can log in with a shared account and
exercise every dispute scenario immediately (see the demo-account table in the main
README). The `prod` profile differs from `local` only in logging (no SQL/DEBUG) and in
using real Amazon SQS instead of LocalStack.

## What the CloudFormation stack creates

- SQS FIFO queue `dispute-events.fifo` (14-day message retention)
- CloudWatch log group `disputex` (configurable retention, default 7 days)
- IAM role + instance profile `disputex-ec2-role`, scoped to: send to that one queue, and
  write to that one log group - nothing else
- A locked-down security group (80/443 public, 22 from your IP only)
- A t3.small EC2 instance (Amazon Linux 2023) + an Elastic IP

## Architecture

```
Internet --(443/HTTPS)--> Caddy (Let's Encrypt TLS) --+--> frontend (nginx SPA)
                                                       +--> app (Spring Boot :8087)
                                                              |  publish only
                                                              v
                                               Amazon SQS FIFO: dispute-events.fifo
     postgres (container, Docker volume) + CloudWatch Logs (app/frontend/caddy)
                        all on one EC2 host
```

Only Caddy (80/443) is internet-facing. Postgres, the backend and the frontend are
reachable only inside the Docker network. SQS and CloudWatch are reached outbound using
the EC2 instance role (no static AWS keys on the host). LocalStack is NOT used in this
deployment (it stays for local development only).

---

## Prerequisites (on your laptop)

- AWS CLI installed and configured (`aws configure`) with your account.
- You deploy into af-south-1: add `--region af-south-1` to the commands, or set it as your
  default profile region.

## Step 1 - Billing guardrail (DO THIS FIRST)

1. AWS Console -> Billing -> Budgets -> Create a monthly cost budget with a cap you're
   comfortable with.
2. Add alert thresholds so you're emailed before the cap is reached.

## Step 2 - SSH key pair

Create (once) and download a key pair you'll use to SSH into the instance:

```bash
aws ec2 create-key-pair --region af-south-1 --key-name disputex-key \
  --query KeyMaterial --output text > disputex-key.pem
chmod 400 disputex-key.pem
```

Keep `disputex-key.pem` safe. It is gitignored (`*.pem`) so it can't be committed.

## Step 3 - Find your public IP (for the SSH firewall rule)

```bash
curl -s https://checkip.amazonaws.com
```
Use the result as `<your-ip>/32` in the next step (e.g. 102.68.1.23/32).

## Step 4 - Create all AWS resources (CloudFormation)

From the repo root:

```bash
aws cloudformation deploy \
  --region af-south-1 \
  --template-file deploy/infra.yaml \
  --stack-name disputex-demo \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
      KeyPairName=disputex-key \
      SshAllowedCidr=<your-ip>/32
```

`--capabilities CAPABILITY_NAMED_IAM` is required because the stack creates a named IAM role.
When it finishes, read the outputs you'll need:

```bash
aws cloudformation describe-stacks --region af-south-1 --stack-name disputex-demo \
  --query "Stacks[0].Outputs" --output table
```

Note the `PublicIp`, `QueueUrl` and `Region` values.

## Step 5 - DNS

Point your domain's A record at the `PublicIp` from Step 4 (TTL 300). Confirm it resolves
(`nslookup your-domain`) before Step 8 - Let's Encrypt needs the record live to issue the cert.

## Step 6 - Install Docker on the instance

```bash
ssh -i disputex-key.pem ec2-user@<PublicIp>
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
exit    # log out/in so the docker group applies
```

Re-SSH, then `docker version && docker compose version` (need Compose v2.24+ for `!reset`).

## Step 7 - Code + secrets on the instance

```bash
git clone <your-repo-url> disputex && cd disputex
cp deploy/.env.example deploy/.env
openssl rand -hex 48    # -> JWT_SECRET_KEY
openssl rand -hex 16    # -> DB_PASSWORD
nano deploy/.env        # set DOMAIN, JWT_SECRET_KEY, DB_PASSWORD,
                        # AWS_REGION=af-south-1, AWS_SQS_DISPUTE_QUEUE_URL=<QueueUrl from Step 4>
```

No AWS keys go in `.env` - the instance role supplies them.

## Step 8 - Launch the stack

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
```

Order: postgres -> app (Flyway V1..V5 schema + demo seed) -> frontend -> caddy (provisions TLS).
First boot builds images + issues the cert; allow 2-3 minutes.

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml ps
```

## Step 9 - Verify

- `https://your-domain` -> login screen with a valid certificate.
- `https://your-domain/actuator/health` -> {"status":"UP"} (the only Actuator endpoint exposed)
- `https://your-domain/swagger-ui/index.html` -> API docs.
- Log in with one of the seeded demo accounts (see the main README demo-account table;
  shared password there). Open Transactions, dispute a `*-MANUAL-PRE60` transaction (routed
  to an agent, so it publishes to SQS), then check the SQS console: the `dispute-events.fifo`
  queue "Messages available" count rises. A `*-AUTO-PRE60` dispute is auto-approved + refunded
  and also publishes a message.

## Watching logs live (CloudWatch)

While a reviewer tests, tail the app logs from your laptop:

```bash
aws logs tail disputex --region af-south-1 --follow
```

Or in the console: CloudWatch -> Log groups -> `disputex` (streams: app / frontend / caddy).

## Operating

- Stop (keeps DB on the volume): `docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml down`
- Restart: re-run the Step 8 command.
- Reset the demo DB: `... down -v` then up again.

## Teardown (do this after the review)

```bash
# on the instance:
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml down -v

# from your laptop - deletes EC2, EIP, EBS, SQS queue, IAM role, log group in one go:
aws cloudformation delete-stack --region af-south-1 --stack-name disputex-demo
aws cloudformation wait stack-delete-complete --region af-south-1 --stack-name disputex-demo

# the SSH key pair is not part of the stack; remove it if you no longer need it:
aws ec2 delete-key-pair --region af-south-1 --key-name disputex-key
```

Then confirm in the console that the stack is gone and the Budget shows no ongoing spend.

## Security notes

- JWT key + DB password live only in `deploy/.env` on the server (gitignored).
- No static AWS credentials on the host; SQS + CloudWatch access is via the instance role,
  scoped to `sqs:SendMessage` on one queue and `logs:*` on one log group (no receive/delete,
  matching the producer-only scope).
- Only Caddy is exposed; the database is internal. SSH is restricted to your IP. HTTPS is
  enforced with HSTS. CORS is pinned to `https://${DOMAIN}`.
- This is a demonstration deployment (single host, no managed DB, no backups, no WAF),
  documented as such - not a hardened production system.