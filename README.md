# AABrowser

AABrowser is an Android Auto application that allows you to browse the web safely while in your vehicle.

## Recent Changes

### 🚗 Driving Mode Enabled
We have unlocked the application to be usable while the vehicle is in motion. Previously, AABrowser was restricted to stationary-only usage.

### 🗺️ Re-categorization as Navigation App
To enable driving mode functionality and ensure compliance with Android Auto safety standards, the application has been re-categorized:
- **Android Manifest**: Updated to define the application as a `navigation` service rather than a game.
- **Automotive App Description**: Updated `automotive_app_desc.xml` to include navigation-specific metadata.

### 📜 Technical Updates
- **`AndroidManifest.xml`**: Modified to include the navigation category and updated package properties.
- **`automotive_app_desc.xml`**: Added support for browsing and interaction during active navigation sessions.

## 🚀 Future Roadmap
- Improved voice control integration.
- Custom bookmarks and history management.
- Enhanced touch response for Android Auto displays.
