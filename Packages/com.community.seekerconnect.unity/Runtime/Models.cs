using System;

namespace Community.SeekerConnect
{
    public enum WalletConnectionState { Uninitialized, Disconnected, Connecting, Connected, Unsupported }
    public enum WalletAvailability { None, Other, Certified }

    [Serializable]
    public sealed class SeekerConnectConfig
    {
        public string appName = "Seeker Connect Unity Demo";
        public string identityUri = "";
        public string iconPath = "icon.png";
        public string chain = "solana:devnet";
        public int timeoutMs = 120000;

        public void Validate()
        {
            if (string.IsNullOrWhiteSpace(appName) || !Uri.TryCreate(identityUri, UriKind.Absolute, out var uri) ||
                uri.Scheme != Uri.UriSchemeHttps || string.IsNullOrEmpty(uri.Host) || !string.IsNullOrEmpty(uri.UserInfo) || !string.IsNullOrEmpty(uri.Fragment))
                throw new ArgumentException("Supply your application's real HTTPS identity URI and display name.");
            if (string.IsNullOrWhiteSpace(iconPath) || !Uri.TryCreate(iconPath, UriKind.Relative, out _) || iconPath.StartsWith("//"))
                throw new ArgumentException("Icon must be a relative path on your identity website.");
            if (chain != "solana:devnet" && chain != "solana:testnet" && chain != "solana:mainnet")
                throw new ArgumentException("Use solana:devnet, solana:testnet, or solana:mainnet.");
            if (timeoutMs < 10000 || timeoutMs > 120000) throw new ArgumentOutOfRangeException(nameof(timeoutMs));
        }
    }

    [Serializable]
    public sealed class WalletAccount
    {
        public string address;
        public string publicKeyBase64;
        public string label;
        public byte[] PublicKey => Convert.FromBase64String(publicKeyBase64);
    }
    [Serializable]
    public sealed class WalletSession
    {
        public string chain;
        public WalletAccount[] accounts;
        public bool supportsSignAndSend;
        public WalletAccount Account => accounts != null && accounts.Length > 0 ? accounts[0] : null;
    }
    public sealed class WalletSignature
    {
        public string Address { get; }
        public byte[] Signature { get; }
        public byte[] SignedPayload { get; }
        public string Base64 => Convert.ToBase64String(Signature);
        internal WalletSignature(string address, byte[] signature, byte[] signedPayload = null)
        { Address = address; Signature = signature; SignedPayload = signedPayload; }
    }
    public sealed class SeekerConnectException : Exception
    {
        public string Code { get; }
        public SeekerConnectException(string code, string message) : base(message) { Code = code; }
    }
    [Serializable]
    internal sealed class NativeReply
    {
        public string id;
        public bool ok;
        public string code;
        public string message;
        public bool hasSession;
        public WalletSession session;
        public string availability;
        public string signature;
        public string signedPayload;
        public string payload;
        public string address;
    }
    [Serializable] internal sealed class Payload { public string payload; }
}
