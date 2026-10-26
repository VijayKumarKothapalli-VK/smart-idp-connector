# Publishing the Smart IDP Connector publicly (free)

The connector is published to two free places:

- **GitHub**: public source code.
- **Maven Central**: the public Maven repository. Any Mule project anywhere can use it as a normal dependency.

Run the commands in **Git Bash**. Steps 1–5 are one-time setup; step 6 is repeated for every release.

---

## Step 1 – Put the code on GitHub (public)

Create the local repository and the first commit:

```bash
cd C:/WorkSpaces/MCP/idp-connector
git init -b main
git add .
git commit -m "Smart IDP Connector 2.0.0"
```

Create the public repository and push it (you are already logged in to `gh` as VijayKumarKothapalli-VK):

```bash
gh repo create smart-idp-connector --public --source . --push --description "Smart Mule 4 connector for MuleSoft Intelligent Document Processing (IDP)"
```

Check: https://github.com/VijayKumarKothapalli-VK/smart-idp-connector

`.gitignore` already excludes `target/`, `bin/` and Eclipse files. The source contains no credentials.

---

## Step 2 – Create your Maven Central account and namespace

1. Open https://central.sonatype.com and click **Sign In**.
2. Choose **Continue with GitHub** and sign in as **VijayKumarKothapalli-VK**.
3. Open **Namespaces**. **`io.github.vijaykumarkothapalli-vk`** should be listed as **Verified**. Signing in with GitHub verifies `io.github.<your-username>` automatically.
   - If it isn't there, click **Add Namespace**, enter `io.github.vijaykumarkothapalli-vk`, and follow the verification step. It asks you to create a temporary public GitHub repository with a given name.

The connector's `groupId` is already `io.github.vijaykumarkothapalli-vk`, so it matches.

---

## Step 3 – Create a publishing token and add it to Maven

1. On central.sonatype.com click your name (top right) → **View Account** → **Generate User Token** → **Ok**.
2. Copy the `<server>` block it shows into `C:\Users\Vijay\.m2\settings.xml`, inside `<servers>`. The `<id>` **must be `central`**:

```xml
<server>
  <id>central</id>
  <username>TOKEN_USERNAME</username>
  <password>TOKEN_PASSWORD</password>
</server>
```

Keep this token private. Never commit it and never paste it into chats.

---

## Step 4 – Create a signing key (GPG)

Maven Central only accepts signed files.

```bash
gpg --full-generate-key
```

Answer the prompts as follows:
- kind: **ECC (sign and encrypt)**, the default, or **RSA and RSA** with 4096 bits
- expiry: `0` (never) or e.g. `2y`
- name: `Vijay Kumar Kothapalli`
- email: `vijaykumarstar111@gmail.com`
- a passphrase you will remember (you'll need it for every release)

Show your key ID, the long hex value after `sec ... /`:

```bash
gpg --list-secret-keys --keyid-format long
```

Publish the public key so Maven Central can verify your signatures (replace the ID):

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

Wait a few minutes for the key to spread across key servers.

---

## Step 5 – Check the release build (no upload)

```bash
mvn -f C:/WorkSpaces/MCP/idp-connector/pom.xml clean verify -P release
```

Enter your GPG passphrase when asked. Expect `BUILD SUCCESS`, with these files in `target/`:
`smart-idp-connector-2.0.0-mule-plugin.jar`, `-sources.jar`, `-javadoc.jar`, and an `.asc` signature next to each.

---

## Step 6 – Upload to Maven Central

```bash
mvn -f C:/WorkSpaces/MCP/idp-connector/pom.xml clean deploy -P release
```

The upload is **validated but not yet published** (`autoPublish` is `false` in the pom), so you can check it first:

1. Open https://central.sonatype.com → **Publish** → **Deployments**.
2. The deployment `io.github.vijaykumarkothapalli-vk:smart-idp-connector:2.0.0` should show **VALIDATED**.
   If it shows **FAILED**, open it to read the reason (missing signature, unknown key, pom field), fix it, and run step 6 again.
3. Click **Publish**.

About 15–30 minutes later it's public at:
- https://central.sonatype.com/artifact/io.github.vijaykumarkothapalli-vk/smart-idp-connector
- https://repo1.maven.org/maven2/io/github/vijaykumarkothapalli-vk/smart-idp-connector/

Tag the release on GitHub:

```bash
git -C C:/WorkSpaces/MCP/idp-connector tag v2.0.0
git -C C:/WorkSpaces/MCP/idp-connector push origin v2.0.0
```

---

## Step 7 – How anyone uses it

Anyone adds this to their Mule project's `pom.xml`. No extra repository settings are needed:

```xml
<dependency>
  <groupId>io.github.vijaykumarkothapalli-vk</groupId>
  <artifactId>smart-idp-connector</artifactId>
  <version>2.0.0</version>
  <classifier>mule-plugin</classifier>
</dependency>
```

---

## Releasing a new version later

1. Change `<version>` in `pom.xml`, e.g. `2.0.1` for fixes or `2.1.0` for new features.
2. Commit and push to GitHub.
3. Run step 6 again and press **Publish**.
4. Tag it (`v2.0.1`).

## Rules to remember

- **A published version can never be changed or deleted.** Test before pressing Publish, and fix problems in a new version.
- The **groupId and artifactId are permanent** for this connector.
- Keep the Central token and the GPG passphrase private. Back up your GPG key: `gpg --export-secret-keys --armor YOUR_KEY_ID > smart-idp-signing-key.asc`, and store that file somewhere safe and offline.
