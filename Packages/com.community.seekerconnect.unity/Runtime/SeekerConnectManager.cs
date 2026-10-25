using System;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using UnityEngine;

namespace Community.SeekerConnect
{
    /// <summary>Call from Unity's main thread. Official Android and WebGL SDK transports.</summary>
    public static class SeekerConnectManager
    {
        internal static RequestRouter Router = new RequestRouter();
        static SynchronizationContext context;
        static int mainThread;
        static SeekerCallback receiver;
        static bool initialized;
        static int timeoutMs = 120000;
#if UNITY_WEBGL && !UNITY_EDITOR
        [System.Runtime.InteropServices.DllImport("__Internal")]
        static extern void SeekerWebRequest(string receiver, string id, string operation, string json);
        [System.Runtime.InteropServices.DllImport("__Internal")]
        static extern void SeekerWebCancel(string id);
#endif
        public static WalletSession Session { get; private set; }
        public static WalletConnectionState State { get; private set; } = WalletConnectionState.Uninitialized;
        public static event Action<WalletConnectionState> ConnectionStateChanged;

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.SubsystemRegistration)]
        static void Reset()
        {
            Router.FailAll("CANCELLED"); Router = new RequestRouter();
            initialized = false; receiver = null; Session = null; ConnectionStateChanged = null;
            State = WalletConnectionState.Uninitialized;
            mainThread = Thread.CurrentThread.ManagedThreadId; context = SynchronizationContext.Current;
        }

        public static async Task InitializeAsync(SeekerConnectConfig config)
        {
            MainThread();
            if (config == null) throw new ArgumentNullException(nameof(config));
            config.Validate();
            if (initialized) throw new InvalidOperationException("Already initialized. Reuse the current manager for this app session.");
            EnsurePlatform();
            if (receiver == null) {
                var go = new GameObject("SeekerConnectCallbacks-" + Guid.NewGuid().ToString("N"));
                UnityEngine.Object.DontDestroyOnLoad(go); receiver = go.AddComponent<SeekerCallback>();
            }
            context = SynchronizationContext.Current;
            timeoutMs = config.timeoutMs;
            await Send("initialize", JsonUtility.ToJson(config));
            initialized = true; SetState(WalletConnectionState.Disconnected);
        }
        public static async Task<WalletSession> ConnectAsync(CancellationToken cancellationToken = default)
        { await SessionOperation("connect", cancellationToken); return Session; }
        public static async Task<WalletSession> RestoreSessionAsync(CancellationToken cancellationToken = default)
        { await SessionOperation("restore", cancellationToken); return Session; }
        static async Task SessionOperation(string operation, CancellationToken token)
        {
            Ready(); token.ThrowIfCancellationRequested();
            SetState(WalletConnectionState.Connecting);
            try { await Send(operation, "{}", token); }
            finally { SetState(Session == null ? WalletConnectionState.Disconnected : WalletConnectionState.Connected); }
        }
        public static async Task<WalletAvailability> GetWalletAvailabilityAsync()
        {
            Ready(); var reply = await Send("availability", "{}");
            return reply.availability == "CERTIFIED" ? WalletAvailability.Certified : reply.availability == "OTHER" ? WalletAvailability.Other : reply.availability == "UNKNOWN" ? WalletAvailability.Unknown : WalletAvailability.None;
        }
        public static async Task<WalletSignature> SignMessageAsync(string message, CancellationToken cancellationToken = default)
        {
            Ready(true);
            if (string.IsNullOrEmpty(message)) throw new ArgumentException("Message cannot be empty", nameof(message));
            var bytes = new UTF8Encoding(false, true).GetBytes(message);
            if (bytes.Length > 32768) throw new ArgumentException("Message exceeds 32768 UTF-8 bytes");
            var reply = await Send("signMessage", Encode(bytes), cancellationToken);
            return new WalletSignature(reply.address, DecodeSignature(reply.signature), Convert.FromBase64String(reply.signedPayload));
        }
        public static async Task<byte[]> SignTransactionAsync(byte[] serializedTransaction, CancellationToken cancellationToken = default)
        {
            Ready(true); ValidateTransaction(serializedTransaction);
            var reply = await Send("signTransaction", Encode(serializedTransaction), cancellationToken);
            return Convert.FromBase64String(reply.payload);
        }
        /// <summary>Returns a submission signature, NOT confirmation. Never automatically retry an ambiguous failure.</summary>
        public static async Task<WalletSignature> SignAndSendTransactionAsync(byte[] serializedTransaction, CancellationToken cancellationToken = default)
        {
            Ready(true); ValidateTransaction(serializedTransaction);
            if (!Session.supportsSignAndSend) throw new SeekerConnectException("UNSUPPORTED_OPERATION", "Wallet does not support sign-and-send");
            var reply = await Send("signAndSend", Encode(serializedTransaction), cancellationToken);
            return new WalletSignature(reply.address, DecodeSignature(reply.signature));
        }
        /// <summary>Clears local official SDK authorization cache. Does not revoke the wallet's grant.</summary>
        public static async Task DisconnectAsync() { Ready(); await Send("disconnect", "{}"); }
        static byte[] DecodeSignature(string encoded)
        {
            var result = Convert.FromBase64String(encoded);
            if (result.Length != 64) throw new SeekerConnectException("INVALID_RESPONSE", "Expected a 64-byte Ed25519 signature");
            return result;
        }
        static string Encode(byte[] bytes) => JsonUtility.ToJson(new Payload { payload = Convert.ToBase64String(bytes) });
        static void ValidateTransaction(byte[] bytes)
        {
            if (bytes == null || bytes.Length < 100 || bytes.Length > 1232)
                throw new ArgumentException("Supply a complete serialized Solana transaction (100..1232 bytes), not just its message.");
        }
        static void MainThread()
        {
            if (mainThread == 0 || Thread.CurrentThread.ManagedThreadId != mainThread)
                throw new InvalidOperationException("Call Seeker Connect on Unity's main thread after runtime initialization.");
        }
        static void EnsurePlatform()
        {
#if (!UNITY_ANDROID && !UNITY_WEBGL) || UNITY_EDITOR
            SetState(WalletConnectionState.Unsupported);
            throw new SeekerConnectException("UNSUPPORTED_PLATFORM", "Wallet operations require an Android or WebGL build and a compatible wallet. Editor simulation is disabled.");
#endif
        }
        static void Ready(bool requireSession = false)
        {
            MainThread(); EnsurePlatform();
            if (!initialized) throw new SeekerConnectException("NOT_INITIALIZED", "Initialize Seeker Connect first");
            if (Router.Count != 0) throw new SeekerConnectException("BUSY", "A wallet operation is already running");
            if (requireSession && Session?.Account == null) throw new SeekerConnectException("NOT_CONNECTED", "Connect or restore a wallet first");
        }
        static async Task<NativeReply> Send(string operation, string json, CancellationToken token = default)
        {
            token.ThrowIfCancellationRequested();
            if (Router.Count != 0) throw new SeekerConnectException("BUSY", "A wallet operation is already running");
            string id = Guid.NewGuid().ToString("N");
            var task = Router.Add(id);
            using var watchdog = new CancellationTokenSource();
            using var registration = token.Register(() => context.Post(_ => CancelNative(id), null));
            try {
#if UNITY_ANDROID && !UNITY_EDITOR
                using var player = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
                using var activity = player.GetStatic<AndroidJavaObject>("currentActivity");
                using var bridge = new AndroidJavaClass("com.community.seekerconnect.SeekerBridge");
                bridge.CallStatic("request", activity, receiver.gameObject.name, id, operation, json);
#elif UNITY_WEBGL && !UNITY_EDITOR
                SeekerWebRequest(receiver.gameObject.name, id, operation, json);
#endif
#if !UNITY_WEBGL || UNITY_EDITOR
                _ = Watchdog(id, watchdog.Token);
#endif
            } catch (Exception) {
                Router.Complete(new NativeReply { id = id, code = "BRIDGE_UNAVAILABLE", message = "Platform bridge failed to load. Check build integration." });
            }
            try { return await task; }
            finally { watchdog.Cancel(); }
        }
        static async Task Watchdog(string id, CancellationToken token)
        {
            try {
                await Task.Delay(timeoutMs + 15000, token);
                CancelNative(id);
                Router.Complete(new NativeReply { id = id, code = "TIMEOUT", message = "Native callback did not arrive. Check transaction status before retrying." });
            } catch (OperationCanceledException) { }
        }
        static void CancelNative(string id)
        {
            if (!Router.Contains(id)) return;
#if UNITY_ANDROID && !UNITY_EDITOR
            try { using var bridge = new AndroidJavaClass("com.community.seekerconnect.SeekerBridge"); bridge.CallStatic("cancel", id); }
            catch (Exception) { /* The watchdog remains responsible for completion. */ }
#elif UNITY_WEBGL && !UNITY_EDITOR
            SeekerWebCancel(id);
#endif
        }
        internal static void Receive(string json)
        {
            NativeReply reply;
            try { reply = JsonUtility.FromJson<NativeReply>(json); }
            catch (Exception) { return; } // Do not log a potentially sensitive native payload.
            if (reply == null || !Router.Contains(reply.id)) return;
            if (reply.hasSession) {
                Session = reply.session?.Account == null ? null : reply.session;
                SetState(Session == null ? WalletConnectionState.Disconnected : WalletConnectionState.Connected);
            }
            Router.Complete(reply);
        }
        static void SetState(WalletConnectionState state)
        {
            if (State == state) return; State = state;
            if (ConnectionStateChanged == null) return;
            foreach (Action<WalletConnectionState> listener in ConnectionStateChanged.GetInvocationList())
                try { listener(state); } catch (Exception e) { Debug.LogException(e); }
        }
        internal static void Closed()
        {
            Router.FailAll("CANCELLED"); initialized = false; receiver = null; Session = null;
            SetState(WalletConnectionState.Uninitialized);
        }
    }
}
