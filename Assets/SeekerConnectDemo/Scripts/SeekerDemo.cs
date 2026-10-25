using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using Community.SeekerConnect;
using UnityEngine;
using UnityEngine.UIElements;

public sealed class SeekerDemo : MonoBehaviour
{
    public StyleSheet theme;
    Label state, address, logs, signature;
    TextField identity, icon, transaction, relay;
    readonly List<Button> actions = new List<Button>();
    bool initialized, busy;

    void OnEnable()
    {
        var root = GetComponent<UIDocument>().rootVisualElement;
        root.Clear(); if (theme != null) root.styleSheets.Add(theme);
        root.AddToClassList("screen");
        var scroll = new ScrollView(); root.Add(scroll);
        var page = new VisualElement(); page.AddToClassList("page"); scroll.Add(page);
        AddLabel(page, "SOLANA MOBILE  /  COMMUNITY SDK", "eyebrow");
        AddLabel(page, "Seeker Connect", "title");
#if UNITY_WEBGL
        AddLabel(page, "Unity Web / Official Seeker Connect SDK", "subtitle");
        AddLabel(page, "Enter your Nostr relay hostname. Connecting opens the official wallet association flow; a compatible wallet is required.", "notice");
#else
        AddLabel(page, "Unity Demo / MWA testing", "subtitle");
        AddLabel(page, "MWA TESTING MODE — Devnet only. Supports Mock MWA Wallet on ordinary Android phones. This is not a certified Seeker connection test.", "notice");
#endif
        AddLabel(page, "Your wallet. Your approval. Real signatures.", "muted");
        state = AddLabel(page, "DISCONNECTED", "status");
#if UNITY_EDITOR
        AddLabel(page, "EDITOR PREVIEW — Wallet operations require an Android build and a compatible wallet. No simulated wallet.", "notice");
#endif
        var setup = Card(page, "01  /  APP IDENTITY");
        AddLabel(setup, "Enter your own HTTPS website. Its icon will identify this app in the wallet approval screen.", "muted");
        identity = new TextField("Identity URI") { value = "https://github.com/CryptoSodi/solana-seeker-connect-unity" }; setup.Add(identity);
        icon = new TextField("Relative icon") { value = "/favicon.ico" }; setup.Add(icon);
#if UNITY_WEBGL
        relay = new TextField("Nostr relay hostname") { value = "relay.solanamobile.com" }; setup.Add(relay);
        AddLabel(setup, "Solana Mobile relay: accept its terms in the official quickstart before connecting.", "muted");
#endif
        AddLabel(setup, "Network: Devnet • No mainnet transfers", "network");
        var wallet = Card(page, "02  /  WALLET");
#if UNITY_WEBGL
        Button(wallet, "Connect with Seeker Connect", async () => { await Initialize(); await SeekerConnectManager.ConnectAsync(); Log("Wallet authorized (Devnet)."); }, true);
#else
        AddLabel(wallet, "Open Mock MWA Wallet and tap Authenticate first, then return here to connect.", "muted");
        Button(wallet, "Connect with MWA test wallet", async () => { await Initialize(); await SeekerConnectManager.ConnectAsync(); Log("Test wallet authorized (Devnet)."); }, true);
#endif
        Button(wallet, "Restore Session", async () => { await Initialize(); var session = await SeekerConnectManager.RestoreSessionAsync(); Log(session == null ? "No stored session." : "Cached authorization restored; signing will reauthorize with the wallet."); });
        address = AddLabel(wallet, "No wallet connected", "address");
        Button(wallet, "Copy wallet address", () => { var value = SeekerConnectManager.Session?.Account?.address; if (value == null) throw new InvalidOperationException("Connect first."); GUIUtility.systemCopyBuffer = value; Log("Address copied."); return Task.CompletedTask; });
        Button(wallet, "Disconnect", async () => { await SeekerConnectManager.DisconnectAsync(); Log("Local authorization cleared."); });
        var signing = Card(page, "03  /  MESSAGE SIGNING");
        AddLabel(signing, "Hello from Unity Seeker Connect!", "message");
        Button(signing, "Sign Message", async () => {
            var result = await SeekerConnectManager.SignMessageAsync("Hello from Unity Seeker Connect!");
            signature.text = "Ed25519 signature (Base64)\n" + result.Base64;
            SaveEvidence("message", "Hello from Unity Seeker Connect!", result.Base64);
            Log("Message signed by " + result.Address);
        }, true);
        signature = AddLabel(signing, "Signature will appear here after wallet approval.", "address");
        var tx = Card(page, "04  /  TRANSACTION LAB");
        AddLabel(tx, "Build a Devnet memo with a fresh blockhash, then request a signature. This demo never broadcasts the transaction.", "muted");
        Button(tx, "Build Devnet memo", async () => {
            var account = SeekerConnectManager.Session?.Account ?? throw new InvalidOperationException("Connect first.");
            transaction.value = Convert.ToBase64String(await DevnetMemo.CreateAsync(account.PublicKey));
            Log("Unsigned memo transaction ready. Sign promptly: blockhashes expire.");
        });
        transaction = new TextField("Serialized transaction / Base64") { multiline = true }; transaction.AddToClassList("transaction"); tx.Add(transaction);
        Button(tx, "Request transaction signature", async () => {
            var original = transaction.value.Trim();
            var result = await SeekerConnectManager.SignTransactionAsync(Convert.FromBase64String(original));
            SaveEvidence("transaction", original, Convert.ToBase64String(result));
            transaction.value = Convert.ToBase64String(result); Log("Signed transaction returned. NOT broadcast or confirmed.");
        });
        var activity = Card(page, "ACTIVITY");
        var logScroll = new ScrollView(); logScroll.AddToClassList("logs"); activity.Add(logScroll);
        logs = AddLabel(logScroll, "Ready. Configure your app identity to begin.", "log-text");
        AddLabel(page, "Independent Unity wrapper · Official Solana Mobile SDK\nPrivate keys never leave your wallet.", "footer");
        SeekerConnectManager.ConnectionStateChanged += UpdateState;
    }
    void OnDisable() { SeekerConnectManager.ConnectionStateChanged -= UpdateState; actions.Clear(); }
    async Task Initialize()
    {
        if (initialized) return;
        await SeekerConnectManager.InitializeAsync(new SeekerConnectConfig { identityUri = identity.value.Trim(), iconPath = icon.value.Trim(), relayDomain = relay?.value.Trim() ?? "",
#if UNITY_WEBGL
            allowMwaTesting = false
#else
            allowMwaTesting = true
#endif
        });
        relay?.SetEnabled(false);
        initialized = true; identity.SetEnabled(false); icon.SetEnabled(false);
        Log("Official SDK initialized. Availability: " + await SeekerConnectManager.GetWalletAvailabilityAsync());
    }
    void UpdateState(WalletConnectionState value)
    { state.text = value.ToString().ToUpperInvariant(); address.text = SeekerConnectManager.Session?.Account?.address ?? "No wallet connected"; }
    void Button(VisualElement parent, string title, Func<Task> action, bool primary = false)
    {
        var button = new Button(async () => {
            if (busy) return; busy = true; foreach (var item in actions) item.SetEnabled(false);
            try { await action(); }
            catch (SeekerConnectException e) { Log(e.Code + ": " + e.Message); }
            catch (Exception e) { Log(e.Message); }
            finally { busy = false; foreach (var item in actions) item.SetEnabled(true); UpdateState(SeekerConnectManager.State); }
        }) { text = title };
        if (primary) button.AddToClassList("primary"); parent.Add(button); actions.Add(button);
    }
    void Log(string message)
    {
        logs.text = DateTime.Now.ToString("HH:mm:ss") + "  " + message + "\n\n" + logs.text;
        if (logs.text.Length > 6000) logs.text = logs.text.Substring(0, 6000);
    }
    [Serializable] sealed class SigningEvidence { public string address, input, output; }
    static void SaveEvidence(string operation, string input, string output)
    {
        if (!Debug.isDebugBuild) return;
        // Public test payloads only; never persist authorization tokens or wallet secrets.
        System.IO.File.WriteAllText(System.IO.Path.Combine(Application.persistentDataPath, operation + "-evidence.json"),
            JsonUtility.ToJson(new SigningEvidence { address = SeekerConnectManager.Session.Account.address, input = input, output = output }));
    }
    static Label AddLabel(VisualElement parent, string text, string className)
    { var label = new Label(text); label.AddToClassList(className); parent.Add(label); return label; }
    static VisualElement Card(VisualElement parent, string title)
    { var card = new VisualElement(); card.AddToClassList("card"); AddLabel(card, title, "section-title"); parent.Add(card); return card; }
}
