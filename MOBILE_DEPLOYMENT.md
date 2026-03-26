# EchoBridge Mobile App Deployment Guide

## Option 1: WebView Mobile App (Easiest)

### Android WebView App

1. **Create new Android Studio project**
2. **Add internet permission to AndroidManifest.xml:**
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.RECORD_AUDIO" />
   <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
   ```

3. **MainActivity.java:**
   ```java
   import android.webkit.WebView;
   import android.webkit.WebViewClient;
   import android.webkit.WebChromeClient;
   import android.webkit.ValueCallback;
   import android.content.Intent;
   import android.net.Uri;

   public class MainActivity extends AppCompatActivity {
       private WebView webView;
       private ValueCallback<Uri[]> uploadMessage;
       private final int FILE_CHOOSER_RESULT_CODE = 1;

       @Override
       protected void onCreate(Bundle savedInstanceState) {
           super.onCreate(savedInstanceState);
           setContentView(R.layout.activity_main);

           webView = findViewById(R.id.webView);
           webView.getSettings().setJavaScriptEnabled(true);
           webView.getSettings().setDomStorageEnabled(true);
           webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
           
           // Enable microphone
           webView.getSettings().setAllowFileAccess(true);
           webView.getSettings().setAllowContentAccess(true);
           
           webView.setWebViewClient(new WebViewClient());
           webView.setWebChromeClient(new WebChromeClient() {
               @Override
               public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                   uploadMessage = filePathCallback;
                   Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                   intent.setType("audio/*");
                   startActivityForResult(Intent.createChooser(intent, "Select Audio"), FILE_CHOOSER_RESULT_CODE);
                   return true;
               }
           });

           // Load your EchoBridge server
           webView.loadUrl("http://your-server-url:8080");
       }

       @Override
       protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
           super.onActivityResult(requestCode, resultCode, intent);
           if (requestCode == FILE_CHOOSER_RESULT_CODE) {
               Uri[] results = null;
               if (resultCode == RESULT_OK) {
                   if (intent != null) {
                       String dataString = intent.getDataString();
                       if (dataString != null) {
                           results = new Uri[]{Uri.parse(dataString)};
                       }
                   }
               }
               uploadMessage.onReceiveValue(results);
               uploadMessage = null;
           }
       }
   }
   ```

4. **activity_main.xml:**
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
       android:layout_width="match_parent"
       android:layout_height="match_parent">
       
       <WebView
           android:id="@+id/webView"
           android:layout_width="match_parent"
           android:layout_height="match_parent" />
           
   </LinearLayout>
   ```

### iOS WebView App

1. **Create new iOS project in Xcode**
2. **Add microphone permission to Info.plist:**
   ```xml
   <key>NSMicrophoneUsageDescription</key>
   <string>This app needs microphone access for speech recognition</string>
   ```

3. **ViewController.swift:**
   ```swift
   import WebKit

   class ViewController: UIViewController, WKNavigationDelegate, WKUIDelegate {
       var webView: WKWebView!

       override func loadView() {
           webView = WKWebView()
           webView.navigationDelegate = self
           webView.uiDelegate = self
           view = webView
       }

       override func viewDidLoad() {
           super.viewDidLoad()
           
           let url = URL(string: "http://your-server-url:8080")!
           webView.load(URLRequest(url: url))
           webView.allowsBackForwardNavigationGestures = true
       }
   }
   ```

### React Native WebView App

1. **Create React Native project:**
   ```bash
   npx react-native init EchoBridgeMobile
   cd EchoBridgeMobile
   ```

2. **Install WebView:**
   ```bash
   npm install react-native-webview
   ```

3. **App.js:**
   ```javascript
   import React from 'react';
   import { WebView } from 'react-native-webview';
   import { PermissionsAndroid, Platform } from 'react-native';

   const App = () => {
     const requestMicrophonePermission = async () => {
       if (Platform.OS === 'android') {
         try {
           const granted = await PermissionsAndroid.request(
             PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
             {
               title: 'Microphone Permission',
               message: 'EchoBridge needs access to your microphone',
               buttonNeutral: 'Ask Me Later',
               buttonNegative: 'Cancel',
               buttonPositive: 'OK',
             },
           );
           return granted === PermissionsAndroid.RESULTS.GRANTED;
         } catch (err) {
           console.warn(err);
           return false;
         }
       }
       return true;
     };

     React.useEffect(() => {
       requestMicrophonePermission();
     }, []);

     return (
       <WebView
         source={{ uri: 'http://your-server-url:8080' }}
         style={{ flex: 1 }}
         mediaPlaybackRequiresUserAction={false}
         allowsInlineMediaPlayback={true}
         javaScriptEnabled={true}
         domStorageEnabled={true}
       />
     );
   };

   export default App;
   ```

## Option 2: Progressive Web App (PWA)

### 1. Add PWA Manifest

Create `src/main/resources/static/manifest.json`:
```json
{
  "name": "EchoBridge Speech Translation",
  "short_name": "EchoBridge",
  "description": "Real-time speech translation system",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#ffffff",
  "theme_color": "#2196f3",
  "icons": [
    {
      "src": "/icons/icon-192x192.png",
      "sizes": "192x192",
      "type": "image/png"
    },
    {
      "src": "/icons/icon-512x512.png",
      "sizes": "512x512",
      "type": "image/png"
    }
  ]
}
```

### 2. Create Service Worker

Create `src/main/resources/static/sw.js`:
```javascript
const CACHE_NAME = 'echobridge-v1';
const urlsToCache = [
  '/',
  '/static/css/main.css',
  '/static/js/main.js',
  '/manifest.json'
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(urlsToCache))
  );
});

self.addEventListener('fetch', event => {
  event.respondWith(
    caches.match(event.request)
      .then(response => {
        return response || fetch(event.request);
      })
  );
});
```

### 3. Update HTML Template

Modify `src/main/resources/templates/index.html`:
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>EchoBridge Speech Translation</title>
    
    <!-- PWA Meta Tags -->
    <meta name="theme-color" content="#2196f3">
    <link rel="manifest" href="/manifest.json">
    <link rel="apple-touch-icon" href="/icons/icon-192x192.png">
    
    <!-- Service Worker -->
    <script>
        if ('serviceWorker' in navigator) {
            navigator.serviceWorker.register('/sw.js');
        }
    </script>
</head>
<body>
    <!-- Your existing content -->
</body>
</html>
```

## Option 3: Capacitor/Cordova

### 1. Install Capacitor
```bash
npm install @capacitor/core @capacitor/cli @capacitor/android @capacitor/ios
npx cap init EchoBridge com.echobridge.app
```

### 2. Configure Capacitor
```bash
npx cap add android
npx cap add ios
```

### 3. Build and Run
```bash
npm run build
npx cap sync
npx cap run android
npx cap run ios
```

## Cloud Deployment for Mobile

### 1. Backend as a Service (BaaS)

#### Firebase
```javascript
// Firebase Functions for EchoBridge
const functions = require('firebase-functions');
const express = require('express');
const app = express();

// Your EchoBridge API endpoints
app.post('/api/microphone/start', (req, res) => {
    // Your existing microphone start logic
});

exports.api = functions.https.onRequest(app);
```

#### AWS Amplify
```yaml
# amplify/backend/api/echobridge-cloudformation-template.json
{
  "AWSCloudFormationKind": "API",
  "Parameters": {
    "AppId": {
      "Type": "String"
    },
    "EnvName": {
      "Type": "String"
    }
  }
}
```

### 2. Serverless Deployment

#### Vercel
Create `vercel.json`:
```json
{
  "version": 2,
  "builds": [
    {
      "src": "src/main/java/**",
      "use": "@vercel/java"
    }
  ],
  "routes": [
    {
      "src": "/(.*)",
      "dest": "/src/main/java/echobridge/com/java_app/EchoBridgeApplication.java"
    }
  ]
}
```

#### Netlify
Create `netlify/functions/echobridge.js`:
```javascript
const serverless = require('serverless-http');
const express = require('express');

const app = express();

// Your EchoBridge routes
app.post('/api/microphone/start', (req, res) => {
    // Your logic here
});

module.exports.handler = serverless(app);
```

## Mobile App Store Deployment

### Android (Google Play Store)

1. **Generate signed APK:**
   ```bash
   ./gradlew assembleRelease
   ```

2. **Create Google Play Console account**
3. **Upload APK**
4. **Fill store listing**
5. **Set up pricing and distribution**
6. **Submit for review**

### iOS (App Store)

1. **Generate IPA file:**
   - Open Xcode project
   - Product > Archive
   - Distribute App

2. **Create App Store Connect account**
3. **Upload IPA**
4. **Fill app information**
5. **Submit for review**

## Testing Mobile Apps

### Emulator Testing
```bash
# Android
npx react-native run-android

# iOS
npx react-native run-ios
```

### Real Device Testing
1. Enable USB debugging (Android)
2. Trust developer certificate (iOS)
3. Connect device and run

## Performance Optimization

### Mobile-Specific Optimizations
```javascript
// Optimize for mobile networks
const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;

if (connection) {
  // Adjust quality based on connection
  if (connection.effectiveType === 'slow-2g' || connection.effectiveType === '2g') {
    // Use lower quality audio
  }
}

// Optimize for battery
if ('getBattery' in navigator) {
  navigator.getBattery().then(battery => {
    if (battery.level < 0.2) {
      // Reduce processing to save battery
    }
  });
}
```

## Security for Mobile

1. **HTTPS only** in production
2. **API key protection**
3. **User authentication**
4. **Data encryption**
5. **Certificate pinning**

## Troubleshooting

### Common Issues
- **Microphone not working**: Check permissions
- **Connection refused**: Verify server URL
- **SSL errors**: Use HTTPS in production
- **Slow performance**: Optimize audio processing

### Debug Tools
- **Android**: Chrome DevTools Remote Debugging
- **iOS**: Safari Web Inspector
- **React Native**: Flipper

---

**Your EchoBridge mobile app is now ready for deployment!**
