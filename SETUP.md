# AI SWTBot Assistant - Setup Guide

## Quick Start (Fix Build Issues)

### Step 1: Load Target Platform in Eclipse

1. Open Eclipse IDE
2. Go to **Window → Show View → Other → Plug-in Development → Target Platform**
3. Right-click in the view, select **Reload Target Platform**
4. OR: Go to **Preferences → Plug-in Development → Target Platform**
5. Select the **"AI SWTBot Assistant Minimal"** target (or whichever is available)
6. Click **Set as Target Platform**
7. Wait for Eclipse to download dependencies (progress in bottom right)

### Step 2: Alternative - Use Minimal Target

If the full target doesn't work, use the minimal one:

1. Open `minimal.target` in Eclipse (double-click)
2. Click **Set as Target Platform** in top-right
3. Wait for download

### Step 3: Clean and Build

1. Go to **Project → Clean...**
2. Select **Clean all projects**
3. Check **Start a build immediately**
4. Click **Clean**

### Step 4: Verify Dependencies

Open `MANIFEST.MF` in the editor:
- Dependencies tab should show green checkmarks
- No red errors in the list

## Troubleshooting

### "org.eclipse cannot be resolved"

**Cause**: Target platform not loaded

**Fix**:
```
1. Check internet connection (Eclipse needs to download from update sites)
2. Try minimal.target instead of ai-swtbot-assistant.target
3. In Target Platform editor, click "Reload" button
4. Check Windows → Preferences → Install/Update → Available Software Sites
   - Make sure download.eclipse.org sites are enabled
```

### "com.google.gson cannot be resolved"

**Cause**: Orbit repository not accessible or wrong version

**Fix**:
```
1. Open MANIFEST.MF
2. In Dependencies tab, find com.google.gson
3. Check version is "2.10.1"
4. In target file, verify orbit repository URL
```

### "JRE System Library [JavaSE-21]" unbound

**Fix**:
```
1. Right-click project → Build Path → Configure Build Path
2. Libraries tab
3. Select JRE System Library → Edit
4. Choose JavaSE-21 or compatible JDK 21+
```

### Still Not Working?

**Nuclear Option** (works 99% of time):
```
1. Delete project from Eclipse (keep files!)
2. Delete .metadata folder in workspace (back up first!)
3. Restart Eclipse
4. File → Import → Existing Projects into Workspace
5. Point to c:/Son/pc-ai-assistant/ai-swtbot-assitant
6. Set target platform again
```

## For AI Development (Non-Eclipse)

To work on core logic without Eclipse target platform:

1. **Core package**: Extract pure Java code to separate package
2. **Dependencies**: Use Maven for Gson, Apache HTTP Client
3. **Test**: Write JUnit tests that don't need Eclipse
4. **Interface**: Define interfaces, Eclipse implements them

See `core/` package structure proposal in architecture.md

## IDE Setup Checklist

- [ ] Eclipse 2024-06 or newer (with PDE installed)
- [ ] JDK 21+ configured
- [ ] Target platform loaded (no errors in MANIFEST.MF)
- [ ] Project builds without errors
- [ ] Can run as Eclipse Application (Run → Run As → Eclipse Application)

## Network Requirements

The following URLs must be accessible:
- https://download.eclipse.org/eclipse/updates/4.31
- https://download.eclipse.org/tools/orbit/simrel/orbit-aggregation/release/4.31.0
- https://download.eclipse.org/technology/swtbot/releases/latest

If behind corporate proxy:
```
Window → Preferences → General → Network Connections
Configure proxy settings here
```
