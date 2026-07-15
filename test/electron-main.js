// Minimal Electron shell loading the shared test page.
const { app, BrowserWindow } = require('electron');
const path = require('path');

app.whenReady().then(() => {
  const win = new BrowserWindow({ width: 420, height: 300, title: 'Electron Test App' });
  win.loadFile(path.join('/root/ui-mcp/test', 'chromium-test.html'));
});

app.on('window-all-closed', () => app.quit());
