const path = require('path');
const { execSync } = require('child_process');
const globalRoot = execSync('npm root -g').toString().trim();
const WS = require(path.join(globalRoot, 'ws'));

const ws = new WS('ws://127.0.0.1:8080/subscriptions', ['graphql-transport-ws']);

ws.on('open', () => {
  ws.send(JSON.stringify({ type: 'connection_init', payload: {} }));
  setTimeout(() => {
    ws.send(JSON.stringify({
      id: '1',
      type: 'subscribe',
      payload: { query: 'subscription { uiStream(prompt: "account balance", surfaceId: "test") }' }
    }));
  }, 300);
});

ws.on('message', (data) => {
  const s = data.toString();
  console.log('--- MSG len=' + s.length + ' ---');
  // Print first 600 chars so we can see the structure
  console.log(s.substring(0, 600));
  console.log('');
});

ws.on('error', (e) => console.error('ERROR:', e.message));
ws.on('close', () => process.exit(0));

setTimeout(() => { ws.close(); }, 7000);
