using System;
using System.Collections.Generic;
using System.Text;
using System.Threading.Tasks;
using UnityEngine;
using UnityEngine.Networking;

/// <summary>Builds an unsigned legacy memo transaction. Does not transfer or submit anything.</summary>
public static class DevnetMemo
{
    public static async Task<byte[]> CreateAsync(byte[] payer)
    {
        using var request = new UnityWebRequest("https://api.devnet.solana.com", "POST");
        request.uploadHandler = new UploadHandlerRaw(Encoding.UTF8.GetBytes("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"getLatestBlockhash\",\"params\":[{\"commitment\":\"confirmed\"}]}"));
        request.downloadHandler = new DownloadHandlerBuffer(); request.SetRequestHeader("Content-Type", "application/json"); request.timeout = 20;
        var operation = request.SendWebRequest(); while (!operation.isDone) await Task.Yield();
        if (request.result != UnityWebRequest.Result.Success) throw new Exception("Devnet RPC failed: " + request.error);
        var response = JsonUtility.FromJson<RpcResponse>(request.downloadHandler.text);
        if (response?.result?.value?.blockhash == null) throw new Exception("Devnet RPC returned no blockhash; check rate limits.");
        return Build(payer, DecodeBase58(response.result.value.blockhash));
    }
    public static byte[] Build(byte[] payer, byte[] blockhash)
    {
        if (payer?.Length != 32 || blockhash?.Length != 32) throw new ArgumentException("Payer and blockhash must be 32 bytes.");
        var memo = Encoding.UTF8.GetBytes("Hello from Unity Seeker Connect!");
        var bytes = new List<byte> { 1 }; bytes.AddRange(new byte[64]); // one empty signature slot
        bytes.AddRange(new byte[] { 1, 0, 1, 2 }); // header and two static accounts
        bytes.AddRange(payer); bytes.AddRange(DecodeBase58("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"));
        bytes.AddRange(blockhash);
        bytes.AddRange(new byte[] { 1, 1, 1, 0, (byte)memo.Length }); // one instruction; memo program; payer signer
        bytes.AddRange(memo); return bytes.ToArray();
    }
    public static byte[] DecodeBase58(string value)
    {
        const string alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        var result = new byte[32];
        foreach (char c in value) {
            int carry = alphabet.IndexOf(c); if (carry < 0) throw new FormatException("Invalid Base58");
            for (int i = 31; i >= 0; --i) { carry += result[i] * 58; result[i] = (byte)carry; carry >>= 8; }
            if (carry != 0) throw new FormatException("Base58 key exceeds 32 bytes");
        }
        return result;
    }
    [Serializable] sealed class RpcResponse { public RpcResult result; }
    [Serializable] sealed class RpcResult { public RpcValue value; }
    [Serializable] sealed class RpcValue { public string blockhash; }
}
