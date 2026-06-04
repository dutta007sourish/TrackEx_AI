#!/bin/bash
set -e

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "❌ GitHub CLI (gh) is not installed."
    echo ""
    echo "Install it with:"
    echo "  brew install gh"
    echo ""
    echo "Then authenticate with:"
    echo "  gh auth login"
    exit 1
fi

# Check if gh is authenticated
if ! gh auth status &> /dev/null; then
    echo "❌ GitHub CLI is not authenticated."
    echo "Run: gh auth login"
    exit 1
fi

# Extract versionName from app/build.gradle.kts
VERSION=$(grep 'versionName' app/build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')

if [ -z "$VERSION" ]; then
    echo "❌ Could not extract versionName from app/build.gradle.kts"
    exit 1
fi

TAG="v${VERSION}"
APK_PATH="app/build/outputs/apk/release/app-release.apk"

echo "📦 Version: $VERSION"
echo "🏷️  Tag: $TAG"

# Build release APK
echo ""
echo "🔨 Building release APK..."
gradle assembleRelease --quiet

if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK not found at $APK_PATH"
    exit 1
fi

APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
echo "✅ APK built successfully ($APK_SIZE)"

# Check if release already exists
if gh release view "$TAG" &> /dev/null; then
    echo ""
    echo "⚠️  Release $TAG already exists."
    read -p "Do you want to overwrite it? (y/N): " OVERWRITE
    if [[ "$OVERWRITE" =~ ^[Yy]$ ]]; then
        echo "🗑️  Deleting existing release..."
        gh release delete "$TAG" --yes
        git tag -d "$TAG" 2>/dev/null || true
        git push origin ":refs/tags/$TAG" 2>/dev/null || true
    else
        echo "Aborted."
        exit 0
    fi
fi

# Create release
echo ""
echo "🚀 Creating GitHub release $TAG..."
gh release create "$TAG" "$APK_PATH" \
    --title "TrackEx AI $TAG" \
    --notes "Release $VERSION" \
    --latest

echo ""
echo "✅ Release created successfully!"
echo "📎 Download: $(gh release view "$TAG" --json url -q .url)"
