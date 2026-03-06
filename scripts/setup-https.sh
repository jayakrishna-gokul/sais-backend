#!/bin/bash
# ================================================
# Sapphire AI — HTTPS Setup
#
# Supports three scenarios:
#   1. Public domain + Let's Encrypt (auto cert)
#   2. Private VPC + customer-provided cert
#   3. Internal testing + self-signed cert
#
# Usage:
#   # Scenario 1: Public domain
#   sudo bash setup-https.sh --mode letsencrypt --domain mycompany.sapphireai.com
#
#   # Scenario 2: Customer-provided cert (private VPC)
#   sudo bash setup-https.sh --mode customer-cert \
#       --domain internal.mycompany.com \
#       --cert /path/to/cert.pem \
#       --key /path/to/key.pem \
#       --chain /path/to/chain.pem
#
#   # Scenario 3: Self-signed (internal testing)
#   sudo bash setup-https.sh --mode self-signed --domain sapphire-internal
# ================================================

set -e

# ─── Defaults ───
MODE=""
DOMAIN=""
CERT_PATH=""
KEY_PATH=""
CHAIN_PATH=""
BACKEND_HOST="127.0.0.1"
BACKEND_PORT="8080"
UI_STATIC_PATH="/opt/sapphire/ui/dist"
LOG_PATH="/var/log/nginx"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
NGINX_CONF_DIR="/etc/nginx/sites-enabled"
SSL_DIR="/opt/sapphire/ssl"

# ─── Parse arguments ───
while [[ $# -gt 0 ]]; do
    case $1 in
        --mode)         MODE="$2";          shift 2 ;;
        --domain)       DOMAIN="$2";        shift 2 ;;
        --cert)         CERT_PATH="$2";     shift 2 ;;
        --key)          KEY_PATH="$2";      shift 2 ;;
        --chain)        CHAIN_PATH="$2";    shift 2 ;;
        --backend-host) BACKEND_HOST="$2";  shift 2 ;;
        --backend-port) BACKEND_PORT="$2";  shift 2 ;;
        --ui-path)      UI_STATIC_PATH="$2"; shift 2 ;;
        --help|-h)
            echo "Usage: sudo bash setup-https.sh [options]"
            echo ""
            echo "Options:"
            echo "  --mode              letsencrypt | customer-cert | self-signed"
            echo "  --domain            Your domain or hostname"
            echo "  --cert              Path to SSL cert (customer-cert mode)"
            echo "  --key               Path to SSL key (customer-cert mode)"
            echo "  --chain             Path to SSL chain (optional)"
            echo "  --backend-host      Spring Boot host (default: 127.0.0.1)"
            echo "  --backend-port      Spring Boot port (default: 8080)"
            echo "  --ui-path           Svelte UI dist path (default: /opt/sapphire/ui/dist)"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# ─── Validate ───
if [[ -z "$MODE" || -z "$DOMAIN" ]]; then
    echo "ERROR: --mode and --domain are required"
    echo "Run: sudo bash setup-https.sh --help"
    exit 1
fi

mkdir -p "$SSL_DIR"
mkdir -p "$LOG_PATH"

echo "================================================"
echo "  Sapphire AI - HTTPS Setup"
echo "  Mode:    $MODE"
echo "  Domain:  $DOMAIN"
echo "  Backend: $BACKEND_HOST:$BACKEND_PORT"
echo "================================================"

# ─── Generate certs based on mode ───
case $MODE in
    letsencrypt)
        echo "[1/3] Generating Let's Encrypt certificate..."
        apt-get update -qq
        apt-get install -y -qq certbot python3-certbot-nginx nginx

        certbot --nginx \
            -d "$DOMAIN" \
            --non-interactive \
            --agree-tos \
            --register-unsafely-without-email

        CERT_PATH="/etc/letsencrypt/live/$DOMAIN/fullchain.pem"
        KEY_PATH="/etc/letsencrypt/live/$DOMAIN/privkey.pem"
        CHAIN_PATH="/etc/letsencrypt/live/$DOMAIN/chain.pem"
        ;;

    customer-cert)
        echo "[1/3] Using customer-provided certificate..."
        if [[ -z "$CERT_PATH" || -z "$KEY_PATH" ]]; then
            echo "ERROR: --cert and --key required for customer-cert mode"
            exit 1
        fi
        if [[ ! -f "$CERT_PATH" || ! -f "$KEY_PATH" ]]; then
            echo "ERROR: Cert or key file not found"
            exit 1
        fi

        # Copy to standard location
        cp "$CERT_PATH" "$SSL_DIR/cert.pem"
        cp "$KEY_PATH"  "$SSL_DIR/key.pem"
        CERT_PATH="$SSL_DIR/cert.pem"
        KEY_PATH="$SSL_DIR/key.pem"

        if [[ -n "$CHAIN_PATH" && -f "$CHAIN_PATH" ]]; then
            cp "$CHAIN_PATH" "$SSL_DIR/chain.pem"
            CHAIN_PATH="$SSL_DIR/chain.pem"
        fi
        ;;

    self-signed)
        echo "[1/3] Generating self-signed certificate..."
        apt-get update -qq
        apt-get install -y -qq openssl nginx

        openssl req -x509 -newkey rsa:4096 \
            -keyout "$SSL_DIR/key.pem" \
            -out "$SSL_DIR/cert.pem" \
            -days 365 \
            -nodes \
            -subj "/CN=$DOMAIN" \
            -addext "subjectAltName=DNS:$DOMAIN,IP:127.0.0.1"

        CERT_PATH="$SSL_DIR/cert.pem"
        KEY_PATH="$SSL_DIR/key.pem"
        CHAIN_PATH=""
        ;;

    *)
        echo "ERROR: Invalid mode '$MODE'. Use: letsencrypt | customer-cert | self-signed"
        exit 1
        ;;
esac

# ─── Build chain directive ───
if [[ -n "$CHAIN_PATH" && -f "$CHAIN_PATH" ]]; then
    SSL_CHAIN_DIRECTIVE="ssl_trusted_certificate $CHAIN_PATH;"
else
    SSL_CHAIN_DIRECTIVE="# No chain file"
fi

# ─── Process nginx template ───
echo "[2/3] Generating nginx configuration..."

sed \
    -e "s|{{DOMAIN}}|$DOMAIN|g" \
    -e "s|{{BACKEND_HOST}}|$BACKEND_HOST|g" \
    -e "s|{{BACKEND_PORT}}|$BACKEND_PORT|g" \
    -e "s|{{SSL_CERT_PATH}}|$CERT_PATH|g" \
    -e "s|{{SSL_KEY_PATH}}|$KEY_PATH|g" \
    -e "s|{{SSL_CHAIN_DIRECTIVE}}|$SSL_CHAIN_DIRECTIVE|g" \
    -e "s|{{UI_STATIC_PATH}}|$UI_STATIC_PATH|g" \
    -e "s|{{LOG_PATH}}|$LOG_PATH|g" \
    "$PROJECT_ROOT/nginx/sapphire.conf.template" > "$NGINX_CONF_DIR/sapphire.conf"

# Remove default nginx config
rm -f "$NGINX_CONF_DIR/default"

# ─── Test and reload ───
echo "[3/3] Testing and reloading nginx..."
nginx -t
systemctl reload nginx || systemctl start nginx

echo ""
echo "================================================"
echo "  ✅ HTTPS Setup Complete"
echo "================================================"
echo "  HTTP  → http://$DOMAIN   (redirects to HTTPS)"
echo "  HTTPS → https://$DOMAIN"
echo "  Cert  → $CERT_PATH"
echo "  Nginx → $NGINX_CONF_DIR/sapphire.conf"
echo "================================================"