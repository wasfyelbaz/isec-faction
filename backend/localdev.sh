
# LibreOffice is started by the application itself (LibreOfficeServerManager), so it can be
# restarted when a report font is uploaded. Uploaded fonts are installed under REPORT_FONTS_DIR;
# point it somewhere writable when not running as root.
export REPORT_FONTS_DIR="${REPORT_FONTS_DIR:-$HOME/.local/share/fonts/faction-uploaded}"

# SSO_ENCRYPTION_KEY encrypts SMTP/IMAP credentials and report passwords at rest.
#
# Generated per machine rather than hardcoded. The value that used to live here was
# base64(sha256("secret")) — a published example — and shipping that in a public repo
# would hand every self-hoster who copies this script an encryption key whose preimage
# is one dictionary word.
#
# Set your own to keep encrypted data readable across restarts:
#   export SSO_ENCRYPTION_KEY=$(openssl rand -base64 32)
if [ -z "${SSO_ENCRYPTION_KEY:-}" ]; then
  SSO_ENCRYPTION_KEY=$(openssl rand -base64 32)
  echo "SSO_ENCRYPTION_KEY not set — generated an ephemeral one for this run."
  echo "Anything encrypted now will be unreadable after restart. To persist it:"
  echo "  export SSO_ENCRYPTION_KEY=\$(openssl rand -base64 32)"
fi
export SSO_ENCRYPTION_KEY

# Start the Spring Boot application
FRONTEND_URL=http://localhost:3000 BACKEND_URL=http://localhost:3000 mvn spring-boot:run
