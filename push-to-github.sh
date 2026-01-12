#!/bin/bash

# ArchDroid GitHub Setup Script
# This script helps push the project to GitHub and configure CI/CD workflows

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}ArchDroid GitHub Setup Script${NC}"
echo "=================================="
echo ""

# Check if GitHub token is provided
if [ -z "$GITHUB_TOKEN" ]; then
    echo -e "${YELLOW}GitHub token not found in GITHUB_TOKEN environment variable.${NC}"
    echo "Please set your GitHub token:"
    echo "  export GITHUB_TOKEN=your_github_token_here"
    echo ""
    echo "To create a token:"
    echo "  1. Go to GitHub Settings → Developer settings → Personal access tokens"
    echo "  2. Generate new token (classic) with 'repo' scope"
    echo "  3. Copy the token and use it above"
    echo ""
    read -p "Enter your GitHub username: " GITHUB_USER
    read -p "Enter repository name (default: archdroid): " REPO_NAME
    REPO_NAME=${REPO_NAME:-archdroid}
else
    echo -e "${GREEN}GitHub token found!${NC}"
    read -p "Enter your GitHub username: " GITHUB_USER
    read -p "Enter repository name (default: archdroid): " REPO_NAME
    REPO_NAME=${REPO_NAME:-archdroid}
fi

# Get current directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo ""
echo -e "${GREEN}Step 1: Creating GitHub repository...${NC}"

# Create repository using GitHub API
curl -X POST -H "Authorization: token $GITHUB_TOKEN" \
    -H "Accept: application/vnd.github.v3+json" \
    https://api.github.com/user/repos \
    -d "{\"name\":\"$REPO_NAME\",\"description\":\"ArchDroid - Terminal emulator for Android with Arch Linux ARM64 bootstrap support\",\"private\":false,\"auto_init\":false}" 2>/dev/null | grep -q "full_name" && \
    echo "Repository created or already exists."

echo ""
echo -e "${GREEN}Step 2: Adding remote origin...${NC}"

# Add remote
git remote add origin "https://github.com/$GITHUB_USER/$REPO_NAME.git" 2>/dev/null || \
    git remote set-url origin "https://github.com/$GITHUB_USER/$REPO_NAME.git"

echo ""
echo -e "${GREEN}Step 3: Pushing to GitHub...${NC}"

# Push to GitHub
git push -u origin master:main

echo ""
echo -e "${GREEN}✓ Successfully pushed to GitHub!${NC}"
echo ""
echo "Next steps:"
echo "  1. Go to your repository: https://github.com/$GITHUB_USER/$REPO_NAME"
echo "  2. Navigate to Settings → Secrets and variables → Actions"
echo "  3. Add the following secrets for release builds:"
echo "     - ANDROID_KEYSTORE_BASE64: Base64 encoded keystore file"
echo "     - KEYSTORE_PASSWORD: Keystore password"
echo "     - KEY_ALIAS: Key alias name"
echo "     - KEY_PASSWORD: Key password"
echo ""
echo "To create a keystore for release builds:"
echo "  keytool -genkeypair -v -storetype PKCS12 -keyalg RSA -keysize 2048 -validity 10000"
echo "    -keystore app/release.keystore -alias archdroid"
echo ""
echo "To encode keystore for GitHub secret:"
echo "  base64 -w 0 app/release.keystore"
echo ""
