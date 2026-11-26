package com.example.security

import android.content.Context
import android.util.Base64
import com.example.data.VaultMetadataEntity
import com.example.data.VaultRecordEntity
import com.example.model.VaultItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HtmlVaultExporter {

    fun generateSelectiveHtmlVault(
        context: Context,
        items: List<VaultItem>,
        exportPassphrase: String
    ): File {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val formattedDate = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US).format(Date())
        val file = File(exportDir, "blackbox_custom_export_$timestamp.html")

        // Fresh cryptographic salt generated exclusively for this export
        val exportSalt = BlackBoxSecurity.generateRandomSalt()
        val exportSaltBase64 = Base64.encodeToString(exportSalt, Base64.NO_WRAP)
        val exportKey = BlackBoxSecurity.deriveMasterKey(exportPassphrase.toCharArray(), exportSalt)

        val recordsJson = JSONArray()
        items.forEach { item ->
            val payload = BlackBoxSecurity.serializeItemPayload(item)
            val (ciphertext, iv) = BlackBoxSecurity.encrypt(payload, exportKey)
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("category", item.category.name)
            obj.put("ciphertext", ciphertext)
            obj.put("iv", iv)
            obj.put("isFavorite", item.isFavorite)
            obj.put("updatedAt", item.updatedAt)
            recordsJson.put(obj)
        }

        val htmlContent = buildHtmlDocument(
            masterSalt = exportSaltBase64,
            verifierHash = "",
            exportTimestamp = formattedDate,
            recordsJsonString = recordsJson.toString()
        )

        file.writeText(htmlContent, Charsets.UTF_8)
        return file
    }

    fun generateHtmlVault(
        context: Context,
        metadata: VaultMetadataEntity,
        records: List<VaultRecordEntity>
    ): File {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val formattedDate = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US).format(Date())
        val file = File(exportDir, "blackbox_vault_$timestamp.html")

        val recordsJson = JSONArray()
        records.forEach { record ->
            val obj = JSONObject()
            obj.put("id", record.id)
            obj.put("title", record.title)
            obj.put("category", record.category)
            obj.put("ciphertext", record.encryptedPayload)
            obj.put("iv", record.initializationVector)
            obj.put("isFavorite", record.isFavorite)
            obj.put("updatedAt", record.updatedAt)
            recordsJson.put(obj)
        }

        val htmlContent = buildHtmlDocument(
            masterSalt = metadata.masterSalt,
            verifierHash = metadata.verifierHash,
            exportTimestamp = formattedDate,
            recordsJsonString = recordsJson.toString()
        )

        file.writeText(htmlContent, Charsets.UTF_8)
        return file
    }

    private fun buildHtmlDocument(
        masterSalt: String,
        verifierHash: String,
        exportTimestamp: String,
        recordsJsonString: String
    ): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>The Black-Box — Encrypted Portable Vault</title>
  <style>
    :root {
      --bg: #050505;
      --surface: #0d0f12;
      --surface-variant: #151922;
      --border: #222b38;
      --accent: #00e676;
      --accent-glow: rgba(0, 230, 118, 0.25);
      --text: #f8fafc;
      --text-muted: #94a3b8;
      --cyan: #38bdf8;
      --red: #ef4444;
      --card-bg: #0f131a;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background-color: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 24px 16px;
    }
    .header {
      width: 100%;
      max-width: 960px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-bottom: 20px;
      border-bottom: 1px solid var(--border);
      margin-bottom: 24px;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .brand-icon {
      width: 36px;
      height: 36px;
      background: var(--surface-variant);
      border: 1px solid var(--accent);
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--accent);
      font-weight: 900;
      box-shadow: 0 0 12px var(--accent-glow);
    }
    .brand-title {
      font-size: 20px;
      font-weight: 700;
      letter-spacing: 0.5px;
    }
    .badge {
      font-size: 11px;
      font-family: monospace;
      padding: 4px 8px;
      border-radius: 4px;
      background: rgba(0, 230, 118, 0.1);
      border: 1px solid var(--accent);
      color: var(--accent);
    }
    .container {
      width: 100%;
      max-width: 960px;
    }
    .unlock-card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 36px 24px;
      max-width: 440px;
      margin: 60px auto;
      text-align: center;
      box-shadow: 0 8px 32px rgba(0,0,0,0.5);
    }
    .unlock-card h2 {
      font-size: 22px;
      margin-bottom: 10px;
    }
    .unlock-card p {
      color: var(--text-muted);
      font-size: 14px;
      margin-bottom: 24px;
      line-height: 1.5;
    }
    .input-group {
      margin-bottom: 20px;
      text-align: left;
    }
    .input-group label {
      display: block;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 1px;
      color: var(--text-muted);
      margin-bottom: 6px;
    }
    input[type="password"], input[type="text"] {
      width: 100%;
      padding: 12px 14px;
      background: var(--surface-variant);
      border: 1px solid var(--border);
      border-radius: 6px;
      color: var(--text);
      font-size: 15px;
      outline: none;
      transition: border-color 0.2s, box-shadow 0.2s;
    }
    input[type="password"]:focus, input[type="text"]:focus {
      border-color: var(--accent);
      box-shadow: 0 0 8px var(--accent-glow);
    }
    .btn {
      background: var(--accent);
      color: #002e13;
      border: none;
      padding: 12px 20px;
      border-radius: 6px;
      font-size: 15px;
      font-weight: 600;
      cursor: pointer;
      width: 100%;
      transition: background 0.2s, transform 0.1s;
    }
    .btn:hover {
      background: #69f0ae;
    }
    .btn:active {
      transform: scale(0.98);
    }
    .btn-secondary {
      background: var(--surface-variant);
      color: var(--text);
      border: 1px solid var(--border);
      width: auto;
      padding: 6px 12px;
      font-size: 13px;
    }
    .btn-secondary:hover {
      border-color: var(--accent);
      color: var(--accent);
    }
    .error-msg {
      color: var(--red);
      font-size: 13px;
      margin-top: 12px;
      display: none;
    }
    .vault-view {
      display: none;
    }
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: 20px;
      align-items: center;
      justify-content: space-between;
    }
    .search-input {
      flex: 1;
      min-width: 240px;
      padding: 10px 14px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 6px;
      color: var(--text);
      font-size: 14px;
    }
    .categories {
      display: flex;
      gap: 8px;
      overflow-x: auto;
    }
    .cat-chip {
      padding: 6px 12px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 20px;
      font-size: 12px;
      cursor: pointer;
      color: var(--text-muted);
      user-select: none;
    }
    .cat-chip.active {
      background: rgba(0, 230, 118, 0.15);
      border-color: var(--accent);
      color: var(--accent);
      font-weight: 600;
    }
    .items-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(290px, 1fr));
      gap: 16px;
    }
    .item-card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 18px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      position: relative;
    }
    .item-card:hover {
      border-color: #334155;
    }
    .item-card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .item-card-title {
      font-size: 16px;
      font-weight: 600;
    }
    .item-card-cat {
      font-size: 11px;
      text-transform: uppercase;
      font-family: monospace;
      color: var(--cyan);
      background: rgba(56, 189, 248, 0.1);
      padding: 2px 6px;
      border-radius: 4px;
    }
    .item-field {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .item-field-label {
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: var(--text-muted);
    }
    .item-field-value {
      font-family: monospace;
      font-size: 13px;
      background: var(--surface-variant);
      padding: 6px 8px;
      border-radius: 4px;
      word-break: break-all;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .copy-btn {
      background: transparent;
      border: none;
      color: var(--accent);
      cursor: pointer;
      font-size: 12px;
      padding: 2px 6px;
    }
    .copy-btn:hover {
      text-decoration: underline;
    }
    .notes-box {
      font-size: 13px;
      color: var(--text-muted);
      line-height: 1.4;
      white-space: pre-wrap;
      max-height: 120px;
      overflow-y: auto;
    }
    .status-bar {
      margin-top: 36px;
      padding-top: 16px;
      border-top: 1px solid var(--border);
      font-size: 12px;
      color: var(--text-muted);
      display: flex;
      justify-content: space-between;
      width: 100%;
      max-width: 960px;
    }
  </style>
</head>
<body>
  <div class="header">
    <div class="brand">
      <div class="brand-icon">BB</div>
      <div class="brand-title">The Black-Box</div>
      <span class="badge">AES-256-GCM OFFLINE</span>
    </div>
    <div id="lockHeaderBtn" style="display:none;">
      <button class="btn btn-secondary" onclick="lockVault()">Lock Vault</button>
    </div>
  </div>

  <div class="container">
    <!-- UNLOCK VIEW -->
    <div id="unlockView" class="unlock-card">
      <h2>Decrypt Vault</h2>
      <p>Enter your Black-Box Master Password to decrypt this database snapshot locally in your browser.</p>
      <div class="input-group">
        <label for="masterPassword">Master Password</label>
        <input type="password" id="masterPassword" placeholder="••••••••••••••••" autofocus onkeydown="if(event.key==='Enter') decryptVault()">
      </div>
      <button class="btn" id="decryptBtn" onclick="decryptVault()">Decrypt Database</button>
      <div class="error-msg" id="errorMsg">Authentication Failed. Invalid password or corrupted payload.</div>
    </div>

    <!-- VAULT VIEW -->
    <div id="vaultView" class="vault-view">
      <div class="toolbar">
        <input type="text" id="searchInput" class="search-input" placeholder="Search passwords, accounts, notes..." oninput="renderItems()">
        <div class="categories">
          <div class="cat-chip active" onclick="setCategory('ALL', this)">All (<span id="countAll">0</span>)</div>
          <div class="cat-chip" onclick="setCategory('LOGIN', this)">Logins</div>
          <div class="cat-chip" onclick="setCategory('NOTE', this)">Notes</div>
          <div class="cat-chip" onclick="setCategory('CARD', this)">Cards</div>
          <div class="cat-chip" onclick="setCategory('KEY', this)">Keys & SSH</div>
        </div>
      </div>
      <div class="items-grid" id="itemsContainer"></div>
    </div>
  </div>

  <div class="status-bar">
    <span>Exported: $exportTimestamp</span>
    <span>Zero-Knowledge • Fully Offline Architecture</span>
  </div>

  <script>
    const VAULT_SALT_B64 = "$masterSalt";
    const ENCRYPTED_RECORDS = $recordsJsonString;
    let decryptedRecords = [];
    let currentCategory = 'ALL';

    function base64ToUint8(b64) {
      const bin = atob(b64);
      const bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      return bytes;
    }

    async function deriveAesKey(password, salt) {
      const enc = new TextEncoder();
      const pwKey = await window.crypto.subtle.importKey(
        "raw",
        enc.encode(password),
        { name: "PBKDF2" },
        false,
        ["deriveKey"]
      );
      return await window.crypto.subtle.deriveKey(
        {
          name: "PBKDF2",
          salt: salt,
          iterations: 100000,
          hash: "SHA-256"
        },
        pwKey,
        { name: "AES-GCM", length: 256 },
        false,
        ["decrypt"]
      );
    }

    async function decryptRecord(record, aesKey) {
      try {
        const ciphertext = base64ToUint8(record.ciphertext);
        const iv = base64ToUint8(record.iv);
        const decryptedBuffer = await window.crypto.subtle.decrypt(
          { name: "AES-GCM", iv: iv, tagLength: 128 },
          aesKey,
          ciphertext
        );
        const dec = new TextDecoder();
        const payloadJson = JSON.parse(dec.decode(decryptedBuffer));
        return { ...record, payload: payloadJson };
      } catch (e) {
        return null;
      }
    }

    async function decryptVault() {
      const pwInput = document.getElementById('masterPassword');
      const errorMsg = document.getElementById('errorMsg');
      const decryptBtn = document.getElementById('decryptBtn');
      const password = pwInput.value;

      if (!password) return;

      decryptBtn.innerText = 'Decrypting...';
      decryptBtn.disabled = true;
      errorMsg.style.display = 'none';

      try {
        const salt = base64ToUint8(VAULT_SALT_B64);
        const aesKey = await deriveAesKey(password, salt);

        const results = [];
        for (const record of ENCRYPTED_RECORDS) {
          const item = await decryptRecord(record, aesKey);
          if (item) results.push(item);
        }

        if (ENCRYPTED_RECORDS.length > 0 && results.length === 0) {
          throw new Error('Decryption failed');
        }

        decryptedRecords = results;
        document.getElementById('unlockView').style.display = 'none';
        document.getElementById('vaultView').style.display = 'block';
        document.getElementById('lockHeaderBtn').style.display = 'block';
        document.getElementById('countAll').innerText = decryptedRecords.length;
        renderItems();
      } catch (err) {
        errorMsg.style.display = 'block';
        decryptBtn.innerText = 'Decrypt Database';
        decryptBtn.disabled = false;
      }
    }

    function lockVault() {
      decryptedRecords = [];
      document.getElementById('masterPassword').value = '';
      document.getElementById('unlockView').style.display = 'block';
      document.getElementById('vaultView').style.display = 'none';
      document.getElementById('lockHeaderBtn').style.display = 'none';
      document.getElementById('decryptBtn').innerText = 'Decrypt Database';
      document.getElementById('decryptBtn').disabled = false;
    }

    function setCategory(cat, element) {
      currentCategory = cat;
      document.querySelectorAll('.cat-chip').forEach(c => c.classList.remove('active'));
      element.classList.add('active');
      renderItems();
    }

    function renderItems() {
      const container = document.getElementById('itemsContainer');
      const query = (document.getElementById('searchInput').value || '').toLowerCase();
      container.innerHTML = '';

      const filtered = decryptedRecords.filter(r => {
        const matchesCategory = currentCategory === 'ALL' || r.category === currentCategory;
        const p = r.payload;
        const matchesQuery = !query ||
          r.title.toLowerCase().includes(query) ||
          (p.username && p.username.toLowerCase().includes(query)) ||
          (p.url && p.url.toLowerCase().includes(query)) ||
          (p.notes && p.notes.toLowerCase().includes(query));
        return matchesCategory && matchesQuery;
      });

      if (filtered.length === 0) {
        container.innerHTML = '<div style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 40px;">No records match your criteria.</div>';
        return;
      }

      filtered.forEach(item => {
        const card = document.createElement('div');
        card.className = 'item-card';
        const p = item.payload;

        let fieldsHtml = '';
        if (p.username) {
          fieldsHtml += '<div class="item-field"><div class="item-field-label">Username</div><div class="item-field-value"><span>' + escapeHtml(p.username) + '</span><button class="copy-btn" onclick="copyText(\'' + escapeJs(p.username) + '\')">Copy</button></div></div>';
        }
        if (p.password) {
          fieldsHtml += '<div class="item-field"><div class="item-field-label">Password</div><div class="item-field-value"><span id="pw_' + item.id + '">••••••••</span><div><button class="copy-btn" onclick="toggleSecret(\'pw_' + item.id + '\', \'' + escapeJs(p.password) + '\')">Show</button><button class="copy-btn" onclick="copyText(\'' + escapeJs(p.password) + '\')">Copy</button></div></div></div>';
        }
        if (p.url) {
          fieldsHtml += '<div class="item-field"><div class="item-field-label">URL</div><div class="item-field-value"><a href="' + escapeHtml(p.url) + '" target="_blank" style="color:var(--cyan);text-decoration:none;">' + escapeHtml(p.url) + '</a></div></div>';
        }
        if (p.cardNumber) {
          fieldsHtml += '<div class="item-field"><div class="item-field-label">Card Number</div><div class="item-field-value"><span>' + escapeHtml(p.cardNumber) + '</span><button class="copy-btn" onclick="copyText(\'' + escapeJs(p.cardNumber) + '\')">Copy</button></div></div>';
        }
        if (p.cardExpiry || p.cardCvv) {
          fieldsHtml += '<div style="display:flex;gap:8px;"><div class="item-field" style="flex:1;"><div class="item-field-label">Exp</div><div class="item-field-value">' + escapeHtml(p.cardExpiry || '—') + '</div></div><div class="item-field" style="flex:1;"><div class="item-field-label">CVV</div><div class="item-field-value"><span>•••</span><button class="copy-btn" onclick="copyText(\'' + escapeJs(p.cardCvv || '') + '\')">Copy</button></div></div></div>';
        }
        if (p.keyType || p.keyValue) {
          fieldsHtml += '<div class="item-field"><div class="item-field-label">Key (' + escapeHtml(p.keyType || 'SSH') + ')</div><div class="item-field-value"><span style="overflow:hidden;text-overflow:ellipsis;max-width:180px;">' + escapeHtml(p.keyValue) + '</span><button class="copy-btn" onclick="copyText(\'' + escapeJs(p.keyValue) + '\')">Copy</button></div></div>';
        }
        if (p.notes) {
          fieldsHtml += '<div class="item-field"><div class="item-field-label">Secure Note</div><div class="notes-box">' + escapeHtml(p.notes) + '</div></div>';
        }

        card.innerHTML = 
          '<div class="item-card-header">' +
            '<div class="item-card-title">' + escapeHtml(item.title) + '</div>' +
            '<div class="item-card-cat">' + escapeHtml(item.category) + '</div>' +
          '</div>' + fieldsHtml;

        container.appendChild(card);
      });
    }

    function toggleSecret(elementId, secret) {
      const el = document.getElementById(elementId);
      if (el.innerText === '••••••••') {
        el.innerText = secret;
      } else {
        el.innerText = '••••••••';
      }
    }

    function copyText(text) {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(() => {
          alert('Copied to clipboard');
        }).catch(() => fallbackCopy(text));
      } else {
        fallbackCopy(text);
      }
    }

    function fallbackCopy(text) {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try {
        document.execCommand('copy');
        alert('Copied to clipboard');
      } catch (e) {
        alert('Copy failed. Please copy manually.');
      }
      document.body.removeChild(ta);
    }

    function escapeHtml(str) {
      if (!str) return '';
      return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function escapeJs(str) {
      if (!str) return '';
      return String(str).replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n').replace(/\r/g, '');
    }
  </script>
</body>
</html>"""
    }
}
